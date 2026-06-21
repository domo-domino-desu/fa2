package me.domino.fa2.ui.pages.user.route

import cafe.adriel.voyager.navigator.Navigator
import me.domino.fa2.data.model.User
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.ui.pages.user.shout.UserShoutsRouteScreen
import me.domino.fa2.ui.pages.user.watchlist.UserWatchlistRouteScreen
import me.domino.fa2.util.FaUrls

data class UserHeaderNavigationActions(
    val onOpenShouts: () -> Unit,
    val onOpenWatchedBy: () -> Unit,
    val onOpenWatching: () -> Unit,
)

internal fun userHeaderNavigationActions(
    username: String,
    header: User?,
    navigator: Navigator,
): UserHeaderNavigationActions {
  val normalizedUsername = username.trim()
  return UserHeaderNavigationActions(
      onOpenShouts = { navigator.push(UserShoutsRouteScreen(username = normalizedUsername)) },
      onOpenWatchedBy = {
        val initialUrl =
            header?.watchedByListUrl?.trim()?.takeIf { value -> value.isNotBlank() }
                ?: FaUrls.watchlistTo(normalizedUsername)
        navigator.push(
            UserWatchlistRouteScreen(
                username = normalizedUsername,
                category = WatchlistCategory.WatchedBy,
                initialUrl = initialUrl,
            )
        )
      },
      onOpenWatching = {
        val initialUrl =
            header?.watchingListUrl?.trim()?.takeIf { value -> value.isNotBlank() }
                ?: FaUrls.watchlistBy(normalizedUsername)
        navigator.push(
            UserWatchlistRouteScreen(
                username = normalizedUsername,
                category = WatchlistCategory.Watching,
                initialUrl = initialUrl,
            )
        )
      },
  )
}
