package me.domino.fa2.data.fa.favorites

import me.domino.fa2.data.fa.core.toPageState
import me.domino.fa2.data.fa.gallery.GalleryParser
import me.domino.fa2.data.model.GalleryPage
import me.domino.fa2.data.model.PageState

/** Favorites 远端数据源。 */
class FavoritesDataSource(
    private val endpoint: FavoritesEndpoint,
    private val parser: GalleryParser,
) {
  /** 拉取 favorites 分页。 */
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
