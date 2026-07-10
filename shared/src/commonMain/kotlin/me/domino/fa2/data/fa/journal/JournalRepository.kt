package me.domino.fa2.data.fa.journal

import me.domino.fa2.data.model.JournalDetail
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.summarizeResourceResult
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl

/** Stable contract consumed by journal-detail UI and domain orchestration. */
interface JournalDetailRepository {
  suspend fun loadJournalDetail(journalId: Int): ResourceResult<JournalDetail>

  suspend fun loadJournalDetailByUrl(url: String): ResourceResult<JournalDetail>
}

/** Journal 详情仓储。 */
class JournalRepository(private val journalStore: JournalPageCache) : JournalDetailRepository {
  private val log = FaLog.withTag("JournalRepository")

  /** 按 ID 加载日志详情。 */
  override suspend fun loadJournalDetail(journalId: Int): ResourceResult<JournalDetail> {
    log.d { "加载Journal详情 -> id=$journalId" }
    val state = journalStore.loadById(journalId)
    log.d { "加载Journal详情 -> ${summarizeResourceResult(state)}" }
    return state
  }

  /** 按 URL 加载日志详情。 */
  override suspend fun loadJournalDetailByUrl(url: String): ResourceResult<JournalDetail> {
    log.d { "加载Journal详情 -> url=${summarizeUrl(url)}" }
    val state = journalStore.loadByUrl(url)
    log.d { "加载Journal详情 -> ${summarizeResourceResult(state)}" }
    return state
  }
}
