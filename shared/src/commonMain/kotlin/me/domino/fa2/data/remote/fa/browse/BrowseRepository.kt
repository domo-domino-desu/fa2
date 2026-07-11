package me.domino.fa2.data.remote.fa.browse

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.model.QueryState
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.SubmissionListingPage
import me.domino.fa2.data.remote.fa.core.FaHtmlDataSource
import me.domino.fa2.data.remote.fa.core.PageQueryCache
import me.domino.fa2.data.remote.fa.core.PageType
import me.domino.fa2.data.remote.fa.core.cacheJson
import me.domino.fa2.data.remote.fa.core.fetchParsed
import me.domino.fa2.data.remote.fa.listing.SubmissionListingParser

class BrowseRepository(
    private val html: FaHtmlDataSource,
    private val parser: SubmissionListingParser,
    private val queries: PageQueryCache,
) {
  fun page(url: String): Flow<QueryState<SubmissionListingPage>> =
      queries.queryFlow(key(url), PageType.Browse, { cacheJson.encodeToString(it) }, ::decode) {
        fetch(url)
      }

  suspend fun loadPage(url: String): ResourceResult<SubmissionListingPage> =
      queries.queryOnce(key(url), PageType.Browse, { cacheJson.encodeToString(it) }, ::decode) {
        fetch(url)
      }

  suspend fun refreshPage(url: String): ResourceResult<SubmissionListingPage> {
    queries.invalidate(key(url))
    return loadPage(url)
  }

  private suspend fun fetch(url: String) =
      html.fetchParsed(url.trim()) { body, finalUrl -> parser.parseListing(body, finalUrl) }

  private fun key(url: String) = "browse:url=${url.trim()}"

  private fun decode(json: String) =
      runCatching { cacheJson.decodeFromString<SubmissionListingPage>(json) }.getOrNull()
}
