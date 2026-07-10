package me.domino.fa2.ui.pages.submission.pager

import co.touchlab.kermit.Logger
import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.cloudflare_challenge_title
import fa2.shared.generated.resources.favorite_submitted_but_refresh_failed
import fa2.shared.generated.resources.favorite_submitted_but_refresh_needs_cloudflare
import fa2.shared.generated.resources.missing_tag_block_credentials
import fa2.shared.generated.resources.refresh_details_failed
import fa2.shared.generated.resources.tag_block_failed
import fa2.shared.generated.resources.tag_blocked
import fa2.shared.generated.resources.tag_unblocked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.Submission
import me.domino.fa2.ui.components.feedback.AppFeedbackRequest
import me.domino.fa2.ui.i18n.appString
import me.domino.fa2.ui.pages.submission.content.SubmissionDetailUiState
import me.domino.fa2.ui.submissionviewer.api.SubmissionPagerDetailSource
import me.domino.fa2.utils.FaUrls

/** Owns optimistic favorite updates and tag block/unblock actions for the current submission. */
internal class SubmissionSocialActionController(
    private val scope: CoroutineScope,
    private val viewState: SubmissionViewStateStore,
    private val contextController: SubmissionPagerContextController,
    private val source: SubmissionPagerDetailSource,
    private val prefetchController: SubmissionDetailPrefetchController,
    private val log: Logger,
    private val publishState: () -> Unit,
    private val feedbackSink: (AppFeedbackRequest) -> Unit,
    private val rebuildDetailState:
        (
            sid: Int,
            detail: Submission,
            previous: SubmissionDetailUiState.Success,
            blockedKeywords: Set<String>,
        ) -> SubmissionDetailUiState.Success,
) {
  fun toggleFavoriteCurrent() {
    val current = contextController.current() ?: return
    val sid = current.id
    val state = viewState.detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
    if (state.favoriteUpdating) return
    val actionUrl = state.detail.favoriteActionUrl.trim()
    if (actionUrl.isBlank()) return
    val original = state.detail
    val optimistic =
        original.copy(
            isFavorited = !original.isFavorited,
            favoriteCount =
                (original.favoriteCount + if (original.isFavorited) -1 else 1).coerceAtLeast(0),
        )
    viewState.detailBySid[sid] =
        state.copy(detail = optimistic, favoriteUpdating = true, favoriteErrorMessage = null)
    publishState()

    scope.launch {
      when (val toggled = source.toggleFavorite(sid, actionUrl)) {
        is ResourceResult.Success ->
            refreshAfterFavorite(
                sid = sid,
                submissionUrl = current.submissionUrl,
                actionUrl = actionUrl,
                optimistic = optimistic,
                previous = state,
            )
        else -> {
          viewState.detailBySid[sid] =
              state.copy(
                  detail = original,
                  favoriteUpdating = false,
                  favoriteErrorMessage = resultMessage(toggled),
              )
          log.w { "收藏操作 -> 失败(sid=$sid,result=${toggled::class.simpleName})" }
        }
      }
      publishState()
    }
  }

  fun blockKeywordCurrent(tagName: String) {
    val tag = tagName.trim()
    if (tag.isBlank()) return
    val current = contextController.current() ?: return
    val sid = current.id
    val state = viewState.detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
    val toAdd = normalizeTagKey(tag) !in state.blockedKeywords
    val nonce = state.detail.tagBlockNonce.trim()
    if (nonce.isNotBlank()) {
      scope.launch { executeTagBlock(sid, tag, toAdd, nonce) }
      return
    }

    scope.launch {
      val targetUrl = current.submissionUrl.ifBlank { FaUrls.submission(sid) }
      when (val refreshed = source.loadByUrl(targetUrl)) {
        is ResourceResult.Success -> {
          prefetchController.markLoaded(sid)
          val refreshedState =
              rebuildDetailState(sid, refreshed.data, state, toBlockedKeywordSet(refreshed.data))
          viewState.detailBySid[sid] = refreshedState
          publishState()
          val refreshedNonce = refreshed.data.tagBlockNonce.trim()
          if (refreshedNonce.isBlank()) {
            val message = appString(Res.string.missing_tag_block_credentials)
            viewState.detailBySid[sid] = refreshedState.copy(favoriteErrorMessage = message)
            feedback(message)
            publishState()
          } else {
            executeTagBlock(sid, tag, toAdd, refreshedNonce)
          }
        }
        else -> {
          val reason = resultMessage(refreshed)
          val message = appString(Res.string.refresh_details_failed, reason)
          viewState.detailBySid[sid] = state.copy(favoriteErrorMessage = message)
          feedback(message)
          publishState()
        }
      }
    }
  }

  private suspend fun refreshAfterFavorite(
      sid: Int,
      submissionUrl: String,
      actionUrl: String,
      optimistic: Submission,
      previous: SubmissionDetailUiState.Success,
  ) {
    val targetUrl = submissionUrl.ifBlank { FaUrls.submission(sid) }
    when (val refreshed = source.loadByUrl(targetUrl)) {
      is ResourceResult.Success -> {
        prefetchController.markLoaded(sid)
        viewState.detailBySid[sid] =
            rebuildDetailState(
                sid,
                optimistic.copy(
                    isFavorited = refreshed.data.isFavorited,
                    favoriteCount = refreshed.data.favoriteCount,
                    favoriteActionUrl =
                        refreshed.data.favoriteActionUrl.ifBlank {
                          guessNextFavoriteActionUrl(actionUrl)
                        },
                    downloadUrl = refreshed.data.downloadUrl,
                    downloadFileName = refreshed.data.downloadFileName,
                ),
                previous,
                previous.blockedKeywords,
            )
      }
      else -> {
        val message =
            if (refreshed is ResourceResult.CfChallenge) {
              appString(Res.string.favorite_submitted_but_refresh_needs_cloudflare)
            } else {
              appString(Res.string.favorite_submitted_but_refresh_failed, resultMessage(refreshed))
            }
        viewState.detailBySid[sid] =
            previous.copy(
                detail = optimistic.copy(favoriteActionUrl = guessNextFavoriteActionUrl(actionUrl)),
                favoriteUpdating = false,
                favoriteErrorMessage = message,
            )
      }
    }
  }

  private suspend fun executeTagBlock(sid: Int, tagName: String, toAdd: Boolean, nonce: String) {
    when (val result = source.blockTag(sid, tagName, nonce, toAdd)) {
      is ResourceResult.Success -> {
        val latest = viewState.detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
        val blocked = latest.blockedKeywords.toMutableSet()
        if (toAdd) blocked += normalizeTagKey(tagName) else blocked -= normalizeTagKey(tagName)
        viewState.detailBySid[sid] =
            latest.copy(blockedKeywords = blocked, favoriteErrorMessage = null)
        feedback(
            if (toAdd) appString(Res.string.tag_blocked, tagName)
            else appString(Res.string.tag_unblocked, tagName)
        )
      }
      else -> {
        val latest = viewState.detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
        val reason = resultMessage(result)
        viewState.detailBySid[sid] = latest.copy(favoriteErrorMessage = reason)
        feedback(appString(Res.string.tag_block_failed, reason))
      }
    }
    publishState()
  }

  private suspend fun resultMessage(result: ResourceResult<*>): String =
      when (result) {
        is ResourceResult.Success -> ""
        is ResourceResult.AuthRequired -> result.message
        ResourceResult.CfChallenge -> appString(Res.string.cloudflare_challenge_title)
        is ResourceResult.MatureBlocked -> result.reason
        is ResourceResult.Error -> result.exception.message ?: result.exception.toString()
      }

  private fun feedback(message: String) {
    feedbackSink(AppFeedbackRequest(message))
  }
}

private fun normalizeTagKey(tagName: String): String = tagName.trim().lowercase()

private fun toBlockedKeywordSet(detail: Submission): Set<String> =
    detail.blockedTagNames.map(::normalizeTagKey).filter { it.isNotBlank() }.toSet()

private fun guessNextFavoriteActionUrl(currentActionUrl: String): String {
  val url = currentActionUrl.trim()
  return when {
    url.contains("/unfav/") -> url.replaceFirst("/unfav/", "/fav/")
    url.contains("/fav/") -> url.replaceFirst("/fav/", "/unfav/")
    else -> url
  }
}
