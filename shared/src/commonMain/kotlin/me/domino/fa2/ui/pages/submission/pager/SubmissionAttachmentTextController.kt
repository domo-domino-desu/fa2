package me.domino.fa2.ui.pages.submission.pager

import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.cloudflare_challenge_title
import fa2.shared.generated.resources.unsupported_attachment_text_format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.remote.translation.SubmissionTextTranslationEngine
import me.domino.fa2.domain.attachmenttext.AttachmentTextProgress
import me.domino.fa2.domain.attachmenttext.UnsupportedAttachmentTextFormatException
import me.domino.fa2.ui.components.html.TranslationUiState
import me.domino.fa2.ui.components.html.resolveTranslationState
import me.domino.fa2.ui.i18n.appString
import me.domino.fa2.ui.pages.submission.attachmenttext.SubmissionAttachmentTextUiState
import me.domino.fa2.ui.pages.submission.content.SubmissionDetailUiState

/** Owns attachment download/extraction progress and its derived translation source state. */
internal class SubmissionAttachmentTextController(
    private val scope: CoroutineScope,
    private val viewState: SubmissionViewStateStore,
    private val contextController: SubmissionPagerContext,
    private val source: SubmissionDetailSource,
    private val translationService: SubmissionTextTranslationEngine,
    private val textTranslationController: SubmissionTextTranslationController,
    private val publishState: () -> Unit,
) {
  fun loadCurrent() {
    val current = contextController.current() ?: return
    val sid = current.id
    val detail = viewState.detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
    val attachment = detail.attachmentTextState ?: return
    if (
        attachment is SubmissionAttachmentTextUiState.Loading ||
            attachment is SubmissionAttachmentTextUiState.Success
    ) {
      return
    }
    val downloadUrl = detail.detail.downloadUrl?.trim().orEmpty()
    val fileName = attachment.fileName().trim()
    if (downloadUrl.isBlank()) {
      textTranslationController.cancel(sid, SubmissionTranslationTarget.ATTACHMENT)
      viewState.detailBySid[sid] =
          detail.copy(
              attachmentTextState =
                  SubmissionAttachmentTextUiState.Error(
                      fileName = fileName,
                      message = "Missing attachment download URL",
                  ),
              attachmentTranslationState = null,
          )
      publishState()
      return
    }

    textTranslationController.cancel(sid, SubmissionTranslationTarget.ATTACHMENT)
    viewState.detailBySid[sid] =
        detail.copy(
            attachmentTextState =
                SubmissionAttachmentTextUiState.Loading(fileName, initialProgress()),
            attachmentTranslationState = null,
        )
    publishState()

    scope.launch {
      val result =
          source.loadAttachmentText(
              downloadUrl,
              fileName,
              progressUpdate@{ progress ->
                val latest =
                    viewState.detailBySid[sid] as? SubmissionDetailUiState.Success
                        ?: return@progressUpdate
                val loading =
                    latest.attachmentTextState as? SubmissionAttachmentTextUiState.Loading
                        ?: return@progressUpdate
                viewState.detailBySid[sid] =
                    latest.copy(attachmentTextState = loading.copy(progress = progress))
                publishState()
              },
          )

      val latest = viewState.detailBySid[sid] as? SubmissionDetailUiState.Success ?: return@launch
      viewState.detailBySid[sid] =
          when (result) {
            is ResourceResult.Success -> {
              val success = SubmissionAttachmentTextUiState.Success(fileName, result.data)
              latest.copy(
                  attachmentTextState = success,
                  attachmentTranslationState =
                      resolveAttachmentTranslationState(success, latest.attachmentTranslationState),
              )
            }
            is ResourceResult.AuthRequired -> latest.withAttachmentError(fileName, result.message)
            ResourceResult.CfChallenge ->
                latest.withAttachmentError(
                    fileName,
                    appString(Res.string.cloudflare_challenge_title),
                )
            is ResourceResult.MatureBlocked -> latest.withAttachmentError(fileName, result.reason)
            is ResourceResult.Error ->
                latest.withAttachmentError(fileName, failureMessage(result.exception))
          }
      publishState()
    }
  }

  private fun resolveAttachmentTranslationState(
      attachment: SubmissionAttachmentTextUiState.Success,
      previous: TranslationUiState?,
  ): TranslationUiState =
      resolveTranslationState(
          sourceHtml = attachment.document.html,
          sourceFileName = attachment.fileName,
          previous = previous,
          translationService = translationService,
      )

  private suspend fun failureMessage(error: Throwable): String =
      if (error is UnsupportedAttachmentTextFormatException) {
        appString(Res.string.unsupported_attachment_text_format)
      } else {
        error.message ?: error.toString()
      }
}

private fun SubmissionAttachmentTextUiState.fileName(): String =
    when (this) {
      is SubmissionAttachmentTextUiState.Idle -> fileName
      is SubmissionAttachmentTextUiState.Error -> fileName
      is SubmissionAttachmentTextUiState.Loading -> fileName
      is SubmissionAttachmentTextUiState.Success -> fileName
    }

private fun SubmissionDetailUiState.Success.withAttachmentError(
    fileName: String,
    message: String,
): SubmissionDetailUiState.Success =
    copy(
        attachmentTextState = SubmissionAttachmentTextUiState.Error(fileName, message),
        attachmentTranslationState = null,
    )

private fun initialProgress(): AttachmentTextProgress =
    AttachmentTextProgress(
        overallFraction = 0f,
        stageIndex = 1,
        stageCount = 1,
        stageId = "download_attachment",
        stageFraction = 0f,
    )
