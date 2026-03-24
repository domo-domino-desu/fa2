package me.domino.fa2.data.repository

import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.SubmissionListingPage
import me.domino.fa2.data.store.BrowseStore
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizePageState
import me.domino.fa2.util.logging.summarizeUrl

/**
 * Browse 仓储。
 */
class BrowseRepository(
    private val store: BrowseStore,
) {
    private val log = FaLog.withTag("BrowseRepository")

    suspend fun loadPage(url: String): PageState<SubmissionListingPage> {
        val safeUrl = summarizeUrl(url)
        log.d { "加载Browse -> url=$safeUrl" }
        val state = store.loadPageOnce(requestUrl = url)
        log.d { "加载Browse -> ${summarizePageState(state)}" }
        return state
    }

    suspend fun refreshPage(url: String): PageState<SubmissionListingPage> {
        val safeUrl = summarizeUrl(url)
        log.i { "刷新Browse -> url=$safeUrl" }
        val state = store.refreshPage(requestUrl = url)
        log.i { "刷新Browse -> ${summarizePageState(state)}" }
        return state
    }
}
