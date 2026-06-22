package me.domino.fa2.data.fa.feed

import me.domino.fa2.data.fa.core.FaHtmlDataSource
import me.domino.fa2.data.fa.core.HtmlResponseResult
import me.domino.fa2.utils.FaUrls

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
