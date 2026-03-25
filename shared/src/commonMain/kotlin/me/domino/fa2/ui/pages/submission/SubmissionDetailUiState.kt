package me.domino.fa2.ui.pages.submission

import me.domino.fa2.data.model.Submission

/** 投稿详情单页状态。 */
sealed interface SubmissionDetailUiState {
  data object Loading : SubmissionDetailUiState

  data class Success(
      val detail: Submission,
      val blockedKeywords: Set<String> = emptySet(),
      val favoriteUpdating: Boolean = false,
      val favoriteErrorMessage: String? = null,
      val attachmentTextState: SubmissionAttachmentTextUiState? = null,
  ) : SubmissionDetailUiState

  data class Error(val message: String) : SubmissionDetailUiState
}
