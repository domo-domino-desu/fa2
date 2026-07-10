package me.domino.fa2.data.remote.fa.watchlist

import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistPage

/** Watchlist resource contract required by watch-recommendation domain logic. */
interface WatchRecommendationWatchlistSource {
  suspend fun loadWatchlistPage(
      username: String,
      category: WatchlistCategory,
      nextPageUrl: String? = null,
  ): ResourceResult<WatchlistPage>
}
