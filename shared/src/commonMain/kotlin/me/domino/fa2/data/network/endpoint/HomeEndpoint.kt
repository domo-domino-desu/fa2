package me.domino.fa2.data.network.endpoint

import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.util.FaUrls

/** 首页请求端点。 */
class HomeEndpoint(
    /** HTML 数据源。 */
    private val dataSource: FaHtmlDataSource
) {
  /** 拉取首页 HTML。 */
  suspend fun fetch(): HtmlResponseResult = dataSource.get(FaUrls.home)
}
