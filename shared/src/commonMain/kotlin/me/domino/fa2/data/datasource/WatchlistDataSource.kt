package me.domino.fa2.data.datasource

import me.domino.fa2.util.toPageState

import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistPage
import me.domino.fa2.data.network.endpoint.WatchlistEndpoint
import me.domino.fa2.data.parser.WatchlistParser

/**
 * watchlist 远端数据源。
 */
class WatchlistDataSource(
    private val endpoint: WatchlistEndpoint,
    private val parser: WatchlistParser,
) {
    /**
     * 拉取 watchlist 分页。
     */
    suspend fun fetchPage(
        username: String,
        category: WatchlistCategory,
        nextPageUrl: String?,
    ): PageState<WatchlistPage> {
        val response = if (nextPageUrl.isNullOrBlank()) {
            when (category) {
                WatchlistCategory.WatchedBy -> endpoint.fetchWatchedBy(username)
                WatchlistCategory.Watching -> endpoint.fetchWatching(username)
            }
        } else {
            endpoint.fetchByUrl(nextPageUrl)
        }
        return response.toPageState { success ->
            parser.parse(
                html = success.body,
                baseUrl = success.url,
            )
        }
    }
}

