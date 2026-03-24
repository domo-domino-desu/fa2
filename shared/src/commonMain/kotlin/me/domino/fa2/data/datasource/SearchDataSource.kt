package me.domino.fa2.data.datasource

import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.SubmissionListingPage
import me.domino.fa2.data.network.endpoint.SearchEndpoint
import me.domino.fa2.data.parser.GalleryParser
import me.domino.fa2.util.toPageState

/** Search 数据源。 */
class SearchDataSource(private val endpoint: SearchEndpoint, private val parser: GalleryParser) {
  /** 拉取 Search 指定分页。 */
  suspend fun fetchPage(url: String): PageState<SubmissionListingPage> =
      endpoint.fetchByUrl(url).toPageState { success ->
        parser.parseListing(html = success.body, baseUrl = success.url)
      }
}
