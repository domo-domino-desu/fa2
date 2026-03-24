package me.domino.fa2.data.network.endpoint

import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.util.FaUrls

/** Gallery 页面端点。 */
class GalleryEndpoint(
    /** HTML 数据源。 */
    private val dataSource: FaHtmlDataSource
) {
  /** 拉取用户画廊首页。 */
  suspend fun fetch(username: String): HtmlResponseResult = dataSource.get(FaUrls.gallery(username))

  /** 按完整 URL 拉取分页。 */
  suspend fun fetchByUrl(url: String): HtmlResponseResult = dataSource.get(url)
}
