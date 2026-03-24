package me.domino.fa2.data.network.endpoint

import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.util.FaUrls

/** Journals 列表端点。 */
class JournalsEndpoint(
    /** HTML 数据源。 */
    private val dataSource: FaHtmlDataSource
) {
  /** 拉取用户 journals 首页。 */
  suspend fun fetch(username: String): HtmlResponseResult =
      dataSource.get(FaUrls.journals(username))

  /** 按完整 URL 拉取分页。 */
  suspend fun fetchByUrl(url: String): HtmlResponseResult = dataSource.get(url)
}
