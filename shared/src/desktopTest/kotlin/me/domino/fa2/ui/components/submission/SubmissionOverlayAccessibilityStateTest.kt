package me.domino.fa2.ui.components.submission

import fa2.shared.generated.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals
import me.domino.fa2.ui.pages.submission.SubmissionImageOcrTranslationMode
import me.domino.fa2.ui.pages.submission.SubmissionImageOcrUiState

class SubmissionOverlayAccessibilityStateTest {
  @Test
  fun ocr_state_description_resource_matches_ui_state() {
    assertEquals(
        Res.string.accessibility_ocr_state_idle,
        submissionImageOcrStateDescriptionRes(SubmissionImageOcrUiState.Idle),
    )
    assertEquals(
        Res.string.accessibility_ocr_state_loading,
        submissionImageOcrStateDescriptionRes(SubmissionImageOcrUiState.Loading),
    )
    assertEquals(
        Res.string.accessibility_ocr_state_applied,
        submissionImageOcrStateDescriptionRes(
            SubmissionImageOcrUiState.Showing(blocks = emptyList())
        ),
    )
    assertEquals(
        Res.string.accessibility_ocr_state_error,
        submissionImageOcrStateDescriptionRes(SubmissionImageOcrUiState.Error("boom")),
    )
  }

  @Test
  fun translation_state_description_resource_matches_mode() {
    assertEquals(
        Res.string.accessibility_translation_state_idle,
        submissionImageOcrTranslationStateDescriptionRes(SubmissionImageOcrTranslationMode.IDLE),
    )
    assertEquals(
        Res.string.accessibility_translation_state_loading,
        submissionImageOcrTranslationStateDescriptionRes(SubmissionImageOcrTranslationMode.LOADING),
    )
    assertEquals(
        Res.string.accessibility_translation_state_applied,
        submissionImageOcrTranslationStateDescriptionRes(SubmissionImageOcrTranslationMode.APPLIED),
    )
    assertEquals(
        Res.string.accessibility_translation_state_error,
        submissionImageOcrTranslationStateDescriptionRes(SubmissionImageOcrTranslationMode.ERROR),
    )
  }
}
