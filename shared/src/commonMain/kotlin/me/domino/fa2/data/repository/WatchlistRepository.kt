package me.domino.fa2.data.repository

import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistPage
import me.domino.fa2.data.store.WatchlistStore
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizePageState
import me.domino.fa2.util.logging.summarizeUrl

/**
 * watchlist 仓储。
 */
class WatchlistRepository(
    private val store: WatchlistStore,
) {
    private val log = FaLog.withTag("WatchlistRepository")

    /**
     * 加载 watchlist 分页。
     */
    suspend fun loadWatchlistPage(
        username: String,
        category: WatchlistCategory,
        nextPageUrl: String? = null,
    ): PageState<WatchlistPage> {
        log.d {
            "加载Watchlist -> user=$username,category=$category,cursor=${nextPageUrl?.let(::summarizeUrl) ?: "first"}"
        }
        val state = store.loadPageOnce(
            username = username,
            category = category,
            nextPageUrl = nextPageUrl,
        )
        log.d { "加载Watchlist -> ${summarizePageState(state)}" }
        return state
    }

    /**
     * 强制刷新 watchlist 首页。
     */
    suspend fun refreshWatchlistFirstPage(
        username: String,
        category: WatchlistCategory,
    ): PageState<WatchlistPage> {
        log.i { "刷新Watchlist -> user=$username,category=$category" }
        store.invalidateUserCategory(username = username, category = category)
        val state = loadWatchlistPage(
            username = username,
            category = category,
            nextPageUrl = null,
        )
        log.i { "刷新Watchlist -> ${summarizePageState(state)}" }
        return state
    }
}
