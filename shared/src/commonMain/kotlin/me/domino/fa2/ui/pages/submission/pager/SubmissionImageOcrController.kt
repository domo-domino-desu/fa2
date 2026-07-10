package me.domino.fa2.ui.pages.submission.pager

import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.image_ocr_empty
import fa2.shared.generated.resources.image_ocr_failed
import fa2.shared.generated.resources.image_ocr_translation_empty
import fa2.shared.generated.resources.image_ocr_translation_failed
import fa2.shared.generated.resources.image_ocr_translation_failed_with_reason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import me.domino.fa2.domain.ocr.NormalizedImagePoint
import me.domino.fa2.domain.ocr.RecognizedTextBlock
import me.domino.fa2.domain.ocr.SubmissionImageOcrService
import me.domino.fa2.domain.ocr.collectRecognizedTextBlocksIntersectingRegion
import me.domino.fa2.domain.ocr.mergeRecognizedTextBlocksForOverlay
import me.domino.fa2.domain.translation.SubmissionImageOcrTranslationResult
import me.domino.fa2.domain.translation.SubmissionImageOcrTranslationService
import me.domino.fa2.ui.pages.submission.content.SubmissionDetailUiState
import me.domino.fa2.ui.pages.submission.imageocr.SubmissionImageOcrBlockUiState
import me.domino.fa2.ui.pages.submission.imageocr.SubmissionImageOcrDialogUiState
import me.domino.fa2.ui.pages.submission.imageocr.SubmissionImageOcrTranslationMode
import me.domino.fa2.ui.pages.submission.imageocr.SubmissionImageOcrTranslationStatus
import me.domino.fa2.ui.pages.submission.imageocr.SubmissionImageOcrUiState
import me.domino.fa2.ui.pages.submission.translation.SubmissionImageOcrTranslationExportSnapshot
import me.domino.fa2.ui.utils.isGifUrl
import me.domino.fa2.utils.logging.FaLog

/** Owns the complete image OCR session, including recognition, translation, editing and export. */
internal class SubmissionImageOcrController(
    private val scope: CoroutineScope,
    private val viewState: SubmissionViewStateStore,
    private val contextController: SubmissionPagerContextController,
    private val recognitionService: SubmissionImageOcrService,
    private val translationService: SubmissionImageOcrTranslationService,
    private val translationRetry: SubmissionTranslationRetryCoordinator,
    private val publishState: () -> Unit,
) {
  private val log = FaLog.withTag("SubmissionImageOcrController")
  private var recognitionJob: Job? = null
  private var translationJob: Job? = null
  private var dialogJob: Job? = null

  fun openImageZoom(imageUrl: String) {
    val normalizedUrl = normalizeZoomImageUrl(imageUrl) ?: return
    log.i { "打开原图缩放层 -> imageUrl=$normalizedUrl" }
    cancelJobs()
    viewState.imageOcrMergedBlockCounter = 0L
    viewState.zoomOverlayImageUrl = normalizedUrl
    viewState.zoomImageOcrState = SubmissionImageOcrUiState.Idle
    publishState()
  }

  fun dismissImageZoom() {
    if (
        viewState.zoomOverlayImageUrl == null &&
            viewState.zoomImageOcrState is SubmissionImageOcrUiState.Idle
    ) {
      return
    }
    log.i { "关闭原图缩放层 -> 清空 OCR 会话状态" }
    clear(publish = true)
  }

  fun clear(publish: Boolean) {
    cancelJobs()
    viewState.imageOcrMergedBlockCounter = 0L
    viewState.zoomOverlayImageUrl = null
    viewState.zoomImageOcrState = SubmissionImageOcrUiState.Idle
    if (publish) publishState()
  }

  fun toggleRecognition() {
    val imageUrl = viewState.zoomOverlayImageUrl ?: return
    if (isGifUrl(imageUrl)) return
    when (viewState.zoomImageOcrState) {
      is SubmissionImageOcrUiState.Loading -> return
      is SubmissionImageOcrUiState.Showing -> {
        log.i { "关闭 OCR 覆盖层 -> imageUrl=$imageUrl" }
        cancelJobs()
        viewState.zoomImageOcrState = SubmissionImageOcrUiState.Idle
        publishState()
        return
      }
      is SubmissionImageOcrUiState.Error,
      SubmissionImageOcrUiState.Idle -> Unit
    }

    cancelJobs()
    log.i { "开始识别原图 OCR -> imageUrl=$imageUrl" }
    viewState.zoomImageOcrState = SubmissionImageOcrUiState.Loading
    publishState()
    recognitionJob =
        scope.launch {
          try {
            val result = recognitionService.recognize(imageUrl)
            if (viewState.zoomOverlayImageUrl != imageUrl) return@launch
            if (result.blocks.isEmpty()) {
              log.w { "原图 OCR 完成但没有识别到文本 -> imageUrl=$imageUrl" }
              val message = imageOcrEmptyMessage()
              viewState.zoomImageOcrState = SubmissionImageOcrUiState.Error(message)
              translationRetry.emitToast(message)
            } else {
              log.i { "原图 OCR 完成 -> imageUrl=$imageUrl, blocks=${result.blocks.size}" }
              viewState.zoomImageOcrState =
                  SubmissionImageOcrUiState.Showing(buildRecognizedBlocks(result.blocks))
            }
            publishState()
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (error: Throwable) {
            if (viewState.zoomOverlayImageUrl != imageUrl) return@launch
            log.e(error) { "原图 OCR 失败 -> imageUrl=$imageUrl" }
            val message =
                imageOcrFailedMessage(error.message?.takeIf { it.isNotBlank() } ?: error.toString())
            viewState.zoomImageOcrState = SubmissionImageOcrUiState.Error(message)
            translationRetry.emitToast(message)
            publishState()
          } finally {
            val currentJob = currentCoroutineContext()[Job]
            if (recognitionJob === currentJob) recognitionJob = null
          }
        }
  }

  fun translateCurrent() {
    val showing = viewState.zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return
    if (showing.blocks.isEmpty()) return
    if (showing.translationMode == SubmissionImageOcrTranslationMode.LOADING) return

    log.i { "开始翻译 OCR 结果 -> blocks=${showing.blocks.size}" }
    cancelDialogJob()
    cancelTranslationJob()
    clearExportSnapshot(publish = false)
    viewState.zoomImageOcrState =
        showing.copy(
            translationMode = SubmissionImageOcrTranslationMode.LOADING,
            dialog = null,
            translationErrorMessage = null,
        )
    publishState()

    val targetImageUrl = viewState.zoomOverlayImageUrl
    val targetBlockIds = showing.blocks.map { it.id }
    translationJob =
        scope.launch {
          try {
            val translatedBlocks = translateBlocks(showing.blocks)
            val latest =
                viewState.zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return@launch
            if (viewState.zoomOverlayImageUrl != targetImageUrl) return@launch
            if (latest.blocks.map { it.id } != targetBlockIds) return@launch

            val hasSuccess =
                translatedBlocks.any {
                  it.translationStatus == SubmissionImageOcrTranslationStatus.SUCCESS
                }
            val nextMode =
                if (hasSuccess) SubmissionImageOcrTranslationMode.APPLIED
                else SubmissionImageOcrTranslationMode.ERROR
            val errorMessage =
                if (nextMode == SubmissionImageOcrTranslationMode.ERROR) {
                  imageOcrTranslationFailedMessage()
                } else {
                  null
                }
            viewState.zoomImageOcrState =
                latest.copy(
                    blocks = translatedBlocks,
                    translationMode = nextMode,
                    dialog = null,
                    translationErrorMessage = errorMessage,
                )
            log.i {
              "OCR 翻译完成 -> mode=${nextMode.name}, success=${translatedBlocks.count { it.translationStatus == SubmissionImageOcrTranslationStatus.SUCCESS }}, total=${translatedBlocks.size}"
            }
            if (errorMessage != null) {
              emitRetryFeedback(errorMessage, targetImageUrl)
            } else {
              updateExportSnapshot(targetImageUrl, translatedBlocks, publish = false)
            }
            publishState()
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (error: Throwable) {
            val latest =
                viewState.zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return@launch
            if (viewState.zoomOverlayImageUrl != targetImageUrl) return@launch
            log.e(error) { "OCR 翻译失败 -> imageUrl=$targetImageUrl" }
            val message =
                imageOcrTranslationFailedWithReasonMessage(
                    error.message?.takeIf { it.isNotBlank() } ?: error.toString()
                )
            viewState.zoomImageOcrState =
                latest.copy(
                    translationMode = SubmissionImageOcrTranslationMode.ERROR,
                    dialog = null,
                    translationErrorMessage = message,
                )
            emitRetryFeedback(message, targetImageUrl)
            publishState()
          } finally {
            val currentJob = currentCoroutineContext()[Job]
            if (translationJob === currentJob) translationJob = null
          }
        }
  }

  fun openDialog(blockId: String) {
    val showing = viewState.zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return
    if (showing.translationMode != SubmissionImageOcrTranslationMode.APPLIED) {
      log.w { "拒绝打开 OCR 弹窗 -> 当前未处于已翻译态, blockId=$blockId" }
      return
    }
    val block = showing.blocks.firstOrNull { it.id == blockId } ?: return
    if (!block.hasTranslation) {
      log.w { "拒绝打开 OCR 弹窗 -> block 没有可用译文, blockId=$blockId" }
      return
    }
    cancelDialogJob()
    viewState.zoomImageOcrState =
        showing.copy(
            dialog =
                SubmissionImageOcrDialogUiState(
                    blockId = block.id,
                    draftOriginalText = block.originalText,
                    translatedText = block.translatedText,
                )
        )
    publishState()
  }

  fun dismissDialog() {
    val showing = viewState.zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return
    if (showing.dialog == null) return
    cancelDialogJob()
    viewState.zoomImageOcrState = showing.copy(dialog = null)
    publishState()
  }

  fun updateDialogDraft(text: String) {
    val showing = viewState.zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return
    val dialog = showing.dialog ?: return
    viewState.zoomImageOcrState =
        showing.copy(dialog = dialog.copy(draftOriginalText = text, errorMessage = null))
    publishState()
  }

  fun refreshDialogTranslation() {
    val showing = viewState.zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return
    val dialog = showing.dialog ?: return
    if (dialog.refreshing) return
    val blockIndex = showing.blocks.indexOfFirst { it.id == dialog.blockId }
    if (blockIndex < 0) return
    val normalizedDraft = dialog.draftOriginalText.trim()
    if (
        normalizedDraft.isBlank() ||
            normalizedDraft == showing.blocks[blockIndex].originalText.trim()
    ) {
      return
    }

    cancelDialogJob()
    clearExportSnapshot(publish = false)
    viewState.zoomImageOcrState =
        showing.copy(dialog = dialog.copy(refreshing = true, errorMessage = null))
    publishState()
    val targetImageUrl = viewState.zoomOverlayImageUrl
    val targetBlockId = dialog.blockId
    dialogJob =
        scope.launch {
          try {
            when (val result = translationService.translateText(normalizedDraft)) {
              is SubmissionImageOcrTranslationResult.Success ->
                  finishDialogRefresh(
                      targetImageUrl,
                      targetBlockId,
                      normalizedDraft,
                      result.translatedText,
                  )
              SubmissionImageOcrTranslationResult.Empty ->
                  failDialogRefresh(
                      targetImageUrl,
                      targetBlockId,
                      imageOcrTranslationEmptyMessage(),
                  )
              is SubmissionImageOcrTranslationResult.Failure ->
                  failDialogRefresh(
                      targetImageUrl,
                      targetBlockId,
                      imageOcrTranslationFailedWithReasonMessage(result.message),
                  )
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (error: Throwable) {
            failDialogRefresh(
                targetImageUrl,
                targetBlockId,
                imageOcrTranslationFailedWithReasonMessage(
                    error.message?.takeIf { it.isNotBlank() } ?: error.toString()
                ),
            )
          } finally {
            val currentJob = currentCoroutineContext()[Job]
            if (dialogJob === currentJob) dialogJob = null
          }
        }
  }

  fun mergeBlocks(draggedBlockId: String, mergeRegionPoints: List<NormalizedImagePoint>) {
    val showing = viewState.zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return
    if (showing.translationMode == SubmissionImageOcrTranslationMode.LOADING) return
    log.i {
      "收到 OCR 框合并请求 -> draggedBlockId=$draggedBlockId, region=${mergeRegionPoints.toLogString()}"
    }
    val recognized =
        collectRecognizedTextBlocksIntersectingRegion(
            blocks = showing.blocks.map { it.toRecognizedTextBlock() },
            regionPoints = mergeRegionPoints,
        )
    if (recognized.size < 2) return
    val selectedKeys = recognized.map { it.toSelectionKey() }.toMutableList()
    val selected = mutableListOf<SubmissionImageOcrBlockUiState>()
    showing.blocks.forEach { block ->
      val match = selectedKeys.indexOf(block.toSelectionKey())
      if (match >= 0) {
        selected += block
        selectedKeys.removeAt(match)
      }
    }
    if (selected.size < 2 || selected.none { it.id == draggedBlockId }) return
    val mergedRecognized =
        mergeRecognizedTextBlocksForOverlay(selected.map { it.toRecognizedTextBlock() }) ?: return
    val merged =
        SubmissionImageOcrBlockUiState(
            id = nextMergedBlockId(),
            points = mergedRecognized.points,
            originalText = mergedRecognized.text,
        )
    val selectedIds = selected.map { it.id }.toSet()
    val mergedBlocks = sortBlocks(showing.blocks.filterNot { it.id in selectedIds } + merged)
    clearExportSnapshot(publish = false)
    viewState.zoomImageOcrState =
        showing.copy(
            blocks = mergedBlocks,
            dialog = showing.dialog?.takeUnless { it.blockId in selectedIds },
        )
    publishState()
    if (showing.translationMode == SubmissionImageOcrTranslationMode.APPLIED) {
      translateMergedBlock(merged.id)
    }
  }

  private fun finishDialogRefresh(
      imageUrl: String?,
      blockId: String,
      originalText: String,
      translatedText: String,
  ) {
    val latest = matchingDialogState(imageUrl, blockId) ?: return
    val blocks =
        latest.first.blocks.map { block ->
          if (block.id == blockId) {
            block.copy(
                originalText = originalText,
                translatedText = translatedText,
                translationStatus = SubmissionImageOcrTranslationStatus.SUCCESS,
            )
          } else {
            block
          }
        }
    viewState.zoomImageOcrState =
        latest.first.copy(
            blocks = blocks,
            dialog =
                latest.second.copy(
                    draftOriginalText = originalText,
                    translatedText = translatedText,
                    refreshing = false,
                    errorMessage = null,
                ),
        )
    updateExportSnapshot(imageUrl, blocks, publish = false)
    publishState()
  }

  private fun failDialogRefresh(imageUrl: String?, blockId: String, message: String) {
    val latest = matchingDialogState(imageUrl, blockId) ?: return
    viewState.zoomImageOcrState =
        latest.first.copy(dialog = latest.second.copy(refreshing = false, errorMessage = message))
    publishState()
  }

  private fun matchingDialogState(
      imageUrl: String?,
      blockId: String,
  ): Pair<SubmissionImageOcrUiState.Showing, SubmissionImageOcrDialogUiState>? {
    val showing = viewState.zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return null
    val dialog = showing.dialog ?: return null
    if (viewState.zoomOverlayImageUrl != imageUrl || dialog.blockId != blockId) return null
    return showing to dialog
  }

  private suspend fun translateBlocks(
      blocks: List<SubmissionImageOcrBlockUiState>
  ): List<SubmissionImageOcrBlockUiState> {
    val translations =
        try {
          translationService.translateTexts(blocks.map { it.originalText })
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Throwable) {
          List(blocks.size) {
            SubmissionImageOcrTranslationResult.Failure(
                error.message?.takeIf { it.isNotBlank() } ?: error.toString()
            )
          }
        }
    return blocks.mapIndexed { index, block ->
      when (
          val result = translations.getOrNull(index) ?: SubmissionImageOcrTranslationResult.Empty
      ) {
        is SubmissionImageOcrTranslationResult.Success ->
            block.copy(
                translatedText = result.translatedText,
                translationStatus = SubmissionImageOcrTranslationStatus.SUCCESS,
            )
        SubmissionImageOcrTranslationResult.Empty ->
            block.copy(
                translatedText = null,
                translationStatus = SubmissionImageOcrTranslationStatus.EMPTY,
            )
        is SubmissionImageOcrTranslationResult.Failure ->
            block.copy(
                translatedText = null,
                translationStatus = SubmissionImageOcrTranslationStatus.FAILURE,
            )
      }
    }
  }

  private fun translateMergedBlock(blockId: String) {
    val showing = viewState.zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return
    val block = showing.blocks.firstOrNull { it.id == blockId } ?: return
    cancelTranslationJob()
    val imageUrl = viewState.zoomOverlayImageUrl
    translationJob =
        scope.launch {
          try {
            when (val result = translationService.translateText(block.originalText)) {
              is SubmissionImageOcrTranslationResult.Success ->
                  updateMergedBlock(
                      blockId,
                      imageUrl,
                      result.translatedText,
                      SubmissionImageOcrTranslationStatus.SUCCESS,
                  )
              SubmissionImageOcrTranslationResult.Empty -> {
                updateMergedBlock(
                    blockId,
                    imageUrl,
                    null,
                    SubmissionImageOcrTranslationStatus.EMPTY,
                )
                translationRetry.emitToast(imageOcrTranslationEmptyMessage())
              }
              is SubmissionImageOcrTranslationResult.Failure -> {
                updateMergedBlock(
                    blockId,
                    imageUrl,
                    null,
                    SubmissionImageOcrTranslationStatus.FAILURE,
                )
                emitRetryFeedback(
                    imageOcrTranslationFailedWithReasonMessage(result.message),
                    imageUrl,
                )
              }
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (error: Throwable) {
            updateMergedBlock(blockId, imageUrl, null, SubmissionImageOcrTranslationStatus.FAILURE)
            emitRetryFeedback(
                imageOcrTranslationFailedWithReasonMessage(
                    error.message?.takeIf { it.isNotBlank() } ?: error.toString()
                ),
                imageUrl,
            )
          } finally {
            val currentJob = currentCoroutineContext()[Job]
            if (translationJob === currentJob) translationJob = null
          }
        }
  }

  private fun updateMergedBlock(
      blockId: String,
      imageUrl: String?,
      translatedText: String?,
      status: SubmissionImageOcrTranslationStatus,
  ) {
    val showing = viewState.zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return
    if (viewState.zoomOverlayImageUrl != imageUrl || showing.blocks.none { it.id == blockId })
        return
    val blocks =
        showing.blocks.map { block ->
          if (block.id == blockId)
              block.copy(translatedText = translatedText, translationStatus = status)
          else block
        }
    viewState.zoomImageOcrState = showing.copy(blocks = blocks)
    updateExportSnapshot(imageUrl, blocks, publish = false)
    publishState()
  }

  private suspend fun emitRetryFeedback(message: String, imageUrl: String?) {
    translationRetry.emitRetryFeedback(message) { retryIfPossible(imageUrl) }
  }

  private fun retryIfPossible(imageUrl: String?): Boolean {
    val showing = viewState.zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return false
    if (viewState.zoomOverlayImageUrl != imageUrl) return false
    if (showing.translationMode == SubmissionImageOcrTranslationMode.LOADING) return false
    if (showing.blocks.isEmpty()) return false
    translateCurrent()
    return true
  }

  private fun clearExportSnapshot(publish: Boolean) {
    val current = contextController.current() ?: return
    val detail = viewState.detailBySid[current.id] as? SubmissionDetailUiState.Success ?: return
    if (detail.imageOcrTranslationExportSnapshot == null) return
    viewState.detailBySid[current.id] = detail.copy(imageOcrTranslationExportSnapshot = null)
    if (publish) publishState()
  }

  private fun updateExportSnapshot(
      imageUrl: String?,
      blocks: List<SubmissionImageOcrBlockUiState>,
      publish: Boolean,
  ) {
    val current = contextController.current() ?: return
    val detail = viewState.detailBySid[current.id] as? SubmissionDetailUiState.Success ?: return
    val normalizedUrl = imageUrl?.trim().orEmpty()
    val snapshot =
        normalizedUrl
            .takeIf { it.isNotBlank() }
            ?.let { validUrl ->
              blocks
                  .takeIf { candidates ->
                    candidates.any {
                      it.translationStatus == SubmissionImageOcrTranslationStatus.SUCCESS &&
                          it.translatedText?.isNotBlank() == true
                    }
                  }
                  ?.let {
                    SubmissionImageOcrTranslationExportSnapshot(
                        imageUrl = validUrl,
                        provider = translationRetry.currentProvider(),
                        blocks = it,
                    )
                  }
            }
    if (detail.imageOcrTranslationExportSnapshot == snapshot) return
    viewState.detailBySid[current.id] = detail.copy(imageOcrTranslationExportSnapshot = snapshot)
    if (publish) publishState()
  }

  private fun cancelJobs() {
    recognitionJob?.cancel()
    recognitionJob = null
    cancelTranslationJob()
    cancelDialogJob()
  }

  private fun cancelTranslationJob() {
    translationJob?.cancel()
    translationJob = null
  }

  private fun cancelDialogJob() {
    dialogJob?.cancel()
    dialogJob = null
  }

  private fun nextMergedBlockId(): String {
    val id = "ocr-merged-${viewState.imageOcrMergedBlockCounter}"
    viewState.imageOcrMergedBlockCounter += 1L
    return id
  }

  private suspend fun imageOcrEmptyMessage(): String =
      safeAppStringOrFallback(Res.string.image_ocr_empty) { "No text detected in image" }

  private suspend fun imageOcrFailedMessage(reason: String): String =
      safeAppStringOrFallback(Res.string.image_ocr_failed, reason) { "Image OCR failed: $reason" }

  private suspend fun imageOcrTranslationFailedMessage(): String =
      safeAppStringOrFallback(Res.string.image_ocr_translation_failed) {
        "Image OCR translation failed"
      }

  private suspend fun imageOcrTranslationFailedWithReasonMessage(reason: String): String =
      safeAppStringOrFallback(Res.string.image_ocr_translation_failed_with_reason, reason) {
        "Image OCR translation failed: $reason"
      }

  private suspend fun imageOcrTranslationEmptyMessage(): String =
      safeAppStringOrFallback(Res.string.image_ocr_translation_empty) {
        "Translation result is empty"
      }
}

private fun normalizeZoomImageUrl(imageUrl: String): String? =
    imageUrl
        .trim()
        .takeIf { it.isNotBlank() }
        ?.let { url -> if (url.startsWith("//")) "https:$url" else url }

private data class ImageOcrSelectionKey(
    val originalText: String,
    val points: List<NormalizedImagePoint>,
)

private fun SubmissionImageOcrBlockUiState.toRecognizedTextBlock(): RecognizedTextBlock =
    RecognizedTextBlock(text = originalText, points = points, confidence = null)

private fun SubmissionImageOcrBlockUiState.toSelectionKey(): ImageOcrSelectionKey =
    ImageOcrSelectionKey(originalText, points)

private fun RecognizedTextBlock.toSelectionKey(): ImageOcrSelectionKey =
    ImageOcrSelectionKey(text, points)

private fun sortBlocks(
    blocks: List<SubmissionImageOcrBlockUiState>
): List<SubmissionImageOcrBlockUiState> =
    blocks.sortedWith(
        compareBy(
            { block -> block.points.minOfOrNull { it.y } ?: 0f },
            { block -> block.points.minOfOrNull { it.x } ?: 0f },
        )
    )

private fun buildRecognizedBlocks(
    blocks: List<RecognizedTextBlock>
): List<SubmissionImageOcrBlockUiState> =
    blocks.mapIndexed { index, block ->
      SubmissionImageOcrBlockUiState(
          id = "ocr-block-$index",
          points = block.points,
          originalText = block.text,
      )
    }

private fun List<NormalizedImagePoint>.toLogString(): String =
    joinToString(prefix = "[", postfix = "]") { "(${it.x},${it.y})" }
