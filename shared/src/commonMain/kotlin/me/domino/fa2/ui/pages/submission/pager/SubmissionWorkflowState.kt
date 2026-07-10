package me.domino.fa2.ui.pages.submission.pager

import me.domino.fa2.ui.pages.submission.content.SubmissionDetailUiState
import me.domino.fa2.ui.pages.submission.imageocr.SubmissionImageOcrUiState

/** Owns mutable presentation state independently from workflow orchestration. */
internal class SubmissionViewStateStore {
  val detailBySid: MutableMap<Int, SubmissionDetailUiState> = mutableMapOf()
  val scrollOffsetBySid: MutableMap<Int, Int> = mutableMapOf()
  val scrollToTopVersionBySid: MutableMap<Int, Long> = mutableMapOf()
  val revealedBlockedMediaSids: MutableSet<Int> = mutableSetOf()

  var zoomOverlayImageUrl: String? = null
  var zoomImageOcrState: SubmissionImageOcrUiState = SubmissionImageOcrUiState.Idle
  var imageOcrMergedBlockCounter: Long = 0L
}
