package me.domino.fa2.data.remote.fa.browse

import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.SubmissionListingPage
import me.domino.fa2.data.remote.fa.core.toResourceResult
import me.domino.fa2.data.remote.fa.listing.SubmissionListingParser

/** Browse 数据源。 */
class BrowseDataSource(
    private val endpoint: BrowseEndpoint,
    private val parser: SubmissionListingParser,
) {
  /** 拉取 Browse 指定分页。 */
  suspend fun fetchPage(url: String): ResourceResult<SubmissionListingPage> =
      endpoint.fetchByUrl(url).toResourceResult { success ->
        parser.parseListing(html = success.body, baseUrl = success.url)
      }
}
