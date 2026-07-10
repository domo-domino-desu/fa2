package me.domino.fa2.data.remote.fa.journal

import me.domino.fa2.data.model.JournalPage
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.summarizeResourceResult
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl

/** Journals 仓储。 */
class JournalsRepository(private val journalsStore: JournalsPageCache) {
  private val log = FaLog.withTag("JournalsRepository")

  /** 加载 journals 分页。 */
  suspend fun loadJournalsPage(
      username: String,
      nextPageUrl: String? = null,
  ): ResourceResult<JournalPage> {
    log.d { "加载Journals -> user=$username,cursor=${nextPageUrl?.let(::summarizeUrl) ?: "first"}" }
    val state = journalsStore.loadPageOnce(username = username, nextPageUrl = nextPageUrl)
    log.d { "加载Journals -> ${summarizeResourceResult(state)}" }
    return state
  }

  /** 强制刷新用户 journals 首页。 */
  suspend fun refreshJournalsFirstPage(username: String): ResourceResult<JournalPage> {
    log.i { "刷新Journals -> user=$username" }
    journalsStore.invalidateUser(username)
    val state = loadJournalsPage(username = username, nextPageUrl = null)
    log.i { "刷新Journals -> ${summarizeResourceResult(state)}" }
    return state
  }
}
