package me.domino.fa2.data.fa.browse

import me.domino.fa2.data.fa.core.toPageState
import me.domino.fa2.data.fa.gallery.GalleryParser
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.SubmissionListingPage

/** Browse 数据源。 */
class BrowseDataSource(private val endpoint: BrowseEndpoint, private val parser: GalleryParser) {
  /** 拉取 Browse 指定分页。 */
  suspend fun fetchPage(url: String): PageState<SubmissionListingPage> =
      endpoint.fetchByUrl(url).toPageState { success ->
        parser.parseListing(html = success.body, baseUrl = success.url)
      }
}
