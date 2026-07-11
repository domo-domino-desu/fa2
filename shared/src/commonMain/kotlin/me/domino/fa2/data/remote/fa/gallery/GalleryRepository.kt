package me.domino.fa2.data.remote.fa.gallery

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.model.GalleryPage
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.remote.fa.core.FaHtmlDataSource
import me.domino.fa2.data.remote.fa.core.PageQueryCache
import me.domino.fa2.data.remote.fa.core.PageType
import me.domino.fa2.data.remote.fa.core.cacheJson
import me.domino.fa2.data.remote.fa.core.fetchParsed
import me.domino.fa2.data.remote.fa.listing.SubmissionListingParser
import me.domino.fa2.utils.FaUrls

class GalleryRepository(
    private val html: FaHtmlDataSource,
    private val parser: SubmissionListingParser,
    private val queries: PageQueryCache,
) {
  suspend fun loadGalleryPage(
      username: String,
      nextPageUrl: String? = null,
  ): ResourceResult<GalleryPage> {
    val url = nextPageUrl ?: FaUrls.gallery(username)
    return queries.queryOnce(
        key(username, url),
        PageType.Gallery,
        { cacheJson.encodeToString(it) },
        ::decode,
    ) {
      fetch(username, url)
    }
  }

  suspend fun refreshGalleryFirstPage(
      username: String,
      firstPageUrlOverride: String? = null,
  ): ResourceResult<GalleryPage> {
    queries.invalidate(PageType.Gallery)
    return loadGalleryPage(username, firstPageUrlOverride)
  }

  private suspend fun fetch(username: String, url: String) =
      html.fetchParsed(url) { body, finalUrl -> parser.parse(body, finalUrl, username) }

  private fun key(username: String, url: String) =
      "gallery:username=${username.lowercase()}:cursor=$url"

  private fun decode(json: String) =
      runCatching { cacheJson.decodeFromString<GalleryPage>(json) }.getOrNull()
}
