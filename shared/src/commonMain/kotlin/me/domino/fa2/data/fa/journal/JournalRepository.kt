package me.domino.fa2.data.fa.journal

import me.domino.fa2.data.model.JournalDetail
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.summarizePageState
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl

/** Journal 详情读取接口。 */

/** Journal 详情仓储。 */
open class JournalRepository(private val journalStore: JournalPageCache? = null) {
  private val log = FaLog.withTag("JournalRepository")

  /** 按 ID 加载日志详情。 */
  open suspend fun loadJournalDetail(journalId: Int): PageState<JournalDetail> {
    log.d { "加载Journal详情 -> id=$journalId" }
    val state = requireNotNull(journalStore) { "JournalPageCache is required" }.loadById(journalId)
    log.d { "加载Journal详情 -> ${summarizePageState(state)}" }
    return state
  }

  /** 按 URL 加载日志详情。 */
  open suspend fun loadJournalDetailByUrl(url: String): PageState<JournalDetail> {
    log.d { "加载Journal详情 -> url=${summarizeUrl(url)}" }
    val state = requireNotNull(journalStore) { "JournalPageCache is required" }.loadByUrl(url)
    log.d { "加载Journal详情 -> ${summarizePageState(state)}" }
    return state
  }
}
