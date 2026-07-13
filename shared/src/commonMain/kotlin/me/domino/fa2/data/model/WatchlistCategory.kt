package me.domino.fa2.data.model

import kotlinx.serialization.Serializable

/** 关注列表类型。 */
@Serializable
enum class WatchlistCategory {
  /** 关注该用户的人（watchlist/to）。 */
  WatchedBy,

  /** 该用户关注的人（watchlist/by）。 */
  Watching,
}
