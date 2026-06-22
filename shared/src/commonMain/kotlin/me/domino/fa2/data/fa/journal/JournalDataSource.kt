package me.domino.fa2.data.fa.journal

import me.domino.fa2.data.fa.core.toPageState
import me.domino.fa2.data.model.JournalDetail
import me.domino.fa2.data.model.PageState

/** Journal 详情远端数据源。 */
class JournalDataSource(private val endpoint: JournalEndpoint, private val parser: JournalParser) {
  /** 按 ID 拉取日志详情。 */
  suspend fun fetchById(journalId: Int): PageState<JournalDetail> =
      endpoint.fetch(journalId).toPageState { success ->
        parser.parse(html = success.body, url = success.url)
      }

  /** 按 URL 拉取日志详情。 */
  suspend fun fetchByUrl(url: String): PageState<JournalDetail> =
      endpoint.fetchByUrl(url).toPageState { success ->
        parser.parse(html = success.body, url = success.url)
      }
}
