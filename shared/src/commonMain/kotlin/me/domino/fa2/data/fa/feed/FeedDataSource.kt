package me.domino.fa2.data.fa.feed

import me.domino.fa2.data.fa.core.toResourceResult
import me.domino.fa2.data.model.FeedPage
import me.domino.fa2.data.model.ResourceResult

/** Feed 远端数据源。 */
class FeedDataSource(private val endpoint: FeedEndpoint, private val parser: FeedParser) {
  /** 拉取 feed 分页。 */
  suspend fun fetchPage(fromSid: Int?): ResourceResult<FeedPage> =
      endpoint.fetch(fromSid).toResourceResult { success ->
        parser.parse(html = success.body, baseUrl = success.url)
      }

  /** 按完整 URL 拉取 feed 分页。 */
  suspend fun fetchPageByUrl(url: String): ResourceResult<FeedPage> =
      endpoint.fetchByUrl(url).toResourceResult { success ->
        parser.parse(html = success.body, baseUrl = success.url)
      }
}
