package me.domino.fa2.data.fa.gallery

import me.domino.fa2.data.model.GalleryPage
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.summarizePageState
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl

/** Gallery 仓储。 */
class GalleryRepository(private val galleryStore: GalleryPageCache) {
  private val log = FaLog.withTag("GalleryRepository")

  /** 加载 gallery 分页。 */
  suspend fun loadGalleryPage(
      username: String,
      nextPageUrl: String? = null,
  ): PageState<GalleryPage> {
    log.d { "加载Gallery -> user=$username,cursor=${nextPageUrl?.let(::summarizeUrl) ?: "first"}" }
    val state =
        galleryStore.loadPageOnce(
            section = GalleryPageCache.Section.Gallery,
            username = username,
            nextPageUrl = nextPageUrl,
        )
    log.d { "加载Gallery -> ${summarizePageState(state)}" }
    return state
  }

  /** 强制刷新 gallery 首页。 */
  suspend fun refreshGalleryFirstPage(
      username: String,
      firstPageUrlOverride: String? = null,
  ): PageState<GalleryPage> {
    log.i {
      "刷新Gallery -> user=$username,override=${firstPageUrlOverride?.let(::summarizeUrl) ?: "none"}"
    }
    galleryStore.invalidateSection(GalleryPageCache.Section.Gallery)
    val state = loadGalleryPage(username = username, nextPageUrl = firstPageUrlOverride)
    log.i { "刷新Gallery -> ${summarizePageState(state)}" }
    return state
  }
}
