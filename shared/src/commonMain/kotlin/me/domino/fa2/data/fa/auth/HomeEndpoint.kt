package me.domino.fa2.data.fa.auth

import me.domino.fa2.data.fa.core.FaHtmlDataSource
import me.domino.fa2.data.fa.core.HtmlResponseResult
import me.domino.fa2.utils.FaUrls

/** 首页请求端点。 */
class HomeEndpoint(
    /** HTML 数据源。 */
    private val dataSource: FaHtmlDataSource
) {
  /** 拉取首页 HTML。 */
  suspend fun fetch(): HtmlResponseResult = dataSource.get(FaUrls.home)
}
