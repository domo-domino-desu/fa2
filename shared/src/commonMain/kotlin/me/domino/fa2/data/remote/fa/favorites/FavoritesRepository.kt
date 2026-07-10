package me.domino.fa2.data.remote.fa.favorites

import me.domino.fa2.data.model.GalleryPage
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.summarizeResourceResult
import me.domino.fa2.data.remote.fa.listing.UserGalleryPageStore
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl

/** Favorites 仓储。 */
class FavoritesRepository(private val galleryStore: UserGalleryPageStore) {
  private val log = FaLog.withTag("FavoritesRepository")

  /** 加载 favorites 分页。 */
  suspend fun loadFavoritesPage(
      username: String,
      nextPageUrl: String? = null,
  ): ResourceResult<GalleryPage> {
    log.d { "加载Favorites -> user=$username,cursor=${nextPageUrl?.let(::summarizeUrl) ?: "first"}" }
    val state =
        galleryStore.loadPageOnce(
            section = UserGalleryPageStore.Section.Favorites,
            username = username,
            nextPageUrl = nextPageUrl,
        )
    log.d { "加载Favorites -> ${summarizeResourceResult(state)}" }
    return state
  }

  /** 强制刷新 favorites 首页。 */
  suspend fun refreshFavoritesFirstPage(
      username: String,
      firstPageUrlOverride: String? = null,
  ): ResourceResult<GalleryPage> {
    log.i {
      "刷新Favorites -> user=$username,override=${firstPageUrlOverride?.let(::summarizeUrl) ?: "none"}"
    }
    galleryStore.invalidateSection(UserGalleryPageStore.Section.Favorites)
    val state = loadFavoritesPage(username = username, nextPageUrl = firstPageUrlOverride)
    log.i { "刷新Favorites -> ${summarizeResourceResult(state)}" }
    return state
  }
}
