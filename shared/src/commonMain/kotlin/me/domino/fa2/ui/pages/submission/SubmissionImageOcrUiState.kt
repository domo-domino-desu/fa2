package me.domino.fa2.ui.pages.submission

import me.domino.fa2.domain.ocr.NormalizedImagePoint

data class SubmissionImageOcrBlockUiState(
    val id: String,
    val points: List<NormalizedImagePoint>,
    val originalText: String,
    val translatedText: String? = null,
    val translationStatus: SubmissionImageOcrTranslationStatus =
        SubmissionImageOcrTranslationStatus.IDLE,
) {
  val displayText: String
    get() = translatedText?.takeIf { it.isNotBlank() } ?: originalText

  val hasTranslation: Boolean
    get() =
        translationStatus == SubmissionImageOcrTranslationStatus.SUCCESS &&
            translatedText?.isNotBlank() == true
}

data class SubmissionImageOcrDialogUiState(
    val blockId: String,
    val draftOriginalText: String,
    val translatedText: String?,
    val refreshing: Boolean = false,
    val errorMessage: String? = null,
)

enum class SubmissionImageOcrTranslationStatus {
  IDLE,
  SUCCESS,
  EMPTY,
  FAILURE,
}

enum class SubmissionImageOcrTranslationMode {
  IDLE,
  LOADING,
  APPLIED,
  ERROR,
}

sealed interface SubmissionImageOcrUiState {
  data object Idle : SubmissionImageOcrUiState

  data object Loading : SubmissionImageOcrUiState

  data class Showing(
      val blocks: List<SubmissionImageOcrBlockUiState>,
      val translationMode: SubmissionImageOcrTranslationMode =
          SubmissionImageOcrTranslationMode.IDLE,
      val dialog: SubmissionImageOcrDialogUiState? = null,
      val translationErrorMessage: String? = null,
  ) : SubmissionImageOcrUiState

  data class Error(
      val message: String,
  ) : SubmissionImageOcrUiState
}
