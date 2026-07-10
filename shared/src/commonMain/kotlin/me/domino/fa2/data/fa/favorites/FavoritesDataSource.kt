package me.domino.fa2.data.fa.favorites

import me.domino.fa2.data.fa.core.toResourceResult
import me.domino.fa2.data.fa.listing.SubmissionListingParser
import me.domino.fa2.data.fa.listing.UserGalleryPageSource
import me.domino.fa2.data.model.GalleryPage
import me.domino.fa2.data.model.ResourceResult

/** Favorites 远端数据源。 */
class FavoritesDataSource(
    private val endpoint: FavoritesEndpoint,
    private val parser: SubmissionListingParser,
) : UserGalleryPageSource {
  /** 拉取 favorites 分页。 */
  override suspend fun fetchPage(
      username: String,
      nextPageUrl: String?,
  ): ResourceResult<GalleryPage> {
    val response =
        if (nextPageUrl.isNullOrBlank()) {
          endpoint.fetch(username)
        } else {
          endpoint.fetchByUrl(nextPageUrl)
        }
    return response.toResourceResult { success ->
      parser.parse(html = success.body, baseUrl = success.url, defaultAuthor = username)
    }
  }
}
