package me.domino.fa2.data.repository

import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.GalleryPage
import me.domino.fa2.data.store.GalleryStore
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizePageState
import me.domino.fa2.util.logging.summarizeUrl

/**
 * Favorites 仓储。
 */
class FavoritesRepository(
    private val galleryStore: GalleryStore,
) {
    private val log = FaLog.withTag("FavoritesRepository")

    /**
     * 加载 favorites 分页。
     */
    suspend fun loadFavoritesPage(
        username: String,
        nextPageUrl: String? = null,
    ): PageState<GalleryPage> {
        log.d { "加载Favorites -> user=$username,cursor=${nextPageUrl?.let(::summarizeUrl) ?: "first"}" }
        val state = galleryStore.loadPageOnce(
            section = GalleryStore.Section.Favorites,
            username = username,
            nextPageUrl = nextPageUrl,
        )
        log.d { "加载Favorites -> ${summarizePageState(state)}" }
        return state
    }

    /**
     * 强制刷新 favorites 首页。
     */
    suspend fun refreshFavoritesFirstPage(
        username: String,
        firstPageUrlOverride: String? = null,
    ): PageState<GalleryPage> {
        log.i { "刷新Favorites -> user=$username,override=${firstPageUrlOverride?.let(::summarizeUrl) ?: "none"}" }
        galleryStore.invalidateSection(GalleryStore.Section.Favorites)
        val state = loadFavoritesPage(
            username = username,
            nextPageUrl = firstPageUrlOverride,
        )
        log.i { "刷新Favorites -> ${summarizePageState(state)}" }
        return state
    }
}
