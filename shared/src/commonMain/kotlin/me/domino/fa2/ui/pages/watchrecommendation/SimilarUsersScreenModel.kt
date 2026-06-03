package me.domino.fa2.ui.pages.watchrecommendation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.following_recommendation_blocklist_update_failed
import fa2.shared.generated.resources.load_failed_please_retry
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.domino.fa2.application.watchrecommendation.WatchRecommendationService
import me.domino.fa2.data.repository.WatchRecommendationBlocklistRepository
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.i18n.appString
import me.domino.fa2.util.logging.FaLog

class SimilarUsersScreenModel(
    private val username: String,
    private val recommendationService: WatchRecommendationService,
    private val blocklistRepository: WatchRecommendationBlocklistRepository,
    private val settingsService: AppSettingsService,
) : StateScreenModel<WatchRecommendationUiState>(WatchRecommendationUiState.Idle) {
  private val log = FaLog.withTag("SimilarUsersScreenModel")
  private var loadJob: Job? = null

  fun loadSimilarUsers() {
    if (loadJob?.isActive == true) return
    val previous = state.value
    mutableState.value =
        when (previous) {
          is WatchRecommendationUiState.Success ->
              previous.copy(
                  refreshing = true,
                  inlineErrorMessage = null,
                  blockingUsernames = emptySet(),
              )

          else -> WatchRecommendationUiState.Loading
        }

    loadJob =
        screenModelScope.launch {
          runCatching {
                settingsService.ensureLoaded()
                recommendationService.recommendFromFollowers(
                    username = username,
                    recommendationCount =
                        settingsService.settings.value.watchRecommendationPageSize,
                )
              }
              .onSuccess { users ->
                mutableState.value = WatchRecommendationUiState.Success(users = users)
                log.i { "相似用户 -> 成功(user=$username,count=${users.size})" }
              }
              .onFailure { error ->
                val message = error.message ?: appString(Res.string.load_failed_please_retry)
                mutableState.value =
                    when (previous) {
                      is WatchRecommendationUiState.Success ->
                          previous.copy(refreshing = false, inlineErrorMessage = message)

                      else -> WatchRecommendationUiState.Error(message)
                    }
                log.e(error) { "相似用户 -> 失败(user=$username)" }
              }
        }
  }

  fun refreshSimilarUsers() {
    val snapshot = state.value
    if (snapshot !is WatchRecommendationUiState.Success) return
    loadSimilarUsers()
  }

  fun blockSimilarUser(username: String) {
    val snapshot = state.value as? WatchRecommendationUiState.Success ?: return
    val normalizedUsername = username.trim().lowercase()
    if (normalizedUsername.isBlank() || normalizedUsername in snapshot.blockingUsernames) return
    mutableState.value =
        snapshot.copy(
            inlineErrorMessage = null,
            blockingUsernames = snapshot.blockingUsernames + normalizedUsername,
        )

    screenModelScope.launch {
      runCatching { blocklistRepository.addBlockedUsername(username) }
          .onSuccess {
            val current = state.value as? WatchRecommendationUiState.Success ?: return@onSuccess
            mutableState.value =
                current.copy(
                    users =
                        current.users.filterNot { candidate ->
                          candidate.user.username.equals(username, ignoreCase = true)
                        },
                    blockingUsernames = current.blockingUsernames - normalizedUsername,
                )
            log.i { "相似用户 -> 已加入手动屏蔽(user=$username)" }
          }
          .onFailure { error ->
            val current = state.value as? WatchRecommendationUiState.Success ?: return@onFailure
            val detail = error.message ?: error::class.simpleName.orEmpty()
            mutableState.value =
                current.copy(
                    inlineErrorMessage =
                        appString(
                            Res.string.following_recommendation_blocklist_update_failed,
                            detail,
                        ),
                    blockingUsernames = current.blockingUsernames - normalizedUsername,
                )
            log.e(error) { "相似用户 -> 加入手动屏蔽失败(user=$username)" }
          }
    }
  }
}
