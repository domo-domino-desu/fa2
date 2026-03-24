package me.domino.fa2.data.store

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.datasource.WatchlistDataSource
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.local.entity.PageCacheEntity
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistPage
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest

/** Watchlist 分页存储层。 */
class WatchlistStore(
    private val dataSource: WatchlistDataSource,
    private val pageCacheDao: PageCacheDao,
) {
  private val store: Store<WatchlistPageKey, WatchlistPage> = buildStore()

  /** 读取 watchlist 分页流。 */
  fun stream(
      username: String,
      category: WatchlistCategory,
      nextPageUrl: String?,
  ): Flow<PageState<WatchlistPage>> {
    val key =
        WatchlistPageKey(
            username = normalizeUsername(username),
            category = category,
            nextPageUrl = nextPageUrl?.trim()?.takeIf { it.isNotBlank() },
        )
    return store
        .stream(StoreReadRequest.cached(key, true))
        .map(::toPageState)
        .flowWithInitialLoading()
  }

  /** 单次读取 watchlist 分页。 */
  suspend fun loadPageOnce(
      username: String,
      category: WatchlistCategory,
      nextPageUrl: String?,
  ): PageState<WatchlistPage> =
      stream(username, category, nextPageUrl).first { state -> state !is PageState.Loading }

  /** 失效指定用户 + 分类下全部 watchlist 缓存（含 Store 内存层）。 */
  suspend fun invalidateUserCategory(username: String, category: WatchlistCategory) {
    val normalizedUsername = normalizeUsername(username)
    val prefix = "watchlist:category=${category.toCacheKey()}:username=$normalizedUsername:"
    val entries = pageCacheDao.listByPageType(PAGE_TYPE_WATCHLIST)
    entries.forEach { entity ->
      if (!entity.cacheKey.startsWith(prefix)) return@forEach
      val parsed = parseWatchlistPageKey(entity.cacheKey)
      if (parsed != null) {
        store.clear(parsed)
      } else {
        pageCacheDao.delete(entity.cacheKey)
      }
    }
  }

  private fun buildStore(): Store<WatchlistPageKey, WatchlistPage> {
    val fetcher =
        Fetcher.of<WatchlistPageKey, WatchlistPage>(name = "watchlist-fetcher") { key ->
          dataSource
              .fetchPage(
                  username = key.username,
                  category = key.category,
                  nextPageUrl = key.nextPageUrl,
              )
              .requireStoreValue()
        }

    val sourceOfTruth =
        SourceOfTruth.of<WatchlistPageKey, WatchlistPage, WatchlistPage>(
            reader = { key ->
              pageCacheDao.observeByKey(cacheKeyFor(key)).map { entity ->
                readCacheIfValid(
                    entity = entity,
                    expectedPageType = PAGE_TYPE_WATCHLIST,
                    decode = ::decodePage,
                )
              }
            },
            writer = { key, page ->
              pageCacheDao.upsert(
                  PageCacheEntity(
                      cacheKey = cacheKeyFor(key),
                      pageType = PAGE_TYPE_WATCHLIST,
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

  private fun decodePage(entity: PageCacheEntity): WatchlistPage? =
      runCatching { storeJson.decodeFromString<WatchlistPage>(entity.dataJson) }.getOrNull()

  private fun cacheKeyFor(key: WatchlistPageKey): String {
    val cursor = key.nextPageUrl?.ifBlank { null } ?: "first"
    return "watchlist:category=${key.category.toCacheKey()}:username=${key.username}:cursor=$cursor"
  }

  private fun normalizeUsername(username: String): String = username.trim().lowercase()

  private fun parseWatchlistPageKey(cacheKey: String): WatchlistPageKey? {
    val prefix = "watchlist:category="
    if (!cacheKey.startsWith(prefix)) return null

    val categoryMarker = ":username="
    val categoryMarkerIndex = cacheKey.indexOf(categoryMarker, startIndex = prefix.length)
    if (categoryMarkerIndex < 0) return null
    val categoryRaw = cacheKey.substring(prefix.length, categoryMarkerIndex).trim()
    val category = categoryRaw.toWatchlistCategory() ?: return null

    val cursorMarker = ":cursor="
    val usernameStart = categoryMarkerIndex + categoryMarker.length
    val cursorMarkerIndex = cacheKey.indexOf(cursorMarker, startIndex = usernameStart)
    if (cursorMarkerIndex < 0) return null
    val username = cacheKey.substring(usernameStart, cursorMarkerIndex).trim()
    if (username.isBlank()) return null
    val cursorRaw = cacheKey.substring(cursorMarkerIndex + cursorMarker.length)
    val nextPageUrl = cursorRaw.takeUnless { it == "first" }

    return WatchlistPageKey(
        username = normalizeUsername(username),
        category = category,
        nextPageUrl = nextPageUrl,
    )
  }

  private data class WatchlistPageKey(
      val username: String,
      val category: WatchlistCategory,
      val nextPageUrl: String?,
  )

  companion object {
    private const val PAGE_TYPE_WATCHLIST: String = "watchlist_page_v1"
  }
}

private fun WatchlistCategory.toCacheKey(): String =
    when (this) {
      WatchlistCategory.WatchedBy -> "watched_by"
      WatchlistCategory.Watching -> "watching"
    }

private fun String.toWatchlistCategory(): WatchlistCategory? =
    when (this.lowercase()) {
      "watched_by" -> WatchlistCategory.WatchedBy
      "watching" -> WatchlistCategory.Watching
      else -> null
    }

private fun Flow<PageState<WatchlistPage>?>.flowWithInitialLoading():
    Flow<PageState<WatchlistPage>> = flow {
  emit(PageState.Loading)
  collect { state -> if (state != null) emit(state) }
}
