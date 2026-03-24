package me.domino.fa2.data.datasource

import me.domino.fa2.data.model.FeedPage
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.network.endpoint.FeedEndpoint
import me.domino.fa2.data.parser.FeedParser
import me.domino.fa2.util.toPageState

/** Feed 远端数据源。 */
class FeedDataSource(private val endpoint: FeedEndpoint, private val parser: FeedParser) {
  /** 拉取 feed 分页。 */
  suspend fun fetchPage(fromSid: Int?): PageState<FeedPage> =
      endpoint.fetch(fromSid).toPageState { success ->
        parser.parse(html = success.body, baseUrl = success.url)
      }
}
