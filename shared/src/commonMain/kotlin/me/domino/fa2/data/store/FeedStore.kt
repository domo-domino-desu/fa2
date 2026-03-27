package me.domino.fa2.data.store

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.datasource.FeedDataSource
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.model.FeedPage
import me.domino.fa2.data.model.PageState
import me.domino.fa2.util.parseSubmissionsFromSid

/** Feed 读取存储层。 */
class FeedStore(
    /** Feed 远端数据源。 */
    private val dataSource: FeedDataSource,
    /** Feed 页面缓存 DAO。 */
    private val pageCacheDao: PageCacheDao,
) {
  private val cachedStore =
      CachedPageStoreSupport(
          storeName = "feed-fetcher",
          pageCacheDao = pageCacheDao,
          pageTypeOf = { PAGE_TYPE_FEED },
          cacheKeyFor = ::cacheKeyFor,
          fetch = { key -> dataSource.fetchPage(fromSid = key.fromSid).requireStoreValue() },
          encode = { page -> storeJson.encodeToString(page) },
          decode = { json ->
            runCatching { storeJson.decodeFromString<FeedPage>(json) }.getOrNull()
          },
      )

  /** 读取 feed 数据流。 */
  fun stream(fromSid: Int? = null): Flow<PageState<FeedPage>> {
    val key = FeedPageKey(fromSid = fromSid)
    return cachedStore.stream(key)
  }

  /** 单次读取 feed 页。 */
  suspend fun loadPageOnce(fromSid: Int?): PageState<FeedPage> =
      cachedStore.loadOnce(FeedPageKey(fromSid = fromSid))

  /** 强制刷新指定页：先清除缓存，再重新拉取。 */
  suspend fun refreshPage(fromSid: Int?): PageState<FeedPage> {
    return cachedStore.refresh(FeedPageKey(fromSid = fromSid))
  }

  /** 按下一页 URL 读取 feed 页。 */
  suspend fun loadPageByNextUrl(nextPageUrl: String): PageState<FeedPage> {
    val fromSid =
        parseSubmissionsFromSid(nextPageUrl)
            ?: return PageState.Error(
                IllegalArgumentException("Invalid next page url: $nextPageUrl")
            )
    return loadPageOnce(fromSid = fromSid)
  }

  /** 按完整 URL 读取 feed 页。 */
  suspend fun loadPageByUrl(url: String): PageState<FeedPage> {
    val normalizedUrl = url.trim()
    val defaultFirstUrl = me.domino.fa2.util.FaUrls.submissions()
    return when {
      normalizedUrl == defaultFirstUrl -> loadPageOnce(fromSid = null)
      parseSubmissionsFromSid(normalizedUrl) != null ->
          loadPageOnce(fromSid = parseSubmissionsFromSid(normalizedUrl))
      else -> dataSource.fetchPageByUrl(normalizedUrl)
    }
  }

  /** 强制刷新指定页缓存。 */
  suspend fun prefetchPage(fromSid: Int?) {
    cachedStore.prefetch(FeedPageKey(fromSid = fromSid))
  }

  private fun cacheKeyFor(key: FeedPageKey): String = "feed:fromSid=${key.fromSid ?: 0}"

  private data class FeedPageKey(val fromSid: Int?)

  companion object {
    /** Feed 页面缓存类型。 */
    private const val PAGE_TYPE_FEED: String = "feed_page_v1"
  }
}
