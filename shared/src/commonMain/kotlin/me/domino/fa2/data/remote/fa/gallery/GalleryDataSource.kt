package me.domino.fa2.data.remote.fa.gallery

import me.domino.fa2.data.model.GalleryPage
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.remote.fa.core.toResourceResult
import me.domino.fa2.data.remote.fa.listing.SubmissionListingParser
import me.domino.fa2.data.remote.fa.listing.UserGalleryPageSource

/** Gallery 远端数据源。 */
class GalleryDataSource(
    private val endpoint: GalleryEndpoint,
    private val parser: SubmissionListingParser,
) : UserGalleryPageSource {
  /** 拉取 gallery 分页。 */
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
