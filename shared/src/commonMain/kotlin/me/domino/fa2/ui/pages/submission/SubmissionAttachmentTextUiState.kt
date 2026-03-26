package me.domino.fa2.ui.pages.submission

import me.domino.fa2.domain.attachmenttext.AttachmentTextDocument
import me.domino.fa2.domain.attachmenttext.AttachmentTextProgress

sealed interface SubmissionAttachmentTextUiState {
  data class Idle(val fileName: String) : SubmissionAttachmentTextUiState

  data class Loading(
      val fileName: String,
      val progress: AttachmentTextProgress?,
  ) : SubmissionAttachmentTextUiState

  data class Success(
      val fileName: String,
      val document: AttachmentTextDocument,
  ) : SubmissionAttachmentTextUiState

  data class Error(
      val fileName: String,
      val message: String,
  ) : SubmissionAttachmentTextUiState
}
