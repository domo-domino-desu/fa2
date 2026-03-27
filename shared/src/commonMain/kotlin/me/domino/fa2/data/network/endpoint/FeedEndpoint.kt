package me.domino.fa2.data.network.endpoint

import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.util.FaUrls

/** Feed 请求端点。 */
class FeedEndpoint(
    /** HTML 数据源。 */
    private val dataSource: FaHtmlDataSource
) {
  /**
   * 拉取 submissions 页面。
   *
   * @param fromSid 分页游标。
   */
  suspend fun fetch(fromSid: Int? = null): HtmlResponseResult =
      dataSource.get(FaUrls.submissions(fromSid))

  /** 按完整 URL 拉取 submissions 页面。 */
  suspend fun fetchByUrl(url: String): HtmlResponseResult = dataSource.get(url)
}
