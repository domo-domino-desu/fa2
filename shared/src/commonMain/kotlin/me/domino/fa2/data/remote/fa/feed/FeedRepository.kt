package me.domino.fa2.data.remote.fa.feed

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.model.FeedPage
import me.domino.fa2.data.model.QueryState
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.remote.fa.core.FaHtmlDataSource
import me.domino.fa2.data.remote.fa.core.PageQueryCache
import me.domino.fa2.data.remote.fa.core.PageType
import me.domino.fa2.data.remote.fa.core.cacheJson
import me.domino.fa2.data.remote.fa.core.fetchParsed
import me.domino.fa2.utils.FaUrls
import me.domino.fa2.utils.parseSubmissionsFromSid

class FeedRepository(
    private val html: FaHtmlDataSource,
    private val parser: FeedParser,
    private val queries: PageQueryCache,
) {
  fun streamFirstPage(): Flow<QueryState<FeedPage>> = streamPage(null)

  fun streamPage(fromSid: Int?): Flow<QueryState<FeedPage>> =
      queries.queryFlow(
          key = key(fromSid),
          type = PageType.Feed,
          encode = { cacheJson.encodeToString(it) },
          decode = { runCatching { cacheJson.decodeFromString<FeedPage>(it) }.getOrNull() },
          fetch = { fetch(FaUrls.submissions(fromSid)) },
      )

  suspend fun loadFirstPage(): ResourceResult<FeedPage> = loadPage(null)

  suspend fun refreshFirstPage(): ResourceResult<FeedPage> {
    queries.invalidate(key(null))
    return loadPage(null)
  }

  suspend fun loadPage(fromSid: Int?): ResourceResult<FeedPage> =
      queries.queryOnce(
          key = key(fromSid),
          type = PageType.Feed,
          encode = { cacheJson.encodeToString(it) },
          decode = { runCatching { cacheJson.decodeFromString<FeedPage>(it) }.getOrNull() },
          fetch = { fetch(FaUrls.submissions(fromSid)) },
      )

  suspend fun loadPageByNextUrl(nextPageUrl: String): ResourceResult<FeedPage> {
    val fromSid =
        parseSubmissionsFromSid(nextPageUrl)
            ?: return ResourceResult.Error(IllegalArgumentException("Invalid feed URL"))
    return loadPage(fromSid)
  }

  suspend fun loadPageByUrl(url: String): ResourceResult<FeedPage> =
      parseSubmissionsFromSid(url)?.let { loadPage(it) } ?: fetch(url.trim())

  suspend fun prefetchPage(fromSid: Int?) {
    loadPage(fromSid)
  }

  private suspend fun fetch(url: String): ResourceResult<FeedPage> =
      html.fetchParsed(url) { body, finalUrl -> parser.parse(body, finalUrl) }

  private fun key(fromSid: Int?): String = "feed:fromSid=${fromSid ?: 0}"
}
