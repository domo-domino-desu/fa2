package me.domino.fa2.data.repository

import me.domino.fa2.data.model.JournalPage
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.store.JournalsStore
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizePageState
import me.domino.fa2.util.logging.summarizeUrl

/**
 * Journals 仓储。
 */
class JournalsRepository(
    private val journalsStore: JournalsStore,
) {
    private val log = FaLog.withTag("JournalsRepository")

    /**
     * 加载 journals 分页。
     */
    suspend fun loadJournalsPage(
        username: String,
        nextPageUrl: String? = null,
    ): PageState<JournalPage> {
        log.d { "加载Journals -> user=$username,cursor=${nextPageUrl?.let(::summarizeUrl) ?: "first"}" }
        val state = journalsStore.loadPageOnce(
            username = username,
            nextPageUrl = nextPageUrl,
        )
        log.d { "加载Journals -> ${summarizePageState(state)}" }
        return state
    }

    /**
     * 强制刷新用户 journals 首页。
     */
    suspend fun refreshJournalsFirstPage(
        username: String,
    ): PageState<JournalPage> {
        log.i { "刷新Journals -> user=$username" }
        journalsStore.invalidateUser(username)
        val state = loadJournalsPage(
            username = username,
            nextPageUrl = null,
        )
        log.i { "刷新Journals -> ${summarizePageState(state)}" }
        return state
    }
}
