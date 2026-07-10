package me.domino.fa2.data.remote.fa.journal

import me.domino.fa2.data.model.JournalPage
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.remote.fa.core.toResourceResult

/** Journals 列表远端数据源。 */
class JournalsDataSource(
    private val endpoint: JournalsEndpoint,
    private val parser: JournalsParser,
) {
  /** 拉取 journals 分页。 */
  suspend fun fetchPage(username: String, nextPageUrl: String?): ResourceResult<JournalPage> {
    val response =
        if (nextPageUrl.isNullOrBlank()) {
          endpoint.fetch(username)
        } else {
          endpoint.fetchByUrl(nextPageUrl)
        }
    return response.toResourceResult { success ->
      parser.parse(html = success.body, baseUrl = success.url)
    }
  }
}
