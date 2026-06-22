package me.domino.fa2.data.fa.user

import me.domino.fa2.data.fa.core.FaHtmlDataSource
import me.domino.fa2.data.fa.core.HtmlResponseResult
import me.domino.fa2.utils.FaUrls

/** User 页面端点。 */
class UserEndpoint(
    /** HTML 数据源。 */
    private val dataSource: FaHtmlDataSource
) {
  /** 拉取用户主页。 */
  suspend fun fetch(username: String): HtmlResponseResult = dataSource.get(FaUrls.user(username))

  /** 按完整 URL 拉取用户页。 */
  suspend fun fetchByUrl(url: String): HtmlResponseResult = dataSource.get(url)
}
