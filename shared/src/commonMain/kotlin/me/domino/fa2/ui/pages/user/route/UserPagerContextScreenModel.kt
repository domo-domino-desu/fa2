package me.domino.fa2.ui.pages.user.route

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import fa2.shared.generated.resources.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistPage
import me.domino.fa2.data.model.WatchlistUser
import me.domino.fa2.ui.i18n.appString

internal sealed interface UserPagerSource {
  data class Watchlist(val ownerUsername: String, val category: WatchlistCategory) : UserPagerSource

  data object Recommendation : UserPagerSource

  data object SimilarUsers : UserPagerSource

  data object RecommendationBlocklist : UserPagerSource
}

internal data class UserPagerUiState(
    val source: UserPagerSource? = null,
    val users: List<WatchlistUser> = emptyList(),
    val currentUsername: String = "",
    val currentIndex: Int = 0,
    val nextPageUrl: String? = null,
    val loadingMore: Boolean = false,
    val appendErrorMessage: String? = null,
) {
  val hasMore: Boolean
    get() = !nextPageUrl.isNullOrBlank()
}

internal class UserPagerContextScreenModel :
    StateScreenModel<UserPagerUiState>(UserPagerUiState()) {
  private var appendJob: Job? = null

  fun seed(
      source: UserPagerSource,
      users: List<WatchlistUser>,
      selectedUsername: String,
      nextPageUrl: String? = null,
  ) {
    val normalizedUsers = users.distinctBy { it.username.lowercase() }
    val selectedKey = selectedUsername.trim().lowercase()
    val selectedIndex = normalizedUsers.indexOfFirst { it.username.equals(selectedKey, true) }
    mutableState.value =
        UserPagerUiState(
            source = source,
            users = normalizedUsers,
            currentUsername =
                normalizedUsers.getOrNull(selectedIndex.coerceAtLeast(0))?.username
                    ?: selectedUsername,
            currentIndex = selectedIndex.takeIf { it >= 0 } ?: 0,
            nextPageUrl = nextPageUrl,
        )
  }

  fun setCurrentIndex(index: Int) {
    val snapshot = state.value
    val safeIndex = index.coerceIn(0, (snapshot.users.size - 1).coerceAtLeast(0))
    val user = snapshot.users.getOrNull(safeIndex) ?: return
    if (snapshot.currentIndex == safeIndex && snapshot.currentUsername == user.username) return
    mutableState.value = snapshot.copy(currentIndex = safeIndex, currentUsername = user.username)
  }

  fun updateUsers(users: List<WatchlistUser>, nextPageUrl: String? = state.value.nextPageUrl) {
    val snapshot = state.value
    val normalizedUsers = users.distinctBy { it.username.lowercase() }
    val anchoredIndex =
        normalizedUsers.indexOfFirst { it.username.equals(snapshot.currentUsername, true) }
    val fallbackIndex =
        snapshot.currentIndex.coerceIn(0, (normalizedUsers.size - 1).coerceAtLeast(0))
    val nextIndex = anchoredIndex.takeIf { it >= 0 } ?: fallbackIndex
    mutableState.value =
        snapshot.copy(
            users = normalizedUsers,
            currentIndex = nextIndex,
            currentUsername =
                normalizedUsers.getOrNull(nextIndex)?.username ?: snapshot.currentUsername,
            nextPageUrl = nextPageUrl,
        )
  }

  fun requestAppend(
      force: Boolean = false,
      loadPage: suspend (nextPageUrl: String) -> PageState<WatchlistPage>,
  ) {
    val snapshot = state.value
    val nextUrl = snapshot.nextPageUrl?.trim().takeUnless { it.isNullOrBlank() } ?: return
    if (appendJob?.isActive == true) return
    if (!force && snapshot.loadingMore) return
    mutableState.value = snapshot.copy(loadingMore = true, appendErrorMessage = null)
    appendJob =
        screenModelScope.launch {
          val result = loadPage(nextUrl)
          val current = state.value
          mutableState.value =
              when (result) {
                is PageState.Success -> {
                  val merged =
                      (current.users + result.data.users).distinctBy { it.username.lowercase() }
                  val anchoredIndex =
                      merged.indexOfFirst { it.username.equals(current.currentUsername, true) }
                  val fallbackIndex =
                      current.currentIndex.coerceIn(0, (merged.size - 1).coerceAtLeast(0))
                  val nextIndex = anchoredIndex.takeIf { it >= 0 } ?: fallbackIndex
                  current.copy(
                      users = merged,
                      currentIndex = nextIndex,
                      currentUsername =
                          merged.getOrNull(nextIndex)?.username ?: current.currentUsername,
                      nextPageUrl = result.data.nextPageUrl,
                      loadingMore = false,
                      appendErrorMessage = null,
                  )
                }

                is PageState.AuthRequired ->
                    current.copy(loadingMore = false, appendErrorMessage = result.message)

                PageState.CfChallenge ->
                    current.copy(
                        loadingMore = false,
                        appendErrorMessage = appString(Res.string.cloudflare_challenge_title),
                    )

                is PageState.MatureBlocked ->
                    current.copy(loadingMore = false, appendErrorMessage = result.reason)

                is PageState.Error ->
                    current.copy(
                        loadingMore = false,
                        appendErrorMessage =
                            result.exception.message
                                ?: appString(Res.string.load_failed_please_retry),
                    )

                PageState.Loading -> current.copy(loadingMore = false)
              }
        }
  }
}
