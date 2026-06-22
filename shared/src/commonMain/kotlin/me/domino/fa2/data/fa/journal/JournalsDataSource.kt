package me.domino.fa2.data.fa.journal

import me.domino.fa2.data.fa.core.toPageState
import me.domino.fa2.data.model.JournalPage
import me.domino.fa2.data.model.PageState

/** Journals 列表远端数据源。 */
class JournalsDataSource(
    private val endpoint: JournalsEndpoint,
    private val parser: JournalsParser,
) {
  /** 拉取 journals 分页。 */
  suspend fun fetchPage(username: String, nextPageUrl: String?): PageState<JournalPage> {
    val response =
        if (nextPageUrl.isNullOrBlank()) {
          endpoint.fetch(username)
        } else {
          endpoint.fetchByUrl(nextPageUrl)
        }
    return response.toPageState { success ->
      parser.parse(html = success.body, baseUrl = success.url)
    }
  }
}
