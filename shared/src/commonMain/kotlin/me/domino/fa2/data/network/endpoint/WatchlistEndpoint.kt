package me.domino.fa2.data.network.endpoint

import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.util.FaUrls

/** watchlist 页面端点。 */
class WatchlistEndpoint(private val dataSource: FaHtmlDataSource) {
  /** 拉取“关注该用户的人”列表页。 */
  suspend fun fetchWatchedBy(username: String): HtmlResponseResult =
    dataSource.get(FaUrls.watchlistTo(username))

  /** 拉取“该用户关注的人”列表页。 */
  suspend fun fetchWatching(username: String): HtmlResponseResult =
    dataSource.get(FaUrls.watchlistBy(username))

  /** 按完整 URL 拉取列表页。 */
  suspend fun fetchByUrl(url: String): HtmlResponseResult = dataSource.get(url)
}
