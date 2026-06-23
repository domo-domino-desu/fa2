package me.domino.fa2.ui.pages.submission.content

import me.domino.fa2.data.model.Submission
import me.domino.fa2.ui.pages.submission.attachmenttext.*
import me.domino.fa2.ui.pages.submission.imageocr.*
import me.domino.fa2.ui.pages.submission.pager.*
import me.domino.fa2.ui.pages.submission.series.*
import me.domino.fa2.ui.pages.submission.translation.*

/** 投稿详情单页状态。 */
sealed interface SubmissionDetailUiState {
  data object Loading : SubmissionDetailUiState

  data class Success(
      val detail: Submission,
      val blockedKeywords: Set<String> = emptySet(),
      val descriptionTranslationState: SubmissionTranslationUiState,
      val favoriteUpdating: Boolean = false,
      val favoriteErrorMessage: String? = null,
      val attachmentTextState: SubmissionAttachmentTextUiState? = null,
      val attachmentTranslationState: SubmissionTranslationUiState? = null,
      val imageOcrTranslationExportSnapshot: SubmissionImageOcrTranslationExportSnapshot? = null,
  ) : SubmissionDetailUiState

  data class Error(val message: String) : SubmissionDetailUiState
}
