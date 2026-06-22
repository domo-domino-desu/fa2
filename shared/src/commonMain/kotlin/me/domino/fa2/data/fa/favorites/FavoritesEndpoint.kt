package me.domino.fa2.data.fa.favorites

import me.domino.fa2.data.fa.core.FaHtmlDataSource
import me.domino.fa2.data.fa.core.HtmlResponseResult
import me.domino.fa2.utils.FaUrls

/** Favorites 页面端点。 */
class FavoritesEndpoint(
    /** HTML 数据源。 */
    private val dataSource: FaHtmlDataSource
) {
  /** 拉取用户收藏首页。 */
  suspend fun fetch(username: String): HtmlResponseResult =
      dataSource.get(FaUrls.favorites(username))

  /** 按完整 URL 拉取分页。 */
  suspend fun fetchByUrl(url: String): HtmlResponseResult = dataSource.get(url)
}
