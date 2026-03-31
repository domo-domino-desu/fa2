package me.domino.fa2.ui.pages.watchrecommendation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.following_recommendation_blocklist_update_failed
import fa2.shared.generated.resources.load_failed_please_retry
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.domino.fa2.data.repository.WatchRecommendationBlocklistRepository
import me.domino.fa2.i18n.appString
import me.domino.fa2.util.logging.FaLog

data class WatchRecommendationBlocklistUiState(
    val usernames: List<String> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val errorMessage: String? = null,
    val inlineErrorMessage: String? = null,
    val removingUsernames: Set<String> = emptySet(),
)

class WatchRecommendationBlocklistScreenModel(
    private val blocklistRepository: WatchRecommendationBlocklistRepository,
) : StateScreenModel<WatchRecommendationBlocklistUiState>(WatchRecommendationBlocklistUiState()) {
  private val log = FaLog.withTag("WatchRecommendationBlocklistScreenModel")
  private var loadJob: Job? = null

  init {
    load()
  }

  fun load(forceRefresh: Boolean = false) {
    if (loadJob?.isActive == true) return
    val previous = state.value
    mutableState.value =
        when {
          forceRefresh && previous.usernames.isNotEmpty() ->
              previous.copy(refreshing = true, inlineErrorMessage = null, errorMessage = null)

          else -> previous.copy(loading = true, refreshing = false, inlineErrorMessage = null)
        }

    loadJob =
        screenModelScope.launch {
          runCatching { blocklistRepository.listBlockedUsernames() }
              .onSuccess { usernames ->
                mutableState.value =
                    WatchRecommendationBlocklistUiState(
                        usernames = usernames,
                        loading = false,
                    )
                log.i { "推荐屏蔽名单 -> 加载成功(count=${usernames.size})" }
              }
              .onFailure { error ->
                val message = error.message ?: appString(Res.string.load_failed_please_retry)
                mutableState.value =
                    if (previous.usernames.isNotEmpty()) {
                      previous.copy(
                          loading = false,
                          refreshing = false,
                          inlineErrorMessage = message,
                      )
                    } else {
                      previous.copy(
                          loading = false,
                          refreshing = false,
                          errorMessage = message,
                      )
                    }
                log.e(error) { "推荐屏蔽名单 -> 加载失败" }
              }
        }
  }

  fun removeBlockedUsername(username: String) {
    val snapshot = state.value
    val normalizedUsername = username.trim().lowercase()
    if (normalizedUsername.isBlank() || normalizedUsername in snapshot.removingUsernames) return
    mutableState.value =
        snapshot.copy(
            inlineErrorMessage = null,
            removingUsernames = snapshot.removingUsernames + normalizedUsername,
        )

    screenModelScope.launch {
      runCatching { blocklistRepository.removeBlockedUsername(username) }
          .onSuccess {
            val current = state.value
            mutableState.value =
                current.copy(
                    usernames =
                        current.usernames.filterNot { candidate ->
                          candidate.equals(username, ignoreCase = true)
                        },
                    removingUsernames = current.removingUsernames - normalizedUsername,
                )
            log.i { "推荐屏蔽名单 -> 已移除(user=$username)" }
          }
          .onFailure { error ->
            val current = state.value
            val detail = error.message ?: error::class.simpleName.orEmpty()
            mutableState.value =
                current.copy(
                    inlineErrorMessage =
                        appString(
                            Res.string.following_recommendation_blocklist_update_failed,
                            detail,
                        ),
                    removingUsernames = current.removingUsernames - normalizedUsername,
                )
            log.e(error) { "推荐屏蔽名单 -> 移除失败(user=$username)" }
          }
    }
  }
}
