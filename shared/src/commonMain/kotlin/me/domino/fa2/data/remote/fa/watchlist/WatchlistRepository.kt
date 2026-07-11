package me.domino.fa2.data.remote.fa.watchlist

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.domino.fa2.data.model.QueryState
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistPage
import me.domino.fa2.data.model.WatchlistUser
import me.domino.fa2.data.remote.fa.core.FaHtmlDataSource
import me.domino.fa2.data.remote.fa.core.PageQueryCache
import me.domino.fa2.data.remote.fa.core.PageType
import me.domino.fa2.data.remote.fa.core.cacheJson
import me.domino.fa2.data.remote.fa.core.fetchParsed
import me.domino.fa2.utils.FaUrls
import me.domino.fa2.utils.concurrency.SequentialRequestThrottle

class WatchlistRepository(
    private val html: FaHtmlDataSource,
    private val parser: WatchlistParser,
    private val queries: PageQueryCache,
) : WatchRecommendationWatchlistSource {
  fun watchlist(
      username: String,
      category: WatchlistCategory,
      nextPageUrl: String? = null,
  ): Flow<QueryState<WatchlistPage>> =
      queries.queryFlow(
          key(username, category, nextPageUrl),
          PageType.Watchlist,
          { cacheJson.encodeToString(it) },
          ::decode,
      ) {
        fetch(username, category, nextPageUrl)
      }

  override suspend fun loadWatchlistPage(
      username: String,
      category: WatchlistCategory,
      nextPageUrl: String?,
  ): ResourceResult<WatchlistPage> =
      queries.queryOnce(
          key(username, category, nextPageUrl),
          PageType.Watchlist,
          { cacheJson.encodeToString(it) },
          ::decode,
      ) {
        fetch(username, category, nextPageUrl)
      }

  suspend fun refreshWatchlistFirstPage(
      username: String,
      category: WatchlistCategory,
  ): ResourceResult<WatchlistPage> {
    queries.invalidate(PageType.Watchlist)
    return loadWatchlistPage(username, category, null)
  }

  suspend fun loadAllWatchlistUsers(
      username: String,
      category: WatchlistCategory,
      useFreshFirstPage: Boolean = false,
  ): ResourceResult<List<WatchlistUser>> {
    val throttle = SequentialRequestThrottle(600)
    val users = linkedMapOf<String, WatchlistUser>()
    var cursor: String? = null
    var first = true
    while (true) {
      throttle.awaitReady()
      val result =
          if (first && useFreshFirstPage) refreshWatchlistFirstPage(username, category)
          else loadWatchlistPage(username, category, cursor)
      first = false
      when (result) {
        is ResourceResult.Success -> {
          result.data.users.forEach { users.putIfAbsent(it.username.lowercase(), it) }
          cursor = result.data.nextPageUrl
          if (cursor == null) return ResourceResult.Success(users.values.toList())
        }
        is ResourceResult.AuthRequired -> return result
        ResourceResult.CfChallenge -> return ResourceResult.CfChallenge
        is ResourceResult.MatureBlocked -> return result
        is ResourceResult.Error -> return result
      }
    }
  }

  private suspend fun fetch(username: String, category: WatchlistCategory, cursor: String?) =
      html.fetchParsed(
          cursor
              ?: when (category) {
                WatchlistCategory.WatchedBy -> FaUrls.watchlistTo(username)
                WatchlistCategory.Watching -> FaUrls.watchlistBy(username)
              }
      ) { body, finalUrl ->
        parser.parse(body, finalUrl)
      }

  private fun key(username: String, category: WatchlistCategory, cursor: String?) =
      "watchlist:${category.name}:username=${username.trim().lowercase()}:cursor=${cursor ?: "first"}"

  private fun decode(json: String) =
      runCatching { cacheJson.decodeFromString<WatchlistPage>(json) }.getOrNull()
}
