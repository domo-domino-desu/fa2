package me.domino.fa2.data.store

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.datasource.FeedDataSource
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.local.entity.PageCacheEntity
import me.domino.fa2.data.model.FeedPage
import me.domino.fa2.data.model.PageState
import me.domino.fa2.util.ParserUtils
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest

/** Feed 读取存储层。 */
class FeedStore(
    /** Feed 远端数据源。 */
    private val dataSource: FeedDataSource,
    /** Feed 页面缓存 DAO。 */
    private val pageCacheDao: PageCacheDao,
) {
  private val store: Store<FeedPageKey, FeedPage> = buildStore()

  /** 读取 feed 数据流。 */
  fun stream(fromSid: Int? = null): Flow<PageState<FeedPage>> {
    val key = FeedPageKey(fromSid = fromSid)
    return store
        .stream(StoreReadRequest.cached(key, true))
        .map(::toPageState)
        .flowWithInitialLoading()
  }

  /** 单次读取 feed 页。 */
  suspend fun loadPageOnce(fromSid: Int?): PageState<FeedPage> =
      stream(fromSid = fromSid).first { state -> state !is PageState.Loading }

  /** 强制刷新指定页：先清除缓存，再重新拉取。 */
  suspend fun refreshPage(fromSid: Int?): PageState<FeedPage> {
    val key = FeedPageKey(fromSid = fromSid)
    store.clear(key)
    return loadPageOnce(fromSid = fromSid)
  }

  /** 按下一页 URL 读取 feed 页。 */
  suspend fun loadPageByNextUrl(nextPageUrl: String): PageState<FeedPage> {
    val fromSid =
        ParserUtils.parseSubmissionsFromSid(nextPageUrl)
            ?: return PageState.Error(
                IllegalArgumentException("Invalid next page url: $nextPageUrl")
            )
    return loadPageOnce(fromSid = fromSid)
  }

  /** 强制刷新指定页缓存。 */
  suspend fun prefetchPage(fromSid: Int?) {
    val key = FeedPageKey(fromSid = fromSid)
    store.stream(StoreReadRequest.fresh(key, false)).first(::isTerminalResponse)
  }

  private fun buildStore(): Store<FeedPageKey, FeedPage> {
    val fetcher =
        Fetcher.of<FeedPageKey, FeedPage>(name = "feed-fetcher") { key ->
          dataSource.fetchPage(fromSid = key.fromSid).requireStoreValue()
        }

    val sourceOfTruth =
        SourceOfTruth.of<FeedPageKey, FeedPage, FeedPage>(
            reader = { key ->
              pageCacheDao.observeByKey(cacheKeyFor(key)).map { entity ->
                readCacheIfValid(
                    entity = entity,
                    expectedPageType = PAGE_TYPE_FEED,
                    decode = ::decodePageEntity,
                )
              }
            },
            writer = { key, page ->
              pageCacheDao.upsert(
                  PageCacheEntity(
                      cacheKey = cacheKeyFor(key),
                      pageType = PAGE_TYPE_FEED,
                      dataJson = storeJson.encodeToString(page),
                      cachedAtMs = Clock.System.now().toEpochMilliseconds(),
                  )
              )
            },
            delete = { key -> pageCacheDao.delete(cacheKeyFor(key)) },
            deleteAll = { pageCacheDao.deleteAll() },
        )

    return StoreBuilder.from(fetcher = fetcher, sourceOfTruth = sourceOfTruth).build()
  }

  private fun decodePageEntity(entity: PageCacheEntity): FeedPage? =
      runCatching { storeJson.decodeFromString<FeedPage>(entity.dataJson) }.getOrNull()

  private fun cacheKeyFor(key: FeedPageKey): String = "feed:fromSid=${key.fromSid ?: 0}"

  private data class FeedPageKey(val fromSid: Int?)

  companion object {
    /** Feed 页面缓存类型。 */
    private const val PAGE_TYPE_FEED: String = "feed_page_v1"
  }
}

private fun Flow<PageState<FeedPage>?>.flowWithInitialLoading(): Flow<PageState<FeedPage>> = flow {
  emit(PageState.Loading)
  collect { state -> if (state != null) emit(state) }
}
