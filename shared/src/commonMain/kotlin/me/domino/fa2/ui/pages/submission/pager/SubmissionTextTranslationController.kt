package me.domino.fa2.ui.pages.submission.pager

import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.attachment_translation_failed
import fa2.shared.generated.resources.description_translation_failed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import me.domino.fa2.domain.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.ui.components.html.SubmissionDescriptionTranslationStatus
import me.domino.fa2.ui.pages.submission.content.SubmissionDetailUiState
import me.domino.fa2.ui.state.SubmissionTranslationSourceMode
import me.domino.fa2.ui.state.SubmissionTranslationUiState
import me.domino.fa2.ui.state.canReuseTranslationResult
import me.domino.fa2.ui.state.markPendingBlocksAsFailed
import me.domino.fa2.ui.state.toPendingState
import me.domino.fa2.ui.state.variantOf
import me.domino.fa2.ui.state.withBlockResult
import me.domino.fa2.ui.state.withVariant

internal enum class SubmissionTranslationTarget {
  DESCRIPTION,
  ATTACHMENT,
}

private data class TranslationJobKey(val sid: Int, val target: SubmissionTranslationTarget)

/** Owns description/attachment translation state transitions and their coroutine lifecycle. */
internal class SubmissionTextTranslationController(
    private val scope: CoroutineScope,
    private val viewState: SubmissionViewStateStore,
    private val contextController: SubmissionPagerContextController,
    private val service: SubmissionDescriptionTranslationService,
    private val retryCoordinator: SubmissionTranslationRetryCoordinator,
    private val publishState: () -> Unit,
) {
  private val jobs = mutableMapOf<TranslationJobKey, Job>()

  fun translateCurrent(target: SubmissionTranslationTarget) {
    val current = contextController.current() ?: return
    val sid = current.id
    val detail = viewState.detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
    val state = detail.translationStateOf(target) ?: return
    if (state.showTranslation) {
      if (state.translating) return
      viewState.detailBySid[sid] =
          detail.withTranslationState(target, state.copy(showTranslation = false))
      publishState()
      return
    }
    if (state.sourceBlocks.isEmpty()) return

    val sourceMode = state.sourceMode
    val activeVariant = state.variantOf(sourceMode)
    if (activeVariant.translating) return
    if (activeVariant.canReuseTranslationResult()) {
      viewState.detailBySid[sid] =
          detail.withTranslationState(target, state.copy(showTranslation = true))
      publishState()
      return
    }

    val key = TranslationJobKey(sid, target)
    cancel(sid, target)
    val pendingVariant = activeVariant.toPendingState(sourceMode)
    val pendingState = state.withVariant(sourceMode, pendingVariant).copy(showTranslation = true)
    viewState.detailBySid[sid] = detail.withTranslationState(target, pendingState)
    publishState()

    jobs[key] =
        scope.launch {
          try {
            service.translateBlocks(pendingVariant.sourceBlocks) { index, result ->
              val latest =
                  viewState.detailBySid[sid] as? SubmissionDetailUiState.Success
                      ?: return@translateBlocks
              val latestState = latest.translationStateOf(target) ?: return@translateBlocks
              if (latestState.sourceKey != pendingState.sourceKey) return@translateBlocks
              val updated =
                  latestState.withVariant(
                      sourceMode,
                      latestState.variantOf(sourceMode).withBlockResult(index, result),
                  )
              viewState.detailBySid[sid] = latest.withTranslationState(target, updated)
              publishState()
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (_: Throwable) {
            markPendingAsFailed(sid, target, sourceMode, pendingState.sourceKey)
          } finally {
            val currentJob = currentCoroutineContext()[Job]
            if (jobs[key] === currentJob) jobs.remove(key)
            finishTranslation(sid, target, sourceMode, pendingState.sourceKey)
          }
        }
  }

  fun toggleWrapCurrent(target: SubmissionTranslationTarget) {
    val current = contextController.current() ?: return
    val detail = viewState.detailBySid[current.id] as? SubmissionDetailUiState.Success ?: return
    val state = detail.translationStateOf(target) ?: return
    if (state.showTranslation || state.translating) return
    val nextMode =
        when (state.sourceMode) {
          SubmissionTranslationSourceMode.RAW -> SubmissionTranslationSourceMode.WRAPPED
          SubmissionTranslationSourceMode.WRAPPED -> SubmissionTranslationSourceMode.RAW
        }
    viewState.detailBySid[current.id] =
        detail.withTranslationState(target, state.copy(sourceMode = nextMode))
    publishState()
  }

  fun cancel(sid: Int, target: SubmissionTranslationTarget) {
    jobs.remove(TranslationJobKey(sid, target))?.cancel()
  }

  private fun markPendingAsFailed(
      sid: Int,
      target: SubmissionTranslationTarget,
      sourceMode: SubmissionTranslationSourceMode,
      sourceKey: String,
  ) {
    val latest = viewState.detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
    val state = latest.translationStateOf(target) ?: return
    if (state.sourceKey != sourceKey) return
    viewState.detailBySid[sid] =
        latest.withTranslationState(
            target,
            state.withVariant(sourceMode, state.variantOf(sourceMode).markPendingBlocksAsFailed()),
        )
    publishState()
  }

  private suspend fun finishTranslation(
      sid: Int,
      target: SubmissionTranslationTarget,
      sourceMode: SubmissionTranslationSourceMode,
      sourceKey: String,
  ) {
    val latest = viewState.detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
    val state = latest.translationStateOf(target) ?: return
    if (state.sourceKey != sourceKey) return
    viewState.detailBySid[sid] =
        latest.withTranslationState(
            target,
            state.withVariant(sourceMode, state.variantOf(sourceMode).copy(translating = false)),
        )
    publishState()
    val finalState =
        (viewState.detailBySid[sid] as? SubmissionDetailUiState.Success)?.translationStateOf(target)
            ?: return
    val hasFailure =
        finalState.variantOf(sourceMode).blocks.any {
          it.status == SubmissionDescriptionTranslationStatus.FAILURE
        }
    if (hasFailure) {
      retryCoordinator.emitRetryFeedback(translationFailureMessage(target)) {
        retryIfPossible(sid, target, sourceKey)
      }
    }
  }

  private fun retryIfPossible(
      sid: Int,
      target: SubmissionTranslationTarget,
      sourceKey: String,
  ): Boolean {
    if (contextController.current()?.id != sid) return false
    val detail = viewState.detailBySid[sid] as? SubmissionDetailUiState.Success ?: return false
    val state = detail.translationStateOf(target) ?: return false
    if (state.sourceKey != sourceKey || state.translating || state.sourceBlocks.isEmpty()) {
      return false
    }
    if (state.showTranslation) {
      viewState.detailBySid[sid] =
          detail.withTranslationState(target, state.copy(showTranslation = false))
    }
    translateCurrent(target)
    return true
  }

  private suspend fun translationFailureMessage(target: SubmissionTranslationTarget): String =
      when (target) {
        SubmissionTranslationTarget.DESCRIPTION ->
            safeAppStringOrFallback(Res.string.description_translation_failed) {
              "Description translation failed"
            }
        SubmissionTranslationTarget.ATTACHMENT ->
            safeAppStringOrFallback(Res.string.attachment_translation_failed) {
              "Attachment translation failed"
            }
      }
}

private fun SubmissionDetailUiState.Success.translationStateOf(
    target: SubmissionTranslationTarget
): SubmissionTranslationUiState? =
    when (target) {
      SubmissionTranslationTarget.DESCRIPTION -> descriptionTranslationState
      SubmissionTranslationTarget.ATTACHMENT -> attachmentTranslationState
    }

private fun SubmissionDetailUiState.Success.withTranslationState(
    target: SubmissionTranslationTarget,
    state: SubmissionTranslationUiState,
): SubmissionDetailUiState.Success =
    when (target) {
      SubmissionTranslationTarget.DESCRIPTION -> copy(descriptionTranslationState = state)
      SubmissionTranslationTarget.ATTACHMENT -> copy(attachmentTranslationState = state)
    }
