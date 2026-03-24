package me.domino.fa2.data.datasource

import me.domino.fa2.data.model.GalleryPage
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.network.endpoint.GalleryEndpoint
import me.domino.fa2.data.parser.GalleryParser
import me.domino.fa2.util.toPageState

/** Gallery 远端数据源。 */
class GalleryDataSource(private val endpoint: GalleryEndpoint, private val parser: GalleryParser) {
  /** 拉取 gallery 分页。 */
  suspend fun fetchPage(username: String, nextPageUrl: String?): PageState<GalleryPage> {
    val response =
        if (nextPageUrl.isNullOrBlank()) {
          endpoint.fetch(username)
        } else {
          endpoint.fetchByUrl(nextPageUrl)
        }
    return response.toPageState { success ->
      parser.parse(html = success.body, baseUrl = success.url, defaultAuthor = username)
    }
  }
}
