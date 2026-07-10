package me.domino.fa2.data.remote.fa.search

import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.SubmissionListingPage
import me.domino.fa2.data.remote.fa.core.toResourceResult
import me.domino.fa2.data.remote.fa.listing.SubmissionListingParser

/** Search 数据源。 */
class SearchDataSource(
    private val endpoint: SearchEndpoint,
    private val parser: SubmissionListingParser,
) {
  /** 拉取 Search 指定分页。 */
  suspend fun fetchPage(url: String): ResourceResult<SubmissionListingPage> =
      endpoint.fetchByUrl(url).toResourceResult { success ->
        parser.parseListing(html = success.body, baseUrl = success.url)
      }
}
