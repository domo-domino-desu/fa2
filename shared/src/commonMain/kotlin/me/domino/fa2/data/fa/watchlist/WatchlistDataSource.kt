package me.domino.fa2.data.fa.watchlist

import me.domino.fa2.data.fa.core.toPageState
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistPage

/** watchlist 远端数据源。 */
class WatchlistDataSource(
    private val endpoint: WatchlistEndpoint,
    private val parser: WatchlistParser,
) {
  /** 拉取 watchlist 分页。 */
  suspend fun fetchPage(
      username: String,
      category: WatchlistCategory,
      nextPageUrl: String?,
  ): PageState<WatchlistPage> {
    val response =
        if (nextPageUrl.isNullOrBlank()) {
          when (category) {
            WatchlistCategory.WatchedBy -> endpoint.fetchWatchedBy(username)
            WatchlistCategory.Watching -> endpoint.fetchWatching(username)
          }
        } else {
          endpoint.fetchByUrl(nextPageUrl)
        }
    return response.toPageState { success ->
      parser.parse(html = success.body, baseUrl = success.url)
    }
  }
}
