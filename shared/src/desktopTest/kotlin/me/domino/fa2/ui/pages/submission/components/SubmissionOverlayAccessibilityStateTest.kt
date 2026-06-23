package me.domino.fa2.ui.pages.submission.components

import fa2.shared.generated.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.domino.fa2.ui.pages.submission.attachmenttext.*
import me.domino.fa2.ui.pages.submission.content.*
import me.domino.fa2.ui.pages.submission.imageocr.*
import me.domino.fa2.ui.pages.submission.imageocr.SubmissionImageOcrTranslationMode
import me.domino.fa2.ui.pages.submission.imageocr.SubmissionImageOcrUiState
import me.domino.fa2.ui.pages.submission.pager.*
import me.domino.fa2.ui.pages.submission.series.*
import me.domino.fa2.ui.pages.submission.translation.*

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

  @Test
  fun image_tap_to_dismiss_is_disabled_while_ocr_is_active() {
    assertTrue(submissionImageTapToDismissEnabled(SubmissionImageOcrUiState.Idle))
    assertFalse(submissionImageTapToDismissEnabled(SubmissionImageOcrUiState.Loading))
    assertFalse(
        submissionImageTapToDismissEnabled(SubmissionImageOcrUiState.Showing(blocks = emptyList()))
    )
    assertFalse(submissionImageTapToDismissEnabled(SubmissionImageOcrUiState.Error("boom")))
  }
}
