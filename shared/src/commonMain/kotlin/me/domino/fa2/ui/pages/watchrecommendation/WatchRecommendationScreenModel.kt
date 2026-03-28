package me.domino.fa2.ui.pages.watchrecommendation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.load_failed_please_retry
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.domino.fa2.application.watchrecommendation.RecommendedWatchUser
import me.domino.fa2.application.watchrecommendation.WatchRecommendationService
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.i18n.appString
import me.domino.fa2.util.logging.FaLog

sealed interface WatchRecommendationUiState {
  data object Idle : WatchRecommendationUiState

  data object Loading : WatchRecommendationUiState

  data class Success(
      val users: List<RecommendedWatchUser>,
      val refreshing: Boolean = false,
      val inlineErrorMessage: String? = null,
  ) : WatchRecommendationUiState

  data class Error(val message: String) : WatchRecommendationUiState
}

class WatchRecommendationScreenModel(
    private val username: String,
    private val recommendationService: WatchRecommendationService,
    private val settingsService: AppSettingsService,
) : StateScreenModel<WatchRecommendationUiState>(WatchRecommendationUiState.Idle) {
  private val log = FaLog.withTag("WatchRecommendationScreenModel")
  private var loadJob: Job? = null

  fun loadRecommendations() {
    if (loadJob?.isActive == true) return
    val previous = state.value
    mutableState.value =
        when (previous) {
          is WatchRecommendationUiState.Success ->
              previous.copy(refreshing = true, inlineErrorMessage = null)

          else -> WatchRecommendationUiState.Loading
        }

    loadJob =
        screenModelScope.launch {
          runCatching {
                settingsService.ensureLoaded()
                recommendationService.recommend(
                    username = username,
                    recommendationCount =
                        settingsService.settings.value.watchRecommendationPageSize,
                )
              }
              .onSuccess { users ->
                mutableState.value = WatchRecommendationUiState.Success(users = users)
                log.i { "关注推荐 -> 成功(user=$username,count=${users.size})" }
              }
              .onFailure { error ->
                val message = error.message ?: appString(Res.string.load_failed_please_retry)
                mutableState.value =
                    when (previous) {
                      is WatchRecommendationUiState.Success ->
                          previous.copy(refreshing = false, inlineErrorMessage = message)

                      else -> WatchRecommendationUiState.Error(message)
                    }
                log.e(error) { "关注推荐 -> 失败(user=$username)" }
              }
        }
  }

  fun refreshRecommendations() {
    val snapshot = state.value
    if (snapshot !is WatchRecommendationUiState.Success) return
    loadRecommendations()
  }
}
