package me.domino.fa2.data.network.endpoint

import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.util.FaUrls

/** Submission 请求端点。 */
class SubmissionEndpoint(
    /** HTML 数据源。 */
    private val dataSource: FaHtmlDataSource
) {
  /**
   * 按 sid 拉取 submission 详情页。
   *
   * @param sid 投稿 ID。
   */
  suspend fun fetchBySid(sid: Int): HtmlResponseResult = dataSource.get(FaUrls.submission(sid))

  /**
   * 按绝对 URL 拉取 submission 详情页。
   *
   * @param url 投稿详情页地址。
   */
  suspend fun fetchByUrl(url: String): HtmlResponseResult = dataSource.get(url)
}
