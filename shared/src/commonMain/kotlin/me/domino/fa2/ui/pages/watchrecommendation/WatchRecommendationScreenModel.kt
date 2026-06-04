package me.domino.fa2.ui.pages.watchrecommendation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.following_recommendation_blocklist_update_failed
import fa2.shared.generated.resources.load_failed_please_retry
import fa2.shared.generated.resources.recommendation_progress_completed
import fa2.shared.generated.resources.recommendation_progress_loading_followers_page
import fa2.shared.generated.resources.recommendation_progress_loading_following_page
import fa2.shared.generated.resources.recommendation_progress_loading_user_profile
import fa2.shared.generated.resources.recommendation_progress_random_pages
import fa2.shared.generated.resources.recommendation_progress_random_users_collected
import fa2.shared.generated.resources.recommendation_progress_regular_needs_count
import fa2.shared.generated.resources.recommendation_progress_regular_sequential
import fa2.shared.generated.resources.recommendation_progress_round_completed
import fa2.shared.generated.resources.recommendation_progress_round_started
import fa2.shared.generated.resources.recommendation_progress_sample_prepared
import fa2.shared.generated.resources.recommendation_progress_starting
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.domino.fa2.application.watchrecommendation.RecommendedWatchUser
import me.domino.fa2.application.watchrecommendation.WatchRecommendationProgress
import me.domino.fa2.application.watchrecommendation.WatchRecommendationService
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.repository.WatchRecommendationBlocklistRepository
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.i18n.appString
import me.domino.fa2.util.logging.FaLog

sealed interface WatchRecommendationUiState {
  data object Idle : WatchRecommendationUiState

  data class Loading(val logLines: List<String> = emptyList()) : WatchRecommendationUiState

  data class Success(
      val users: List<RecommendedWatchUser>,
      val randomUsers: List<RecommendedWatchUser> = users.shuffled(),
      val useRandomOrder: Boolean = false,
      val refreshing: Boolean = false,
      val inlineErrorMessage: String? = null,
      val blockingUsernames: Set<String> = emptySet(),
  ) : WatchRecommendationUiState {
    val visibleUsers: List<RecommendedWatchUser>
      get() = if (useRandomOrder) randomUsers else users
  }

  data class Error(val message: String) : WatchRecommendationUiState
}

class WatchRecommendationScreenModel(
    private val username: String,
    private val recommendationService: WatchRecommendationService,
    private val blocklistRepository: WatchRecommendationBlocklistRepository,
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
              previous.copy(
                  refreshing = true,
                  inlineErrorMessage = null,
                  blockingUsernames = emptySet(),
              )

          else ->
              WatchRecommendationUiState.Loading(
                  logLines = listOf(appString(Res.string.recommendation_progress_starting))
              )
        }

    loadJob =
        screenModelScope.launch {
          runCatching {
                settingsService.ensureLoaded()
                recommendationService.recommend(
                    username = username,
                    recommendationCount =
                        settingsService.settings.value.watchRecommendationPageSize,
                    excludeFollowingUsername = username,
                    onProgress = ::appendProgressLog,
                )
              }
              .onSuccess { users ->
                mutableState.value =
                    WatchRecommendationUiState.Success(
                        users = users,
                        randomUsers = users.shuffled(),
                    )
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

  fun toggleRandomOrder() {
    val snapshot = state.value as? WatchRecommendationUiState.Success ?: return
    mutableState.value = snapshot.copy(useRandomOrder = !snapshot.useRandomOrder)
  }

  fun blockRecommendation(username: String) {
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
                    randomUsers =
                        current.randomUsers.filterNot { candidate ->
                          candidate.user.username.equals(username, ignoreCase = true)
                        },
                    blockingUsernames = current.blockingUsernames - normalizedUsername,
                )
            log.i { "关注推荐 -> 已加入手动屏蔽(user=$username)" }
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
            log.e(error) { "关注推荐 -> 加入手动屏蔽失败(user=$username)" }
          }
    }
  }

  private fun appendProgressLog(progress: WatchRecommendationProgress) {
    val message = recommendationProgressMessage(progress)
    val snapshot = state.value as? WatchRecommendationUiState.Loading ?: return
    val nextLines = (snapshot.logLines + message).takeLast(maxRecommendationProgressLogLines)
    mutableState.value = snapshot.copy(logLines = nextLines)
  }
}

internal const val maxRecommendationProgressLogLines: Int = 6

internal fun recommendationProgressMessage(progress: WatchRecommendationProgress): String =
    when (progress) {
      is WatchRecommendationProgress.LoadingWatchlist -> {
        val totalPages = progress.totalPages?.toString() ?: "?"
        when (progress.category) {
          WatchlistCategory.WatchedBy ->
              appString(
                  Res.string.recommendation_progress_loading_followers_page,
                  progress.username,
                  progress.page,
                  totalPages,
              )

          WatchlistCategory.Watching ->
              appString(
                  Res.string.recommendation_progress_loading_following_page,
                  progress.username,
                  progress.page,
                  totalPages,
              )
        }
      }

      is WatchRecommendationProgress.LoadingUserProfile ->
          appString(Res.string.recommendation_progress_loading_user_profile, progress.username)

      is WatchRecommendationProgress.RegularUserNeedsCount ->
          appString(Res.string.recommendation_progress_regular_needs_count, progress.username)

      is WatchRecommendationProgress.RegularUserSequential ->
          appString(Res.string.recommendation_progress_regular_sequential, progress.username)

      is WatchRecommendationProgress.RandomPagesSelected ->
          appString(
              Res.string.recommendation_progress_random_pages,
              progress.pages.joinToString(),
          )

      is WatchRecommendationProgress.RandomUsersCollected ->
          appString(Res.string.recommendation_progress_random_users_collected, progress.count)

      is WatchRecommendationProgress.SamplePrepared ->
          appString(
              Res.string.recommendation_progress_sample_prepared,
              progress.sourceCount,
              progress.maxRounds,
          )

      is WatchRecommendationProgress.RoundStarted ->
          appString(
              Res.string.recommendation_progress_round_started,
              progress.round,
              progress.sampleSize,
              progress.minimumSharedFollowCount,
          )

      is WatchRecommendationProgress.RoundCompleted ->
          appString(
              Res.string.recommendation_progress_round_completed,
              progress.round,
              progress.candidateCount,
              progress.recommendationCount,
          )

      is WatchRecommendationProgress.Completed ->
          appString(Res.string.recommendation_progress_completed, progress.resultCount)
    }
