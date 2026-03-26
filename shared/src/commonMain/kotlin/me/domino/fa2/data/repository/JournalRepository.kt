package me.domino.fa2.data.repository

import me.domino.fa2.data.model.JournalDetail
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.store.JournalStore
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizePageState
import me.domino.fa2.util.logging.summarizeUrl

/** Journal 详情读取接口。 */
interface JournalDetailRepository {
  suspend fun loadJournalDetail(journalId: Int): PageState<JournalDetail>

  suspend fun loadJournalDetailByUrl(url: String): PageState<JournalDetail>
}

/** Journal 详情仓储。 */
class JournalRepository(private val journalStore: JournalStore) : JournalDetailRepository {
  private val log = FaLog.withTag("JournalRepository")

  /** 按 ID 加载日志详情。 */
  override suspend fun loadJournalDetail(journalId: Int): PageState<JournalDetail> {
    log.d { "加载Journal详情 -> id=$journalId" }
    val state = journalStore.loadById(journalId)
    log.d { "加载Journal详情 -> ${summarizePageState(state)}" }
    return state
  }

  /** 按 URL 加载日志详情。 */
  override suspend fun loadJournalDetailByUrl(url: String): PageState<JournalDetail> {
    log.d { "加载Journal详情 -> url=${summarizeUrl(url)}" }
    val state = journalStore.loadByUrl(url)
    log.d { "加载Journal详情 -> ${summarizePageState(state)}" }
    return state
  }
}
