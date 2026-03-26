package me.domino.fa2.data.store

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.datasource.BrowseDataSource
import me.domino.fa2.data.local.dao.PageCacheDao
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.SubmissionListingPage

/** Browse 列表存储层。 */
class BrowseStore(
    private val dataSource: BrowseDataSource,
    private val pageCacheDao: PageCacheDao,
) {
  private val cachedStore =
      CachedPageStoreSupport(
          storeName = "browse-fetcher",
          pageCacheDao = pageCacheDao,
          pageTypeOf = { PAGE_TYPE_BROWSE },
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

  private fun cacheKeyFor(url: String): String = "browse:url=$url"

  companion object {
    private const val PAGE_TYPE_BROWSE: String = "browse_page_v1"
  }
}
