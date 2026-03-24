package me.domino.fa2.data.network.endpoint

import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.util.FaUrls

/** 单篇 Journal 端点。 */
class JournalEndpoint(
    /** HTML 数据源。 */
    private val dataSource: FaHtmlDataSource
) {
  /** 按 ID 拉取日志详情。 */
  suspend fun fetch(journalId: Int): HtmlResponseResult = dataSource.get(FaUrls.journal(journalId))

  /** 按完整 URL 拉取日志详情。 */
  suspend fun fetchByUrl(url: String): HtmlResponseResult = dataSource.get(url)
}
