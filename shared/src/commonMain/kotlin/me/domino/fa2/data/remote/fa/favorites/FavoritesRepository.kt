package me.domino.fa2.data.remote.fa.favorites

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

class FavoritesRepository(
    private val html: FaHtmlDataSource,
    private val parser: SubmissionListingParser,
    private val queries: PageQueryCache,
) {
  suspend fun loadFavoritesPage(
      username: String,
      nextPageUrl: String? = null,
  ): ResourceResult<GalleryPage> {
    val url = nextPageUrl ?: FaUrls.favorites(username)
    return queries.queryOnce(
        key(username, url),
        PageType.Favorites,
        { cacheJson.encodeToString(it) },
        ::decode,
    ) {
      fetch(username, url)
    }
  }

  suspend fun refreshFavoritesFirstPage(
      username: String,
      firstPageUrlOverride: String? = null,
  ): ResourceResult<GalleryPage> {
    queries.invalidate(PageType.Favorites)
    return loadFavoritesPage(username, firstPageUrlOverride)
  }

  private suspend fun fetch(username: String, url: String) =
      html.fetchParsed(url) { body, finalUrl -> parser.parse(body, finalUrl, username) }

  private fun key(username: String, url: String) =
      "favorites:username=${username.lowercase()}:cursor=$url"

  private fun decode(json: String) =
      runCatching { cacheJson.decodeFromString<GalleryPage>(json) }.getOrNull()
}
