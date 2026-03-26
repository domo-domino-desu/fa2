package me.domino.fa2.data.store

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.datasource.SearchDataSource
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.SubmissionListingPage

/** Search 列表存储层。 */
class SearchStore(
    private val dataSource: SearchDataSource,
    private val pageCacheDao: PageCacheDao,
) {
  private val cachedStore =
      CachedPageStoreSupport(
          storeName = "search-fetcher",
          pageCacheDao = pageCacheDao,
          pageTypeOf = { PAGE_TYPE_SEARCH },
          cacheKeyFor = ::cacheKeyFor,
          fetch = { url -> dataSource.fetchPage(url).requireStoreValue() },
          encode = { page -> storeJson.encodeToString(page) },
          decode = { json ->
            runCatching { storeJson.decodeFromString<SubmissionListingPage>(json) }.getOrNull()
          },
      )

  fun stream(requestUrl: String): Flow<PageState<SubmissionListingPage>> {
    val key = normalizeRequestUrl(requestUrl)
    return cachedStore.stream(key)
  }

  suspend fun loadPageOnce(requestUrl: String): PageState<SubmissionListingPage> =
      cachedStore.loadOnce(normalizeRequestUrl(requestUrl))

  suspend fun refreshPage(requestUrl: String): PageState<SubmissionListingPage> =
      cachedStore.refresh(normalizeRequestUrl(requestUrl))

  private fun normalizeRequestUrl(url: String): String = url.trim()

  private fun cacheKeyFor(url: String): String = "search:url=$url"

  companion object {
    private const val PAGE_TYPE_SEARCH: String = "search_page_v1"
  }
}
