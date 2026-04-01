package me.domino.fa2.ui.pages.submission

import co.touchlab.kermit.Logger
import fa2.shared.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.domino.fa2.application.attachmenttext.AttachmentTextExtractor
import me.domino.fa2.application.ocr.SubmissionImageOcrService
import me.domino.fa2.application.ocr.collectRecognizedTextBlocksIntersectingRegion
import me.domino.fa2.application.ocr.mergeComicDialogueBlocks
import me.domino.fa2.application.ocr.mergeRecognizedTextBlocksForOverlay
import me.domino.fa2.application.submissionseries.seriesInitialReadyCount
import me.domino.fa2.application.submissionseries.seriesWarmBufferCount
import me.domino.fa2.application.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.application.translation.SubmissionImageOcrTranslationResult
import me.domino.fa2.application.translation.SubmissionImageOcrTranslationService
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.settings.TranslationProvider
import me.domino.fa2.domain.attachmenttext.AttachmentTextProgress
import me.domino.fa2.domain.attachmenttext.deriveAttachmentFileName
import me.domino.fa2.domain.ocr.NormalizedImagePoint
import me.domino.fa2.domain.ocr.RecognizedTextBlock
import me.domino.fa2.i18n.SystemLanguageProvider
import me.domino.fa2.i18n.appString
import me.domino.fa2.ui.components.AppFeedbackRequest
import me.domino.fa2.ui.state.SubmissionDescriptionTranslationStatus
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.isGifUrl
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizePageState
import org.jetbrains.compose.resources.StringResource

internal const val pagerPrefetchDebounceMs: Long = 500L

private enum class SubmissionTranslationTarget {
  DESCRIPTION,
  ATTACHMENT,
}

private data class SubmissionTranslationJobKey(
    val sid: Int,
    val target: SubmissionTranslationTarget,
)

internal class SubmissionScreenWorkflow(
    private val initialSid: Int,
    private val contextController: SubmissionPagerContextController,
    private val submissionSource: SubmissionPagerDetailSource,
    private val translationService: SubmissionDescriptionTranslationService,
    private val imageOcrService: SubmissionImageOcrService,
    private val imageOcrTranslationService: SubmissionImageOcrTranslationService,
    private val settingsService: AppSettingsService? = null,
    private val systemLanguageProvider: SystemLanguageProvider? = null,
    private val screenModelScope: CoroutineScope,
    private val log: Logger,
    private val stateSink: (SubmissionPagerUiState) -> Unit,
    private val pageStateSink: (PageState<SubmissionPagerUiState>) -> Unit,
    private val feedbackSink: (AppFeedbackRequest) -> Unit,
) {
  private val imageOcrLog = FaLog.withTag("SubmissionImageOcrWorkflow")
  private val detailBySid: MutableMap<Int, SubmissionDetailUiState> = mutableMapOf()
  private val scrollOffsetBySid: MutableMap<Int, Int> = mutableMapOf()
  private val scrollToTopVersionBySid: MutableMap<Int, Long> = mutableMapOf()
  private val translationJobs: MutableMap<SubmissionTranslationJobKey, Job> = mutableMapOf()
  private var zoomOverlayImageUrl: String? = null
  private var zoomImageOcrState: SubmissionImageOcrUiState = SubmissionImageOcrUiState.Idle
  private var imageOcrRecognitionJob: Job? = null
  private var imageOcrTranslationJob: Job? = null
  private var imageOcrDialogJob: Job? = null
  private var prefetchJob: Job? = null
  private var imageOcrMergedBlockCounter: Long = 0L
  private val prefetchedSuccessSids: MutableSet<Int> = mutableSetOf()
  private val prefetchingSids: MutableSet<Int> = mutableSetOf()

  fun initialize() {
    log.i { "初始化投稿详情页 -> 开始(initialSid=$initialSid)" }
    contextController.initializeSelection(initialSid)
    publishState()
    loadCurrentAndSchedulePrefetch()
    maintainPagerBuffer(forceBoundary = false)
  }

  fun previous() {
    if (!contextController.hasPreviousCached()) {
      if (
          contextController.sourceKind() == SubmissionContextSourceKind.SEQUENCE &&
              contextController.hasPreviousPages()
      ) {
        contextController.requestPrepend(force = true)
      }
      return
    }
    clearZoomOverlayState(publish = false)
    contextController.setCurrentIndex(contextController.currentIndex() - 1)
    publishState()
    loadCurrentAndSchedulePrefetch()
    maintainPagerBuffer(forceBoundary = false)
  }

  fun next() {
    if (contextController.hasNextCached()) {
      clearZoomOverlayState(publish = false)
      contextController.setCurrentIndex(contextController.currentIndex() + 1)
      publishState()
      loadCurrentAndSchedulePrefetch()
      maintainPagerBuffer(forceBoundary = false)
      return
    }
    maintainPagerBuffer(forceBoundary = true)
  }

  fun onPageChanged(index: Int) {
    clearZoomOverlayState(publish = false)
    contextController.setCurrentIndex(index)
    publishState()
    loadCurrentAndSchedulePrefetch()
    maintainPagerBuffer(forceBoundary = false)
  }

  fun setCurrentPageScrollOffset(sid: Int, offset: Int) {
    val normalized = offset.coerceAtLeast(0)
    if (scrollOffsetBySid[sid] == normalized) return
    scrollOffsetBySid[sid] = normalized
  }

  fun scrollOffsetForSid(sid: Int): Int = scrollOffsetBySid[sid] ?: 0

  fun scrollToTopVersionForSid(sid: Int): Long = scrollToTopVersionBySid[sid] ?: 0L

  fun requestCurrentPageScrollToTop() {
    val current = contextController.current() ?: return
    val sid = current.id
    scrollOffsetBySid[sid] = 0
    scrollToTopVersionBySid[sid] = (scrollToTopVersionBySid[sid] ?: 0L) + 1L
    publishState()
  }

  fun retryCurrentDetail() {
    val current = contextController.current() ?: return
    log.i { "重试详情加载 -> sid=${current.id}" }
    loadDetail(current = current, force = true)
  }

  fun openImageZoom(imageUrl: String) {
    val normalizedUrl = normalizeZoomImageUrl(imageUrl) ?: return
    imageOcrLog.i { "打开原图缩放层 -> imageUrl=$normalizedUrl" }
    cancelImageOcrJobs()
    imageOcrMergedBlockCounter = 0L
    zoomOverlayImageUrl = normalizedUrl
    zoomImageOcrState = SubmissionImageOcrUiState.Idle
    publishState()
  }

  fun dismissImageZoom() {
    if (zoomOverlayImageUrl == null && zoomImageOcrState is SubmissionImageOcrUiState.Idle) return
    imageOcrLog.i { "关闭原图缩放层 -> 清空 OCR 会话状态" }
    clearZoomOverlayState(publish = true)
  }

  fun toggleImageOcrCurrent() {
    val imageUrl = zoomOverlayImageUrl ?: return
    if (isGifUrl(imageUrl)) return
    when (zoomImageOcrState) {
      is SubmissionImageOcrUiState.Loading -> return
      is SubmissionImageOcrUiState.Showing -> {
        imageOcrLog.i { "关闭 OCR 覆盖层 -> imageUrl=$imageUrl" }
        cancelImageOcrJobs()
        zoomImageOcrState = SubmissionImageOcrUiState.Idle
        publishState()
        return
      }

      is SubmissionImageOcrUiState.Error,
      SubmissionImageOcrUiState.Idle -> Unit
    }

    cancelImageOcrJobs()
    imageOcrLog.i { "开始识别原图 OCR -> imageUrl=$imageUrl" }
    zoomImageOcrState = SubmissionImageOcrUiState.Loading
    publishState()

    val targetImageUrl = imageUrl
    imageOcrRecognitionJob =
        screenModelScope.launch {
          try {
            val result = imageOcrService.recognize(targetImageUrl).mergeComicDialogueBlocks()
            if (zoomOverlayImageUrl != targetImageUrl) return@launch
            if (result.blocks.isEmpty()) {
              imageOcrLog.w { "原图 OCR 完成但没有识别到文本 -> imageUrl=$targetImageUrl" }
              val emptyMessage = imageOcrEmptyMessage()
              zoomImageOcrState = SubmissionImageOcrUiState.Error(emptyMessage)
              emitToast(emptyMessage)
            } else {
              imageOcrLog.i {
                "原图 OCR 完成 -> imageUrl=$targetImageUrl, blocks=${result.blocks.size}"
              }
              zoomImageOcrState =
                  SubmissionImageOcrUiState.Showing(
                      blocks = buildRecognizedImageOcrBlocks(result.blocks)
                  )
            }
            publishState()
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (error: Throwable) {
            if (zoomOverlayImageUrl != targetImageUrl) return@launch
            imageOcrLog.e(error) { "原图 OCR 失败 -> imageUrl=$targetImageUrl" }
            val failureMessage =
                imageOcrFailedMessage(error.message?.takeIf { it.isNotBlank() } ?: error.toString())
            zoomImageOcrState = SubmissionImageOcrUiState.Error(failureMessage)
            emitToast(failureMessage)
            publishState()
          } finally {
            val currentJob = currentCoroutineContext()[Job]
            if (imageOcrRecognitionJob === currentJob) {
              imageOcrRecognitionJob = null
            }
          }
        }
  }

  fun translateImageOcrCurrent() {
    val showingState = zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return
    if (showingState.blocks.isEmpty()) return
    if (showingState.translationMode == SubmissionImageOcrTranslationMode.LOADING) return

    imageOcrLog.i { "开始翻译 OCR 结果 -> blocks=${showingState.blocks.size}" }
    cancelImageOcrDialogJob()
    cancelImageOcrTranslationJob()
    clearCurrentImageOcrTranslationExportSnapshot(publish = false)
    zoomImageOcrState =
        showingState.copy(
            translationMode = SubmissionImageOcrTranslationMode.LOADING,
            dialog = null,
            translationErrorMessage = null,
        )
    publishState()

    val targetImageUrl = zoomOverlayImageUrl
    val targetBlockIds = showingState.blocks.map { it.id }
    imageOcrTranslationJob =
        screenModelScope.launch {
          try {
            val translatedBlocks = translateImageOcrBlocks(showingState.blocks)
            val latestState =
                zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return@launch
            if (zoomOverlayImageUrl != targetImageUrl) return@launch
            if (latestState.blocks.map { it.id } != targetBlockIds) return@launch

            val hasSuccessfulTranslations =
                translatedBlocks.any { block ->
                  block.translationStatus == SubmissionImageOcrTranslationStatus.SUCCESS
                }
            val nextMode =
                if (hasSuccessfulTranslations) {
                  SubmissionImageOcrTranslationMode.APPLIED
                } else {
                  SubmissionImageOcrTranslationMode.ERROR
                }
            val errorMessage =
                if (nextMode == SubmissionImageOcrTranslationMode.ERROR) {
                  imageOcrTranslationFailedMessage()
                } else {
                  null
                }

            zoomImageOcrState =
                latestState.copy(
                    blocks = translatedBlocks,
                    translationMode = nextMode,
                    dialog = null,
                    translationErrorMessage = errorMessage,
                )
            imageOcrLog.i {
              "OCR 翻译完成 -> mode=${nextMode.name}, success=${translatedBlocks.count { it.translationStatus == SubmissionImageOcrTranslationStatus.SUCCESS }}, total=${translatedBlocks.size}"
            }
            if (errorMessage != null) {
              emitImageOcrRetryFeedback(
                  message = errorMessage,
                  targetImageUrl = targetImageUrl,
              )
            } else {
              updateCurrentImageOcrTranslationExportSnapshot(
                  imageUrl = targetImageUrl,
                  blocks = translatedBlocks,
                  publish = false,
              )
            }
            publishState()
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (error: Throwable) {
            val latestState =
                zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return@launch
            if (zoomOverlayImageUrl != targetImageUrl) return@launch
            imageOcrLog.e(error) { "OCR 翻译失败 -> imageUrl=$targetImageUrl" }
            val errorMessage =
                imageOcrTranslationFailedWithReasonMessage(
                    error.message?.takeIf { it.isNotBlank() } ?: error.toString()
                )
            zoomImageOcrState =
                latestState.copy(
                    translationMode = SubmissionImageOcrTranslationMode.ERROR,
                    dialog = null,
                    translationErrorMessage = errorMessage,
                )
            emitImageOcrRetryFeedback(message = errorMessage, targetImageUrl = targetImageUrl)
            publishState()
          } finally {
            val currentJob = currentCoroutineContext()[Job]
            if (imageOcrTranslationJob === currentJob) {
              imageOcrTranslationJob = null
            }
          }
        }
  }

  fun openImageOcrDialog(blockId: String) {
    val showingState = zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return
    if (showingState.translationMode != SubmissionImageOcrTranslationMode.APPLIED) {
      imageOcrLog.w { "拒绝打开 OCR 弹窗 -> 当前未处于已翻译态, blockId=$blockId" }
      return
    }
    val block = showingState.blocks.firstOrNull { it.id == blockId } ?: return
    if (!block.hasTranslation) {
      imageOcrLog.w { "拒绝打开 OCR 弹窗 -> block 没有可用译文, blockId=$blockId" }
      return
    }
    imageOcrLog.i { "打开 OCR 弹窗 -> blockId=$blockId" }
    cancelImageOcrDialogJob()
    zoomImageOcrState =
        showingState.copy(
            dialog =
                SubmissionImageOcrDialogUiState(
                    blockId = block.id,
                    draftOriginalText = block.originalText,
                    translatedText = block.translatedText,
                )
        )
    publishState()
  }

  fun dismissImageOcrDialog() {
    val showingState = zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return
    if (showingState.dialog == null) return
    cancelImageOcrDialogJob()
    zoomImageOcrState = showingState.copy(dialog = null)
    publishState()
  }

  fun updateImageOcrDialogDraft(text: String) {
    val showingState = zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return
    val dialog = showingState.dialog ?: return
    zoomImageOcrState =
        showingState.copy(dialog = dialog.copy(draftOriginalText = text, errorMessage = null))
    publishState()
  }

  fun refreshImageOcrDialogTranslation() {
    val showingState = zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return
    val dialog = showingState.dialog ?: return
    if (dialog.refreshing) return
    val blockIndex = showingState.blocks.indexOfFirst { it.id == dialog.blockId }
    if (blockIndex < 0) return

    val currentBlock = showingState.blocks[blockIndex]
    val normalizedDraft = dialog.draftOriginalText.trim()
    if (normalizedDraft.isBlank()) return
    if (normalizedDraft == currentBlock.originalText.trim()) return

    cancelImageOcrDialogJob()
    clearCurrentImageOcrTranslationExportSnapshot(publish = false)
    zoomImageOcrState =
        showingState.copy(dialog = dialog.copy(refreshing = true, errorMessage = null))
    publishState()

    val targetImageUrl = zoomOverlayImageUrl
    val targetBlockId = dialog.blockId
    imageOcrDialogJob =
        screenModelScope.launch {
          try {
            when (val result = imageOcrTranslationService.translateText(normalizedDraft)) {
              is SubmissionImageOcrTranslationResult.Success -> {
                val latestState =
                    zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return@launch
                val latestDialog = latestState.dialog ?: return@launch
                if (
                    zoomOverlayImageUrl != targetImageUrl || latestDialog.blockId != targetBlockId
                ) {
                  return@launch
                }
                val refreshedBlocks =
                    latestState.blocks.map { block ->
                      if (block.id != targetBlockId) {
                        block
                      } else {
                        block.copy(
                            originalText = normalizedDraft,
                            translatedText = result.translatedText,
                            translationStatus = SubmissionImageOcrTranslationStatus.SUCCESS,
                        )
                      }
                    }
                zoomImageOcrState =
                    latestState.copy(
                        blocks = refreshedBlocks,
                        dialog =
                            latestDialog.copy(
                                draftOriginalText = normalizedDraft,
                                translatedText = result.translatedText,
                                refreshing = false,
                                errorMessage = null,
                            ),
                    )
                updateCurrentImageOcrTranslationExportSnapshot(
                    imageUrl = targetImageUrl,
                    blocks = refreshedBlocks,
                    publish = false,
                )
                publishState()
              }

              SubmissionImageOcrTranslationResult.Empty -> {
                val latestState =
                    zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return@launch
                val latestDialog = latestState.dialog ?: return@launch
                if (
                    zoomOverlayImageUrl != targetImageUrl || latestDialog.blockId != targetBlockId
                ) {
                  return@launch
                }
                val errorMessage = imageOcrTranslationEmptyMessage()
                zoomImageOcrState =
                    latestState.copy(
                        dialog =
                            latestDialog.copy(
                                refreshing = false,
                                errorMessage = errorMessage,
                            )
                    )
                publishState()
              }

              is SubmissionImageOcrTranslationResult.Failure -> {
                val latestState =
                    zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return@launch
                val latestDialog = latestState.dialog ?: return@launch
                if (
                    zoomOverlayImageUrl != targetImageUrl || latestDialog.blockId != targetBlockId
                ) {
                  return@launch
                }
                val errorMessage = imageOcrTranslationFailedWithReasonMessage(result.message)
                zoomImageOcrState =
                    latestState.copy(
                        dialog =
                            latestDialog.copy(
                                refreshing = false,
                                errorMessage = errorMessage,
                            )
                    )
                publishState()
              }
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (error: Throwable) {
            val latestState =
                zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return@launch
            val latestDialog = latestState.dialog ?: return@launch
            if (zoomOverlayImageUrl != targetImageUrl || latestDialog.blockId != targetBlockId) {
              return@launch
            }
            val errorMessage =
                imageOcrTranslationFailedWithReasonMessage(
                    error.message?.takeIf { it.isNotBlank() } ?: error.toString()
                )
            zoomImageOcrState =
                latestState.copy(
                    dialog =
                        latestDialog.copy(
                            refreshing = false,
                            errorMessage = errorMessage,
                        )
                )
            publishState()
          } finally {
            val currentJob = currentCoroutineContext()[Job]
            if (imageOcrDialogJob === currentJob) {
              imageOcrDialogJob = null
            }
          }
        }
  }

  fun mergeImageOcrBlocks(
      draggedBlockId: String,
      mergeRegionPoints: List<NormalizedImagePoint>,
  ) {
    val showingState = zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return
    if (showingState.translationMode == SubmissionImageOcrTranslationMode.LOADING) return
    imageOcrLog.i {
      "收到 OCR 框合并请求 -> draggedBlockId=$draggedBlockId, region=${mergeRegionPoints.toLogString()}"
    }

    val regionRecognizedBlocks =
        collectRecognizedTextBlocksIntersectingRegion(
            blocks = showingState.blocks.map { block -> block.toRecognizedTextBlock() },
            regionPoints = mergeRegionPoints,
        )
    if (regionRecognizedBlocks.size < 2) {
      imageOcrLog.w { "忽略 OCR 框合并 -> 命中块不足, draggedBlockId=$draggedBlockId" }
      return
    }

    val selectedKeys =
        regionRecognizedBlocks.map { block -> block.toSelectionKey() }.toMutableList()
    val selectedBlocks = mutableListOf<SubmissionImageOcrBlockUiState>()
    showingState.blocks.forEach { block ->
      val key = block.toSelectionKey()
      val matchIndex = selectedKeys.indexOf(key)
      if (matchIndex >= 0) {
        selectedBlocks += block
        selectedKeys.removeAt(matchIndex)
      }
    }
    if (selectedBlocks.size < 2) {
      imageOcrLog.w { "忽略 OCR 框合并 -> 会话块映射后不足两个" }
      return
    }
    if (selectedBlocks.none { it.id == draggedBlockId }) {
      imageOcrLog.w { "忽略 OCR 框合并 -> 选区不包含拖拽源, draggedBlockId=$draggedBlockId" }
      return
    }

    val mergedRecognizedBlock =
        mergeRecognizedTextBlocksForOverlay(
            selectedBlocks.map { block -> block.toRecognizedTextBlock() }
        ) ?: return
    val mergedBlock =
        SubmissionImageOcrBlockUiState(
            id = nextMergedImageOcrBlockId(),
            points = mergedRecognizedBlock.points,
            originalText = mergedRecognizedBlock.text,
            translatedText = null,
            translationStatus = SubmissionImageOcrTranslationStatus.IDLE,
        )
    val selectedIds = selectedBlocks.map { it.id }.toSet()
    val mergedBlocks =
        sortImageOcrBlocks(
            showingState.blocks.filterNot { block -> block.id in selectedIds } + mergedBlock
        )
    val dialog =
        showingState.dialog?.takeUnless { currentDialog -> currentDialog.blockId in selectedIds }
    imageOcrLog.i {
      "OCR 框合并完成 -> selected=${selectedIds.joinToString()}, mergedBlockId=${mergedBlock.id}, translationMode=${showingState.translationMode.name}"
    }
    clearCurrentImageOcrTranslationExportSnapshot(publish = false)
    zoomImageOcrState = showingState.copy(blocks = mergedBlocks, dialog = dialog)
    publishState()

    if (showingState.translationMode == SubmissionImageOcrTranslationMode.APPLIED) {
      translateMergedImageOcrBlock(blockId = mergedBlock.id)
    }
  }

  fun translateDescriptionCurrent() {
    translateCurrent(target = SubmissionTranslationTarget.DESCRIPTION)
  }

  fun toggleDescriptionWrapCurrent() {
    toggleWrapCurrent(target = SubmissionTranslationTarget.DESCRIPTION)
  }

  fun translateAttachmentCurrent() {
    translateCurrent(target = SubmissionTranslationTarget.ATTACHMENT)
  }

  fun toggleAttachmentWrapCurrent() {
    toggleWrapCurrent(target = SubmissionTranslationTarget.ATTACHMENT)
  }

  fun loadAttachmentTextCurrent() {
    val current = contextController.current() ?: return
    val sid = current.id
    val currentState = detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
    val attachmentState = currentState.attachmentTextState ?: return
    if (
        attachmentState is SubmissionAttachmentTextUiState.Loading ||
            attachmentState is SubmissionAttachmentTextUiState.Success
    ) {
      return
    }

    val downloadUrl = currentState.detail.downloadUrl?.trim().orEmpty()
    val fileName =
        when (attachmentState) {
          is SubmissionAttachmentTextUiState.Idle -> attachmentState.fileName
          is SubmissionAttachmentTextUiState.Error -> attachmentState.fileName
          is SubmissionAttachmentTextUiState.Loading,
          is SubmissionAttachmentTextUiState.Success -> return
        }.trim()
    if (downloadUrl.isBlank()) {
      cancelTranslationJob(sid, SubmissionTranslationTarget.ATTACHMENT)
      detailBySid[sid] =
          currentState.copy(
              attachmentTextState =
                  SubmissionAttachmentTextUiState.Error(
                      fileName = fileName,
                      message = appString(Res.string.missing_attachment_download_url),
                  ),
              attachmentTranslationState = null,
          )
      publishState()
      return
    }

    cancelTranslationJob(sid, SubmissionTranslationTarget.ATTACHMENT)
    detailBySid[sid] =
        currentState.copy(
            attachmentTextState =
                SubmissionAttachmentTextUiState.Loading(
                    fileName = fileName,
                    progress =
                        initialAttachmentTextProgress(
                            fileName = fileName,
                            _settingsService = settingsService,
                            _systemLanguageProvider = systemLanguageProvider,
                        ),
                ),
            attachmentTranslationState = null,
        )
    publishState()

    screenModelScope.launch {
      val result =
          submissionSource.loadAttachmentText(
              downloadUrl = downloadUrl,
              downloadFileName = fileName,
              onProgress = progressUpdate@{ progress ->
                    val latestState =
                        detailBySid[sid] as? SubmissionDetailUiState.Success
                            ?: return@progressUpdate
                    val latestAttachmentState =
                        latestState.attachmentTextState as? SubmissionAttachmentTextUiState.Loading
                            ?: return@progressUpdate
                    detailBySid[sid] =
                        latestState.copy(
                            attachmentTextState = latestAttachmentState.copy(progress = progress)
                        )
                    publishState()
                  },
          )

      val latestState = detailBySid[sid] as? SubmissionDetailUiState.Success ?: return@launch
      detailBySid[sid] =
          when (result) {
            is PageState.Success -> {
              val nextAttachmentState =
                  SubmissionAttachmentTextUiState.Success(
                      fileName = fileName,
                      document = result.data,
                  )
              latestState.copy(
                  attachmentTextState = nextAttachmentState,
                  attachmentTranslationState =
                      resolveAttachmentTranslationState(
                          attachmentTextState = nextAttachmentState,
                          previous = latestState.attachmentTranslationState,
                          translationService = translationService,
                      ),
              )
            }

            is PageState.AuthRequired ->
                latestState.copy(
                    attachmentTextState =
                        SubmissionAttachmentTextUiState.Error(
                            fileName = fileName,
                            message = result.message,
                        ),
                    attachmentTranslationState = null,
                )

            PageState.CfChallenge ->
                latestState.copy(
                    attachmentTextState =
                        SubmissionAttachmentTextUiState.Error(
                            fileName = fileName,
                            message = appString(Res.string.cloudflare_challenge_title),
                        ),
                    attachmentTranslationState = null,
                )

            is PageState.MatureBlocked ->
                latestState.copy(
                    attachmentTextState =
                        SubmissionAttachmentTextUiState.Error(
                            fileName = fileName,
                            message = result.reason,
                        ),
                    attachmentTranslationState = null,
                )

            is PageState.Error ->
                latestState.copy(
                    attachmentTextState =
                        SubmissionAttachmentTextUiState.Error(
                            fileName = fileName,
                            message = result.exception.message ?: result.exception.toString(),
                        ),
                    attachmentTranslationState = null,
                )

            PageState.Loading -> latestState
          }
      publishState()
    }
  }

  fun toggleFavoriteCurrent() {
    val current = contextController.current() ?: return
    val sid = current.id
    val currentState = detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
    if (currentState.favoriteUpdating) return
    val actionUrl = currentState.detail.favoriteActionUrl.trim()
    if (actionUrl.isBlank()) {
      log.w { "收藏操作 -> 跳过(缺少actionUrl,sid=$sid)" }
      return
    }
    log.i { "收藏操作 -> 开始(sid=$sid,toFavorited=${!currentState.detail.isFavorited})" }

    val originalDetail = currentState.detail
    val optimisticDetail =
        originalDetail.copy(
            isFavorited = !originalDetail.isFavorited,
            favoriteCount =
                (originalDetail.favoriteCount + if (originalDetail.isFavorited) -1 else 1)
                    .coerceAtLeast(0),
        )
    detailBySid[sid] =
        currentState.copy(
            detail = optimisticDetail,
            favoriteUpdating = true,
            favoriteErrorMessage = null,
        )
    publishState()

    screenModelScope.launch {
      when (val toggleResult = submissionSource.toggleFavorite(sid = sid, actionUrl = actionUrl)) {
        is PageState.Success -> {
          val targetUrl = current.submissionUrl.ifBlank { FaUrls.submission(current.id) }
          when (val refreshed = submissionSource.loadByUrl(targetUrl)) {
            is PageState.Success -> {
              prefetchedSuccessSids += sid
              detailBySid[sid] =
                  buildSuccessDetailState(
                      sid = sid,
                      detail =
                          optimisticDetail.copy(
                              isFavorited = refreshed.data.isFavorited,
                              favoriteCount = refreshed.data.favoriteCount,
                              favoriteActionUrl =
                                  refreshed.data.favoriteActionUrl.ifBlank {
                                    guessNextFavoriteActionUrl(actionUrl)
                                  },
                              downloadUrl = refreshed.data.downloadUrl,
                              downloadFileName = refreshed.data.downloadFileName,
                          ),
                      previous = currentState,
                      blockedKeywords = currentState.blockedKeywords,
                  )
              log.i { "收藏操作 -> 成功(sid=$sid,isFavorited=${refreshed.data.isFavorited})" }
            }

            is PageState.AuthRequired -> {
              detailBySid[sid] =
                  currentState.copy(
                      detail =
                          optimisticDetail.copy(
                              favoriteActionUrl = guessNextFavoriteActionUrl(actionUrl)
                          ),
                      favoriteUpdating = false,
                      favoriteErrorMessage =
                          appString(
                              Res.string.favorite_submitted_but_refresh_failed,
                              refreshed.message,
                          ),
                  )
              log.w { "收藏操作 -> 已提交, 但刷新需要重新登录(sid=$sid)" }
            }

            PageState.CfChallenge -> {
              detailBySid[sid] =
                  currentState.copy(
                      detail =
                          optimisticDetail.copy(
                              favoriteActionUrl = guessNextFavoriteActionUrl(actionUrl)
                          ),
                      favoriteUpdating = false,
                      favoriteErrorMessage =
                          appString(Res.string.favorite_submitted_but_refresh_needs_cloudflare),
                  )
              log.w { "收藏操作 -> 已提交, 但刷新需要Cloudflare验证(sid=$sid)" }
            }

            is PageState.MatureBlocked -> {
              detailBySid[sid] =
                  currentState.copy(
                      detail =
                          optimisticDetail.copy(
                              favoriteActionUrl = guessNextFavoriteActionUrl(actionUrl)
                          ),
                      favoriteUpdating = false,
                      favoriteErrorMessage =
                          appString(
                              Res.string.favorite_submitted_but_refresh_failed,
                              refreshed.reason,
                          ),
                  )
              log.w { "收藏操作 -> 已提交, 但刷新受限(sid=$sid,reason=${refreshed.reason})" }
            }

            is PageState.Error -> {
              detailBySid[sid] =
                  currentState.copy(
                      detail =
                          optimisticDetail.copy(
                              favoriteActionUrl = guessNextFavoriteActionUrl(actionUrl)
                          ),
                      favoriteUpdating = false,
                      favoriteErrorMessage =
                          appString(
                              Res.string.favorite_submitted_but_refresh_failed,
                              refreshed.exception.message ?: refreshed.exception.toString(),
                          ),
                  )
              log.e(refreshed.exception) { "收藏操作 -> 已提交, 但刷新失败(sid=$sid)" }
            }

            PageState.Loading -> Unit
          }
        }

        is PageState.AuthRequired -> {
          detailBySid[sid] =
              currentState.copy(
                  detail = originalDetail,
                  favoriteUpdating = false,
                  favoriteErrorMessage = toggleResult.message,
              )
          log.w { "收藏操作 -> 需要重新登录(sid=$sid)" }
        }

        PageState.CfChallenge -> {
          detailBySid[sid] =
              currentState.copy(
                  detail = originalDetail,
                  favoriteUpdating = false,
                  favoriteErrorMessage = appString(Res.string.cloudflare_challenge_title),
              )
          log.w { "收藏操作 -> Cloudflare验证(sid=$sid)" }
        }

        is PageState.MatureBlocked -> {
          detailBySid[sid] =
              currentState.copy(
                  detail = originalDetail,
                  favoriteUpdating = false,
                  favoriteErrorMessage = toggleResult.reason,
              )
          log.w { "收藏操作 -> 受限(sid=$sid,reason=${toggleResult.reason})" }
        }

        is PageState.Error -> {
          detailBySid[sid] =
              currentState.copy(
                  detail = originalDetail,
                  favoriteUpdating = false,
                  favoriteErrorMessage =
                      toggleResult.exception.message ?: toggleResult.exception.toString(),
              )
          log.e(toggleResult.exception) { "收藏操作 -> 失败(sid=$sid)" }
        }

        PageState.Loading -> Unit
      }
      publishState()
    }
  }

  fun blockKeywordCurrent(tagName: String) {
    val normalizedTagName = tagName.trim()
    if (normalizedTagName.isBlank()) {
      log.w { "标签屏蔽 -> 跳过(空标签)" }
      return
    }
    val current = contextController.current() ?: return
    val sid = current.id
    val currentState = detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
    val nonce = currentState.detail.tagBlockNonce.trim()
    val normalizedTagKey = normalizeTagKey(normalizedTagName)
    val currentlyBlocked = normalizedTagKey in currentState.blockedKeywords
    val toAdd = !currentlyBlocked
    log.i { "标签屏蔽 -> 开始(sid=$sid,tag=$normalizedTagName,toAdd=$toAdd)" }
    if (nonce.isBlank()) {
      screenModelScope.launch {
        val targetUrl = current.submissionUrl.ifBlank { FaUrls.submission(sid) }
        when (val refreshed = submissionSource.loadByUrl(targetUrl)) {
          is PageState.Success -> {
            prefetchedSuccessSids += sid
            val refreshedState =
                buildSuccessDetailState(
                    sid = sid,
                    detail = refreshed.data,
                    previous = currentState,
                    blockedKeywords = toBlockedKeywordSet(refreshed.data),
                )
            detailBySid[sid] = refreshedState
            publishState()

            val refreshedNonce = refreshed.data.tagBlockNonce.trim()
            if (refreshedNonce.isBlank()) {
              detailBySid[sid] =
                  refreshedState.copy(
                      favoriteErrorMessage = appString(Res.string.missing_tag_block_credentials)
                  )
              publishState()
              emitToast(appString(Res.string.missing_tag_block_credentials))
              log.w { "标签屏蔽 -> 缺少凭据(sid=$sid)" }
              return@launch
            }

            executeTagBlockRequest(
                sid = sid,
                tagName = normalizedTagName,
                toAdd = toAdd,
                nonce = refreshedNonce,
            )
          }

          is PageState.AuthRequired -> {
            detailBySid[sid] =
                currentState.copy(
                    favoriteErrorMessage =
                        appString(Res.string.refresh_details_failed, refreshed.message)
                )
            publishState()
            emitToast(appString(Res.string.refresh_details_failed, refreshed.message))
            log.w { "标签屏蔽 -> 刷新详情需要重新登录(sid=$sid)" }
          }

          PageState.CfChallenge -> {
            detailBySid[sid] =
                currentState.copy(
                    favoriteErrorMessage =
                        appString(
                            Res.string.refresh_details_failed,
                            appString(Res.string.cloudflare_challenge_title),
                        )
                )
            publishState()
            emitToast(
                appString(
                    Res.string.refresh_details_failed,
                    appString(Res.string.cloudflare_challenge_title),
                )
            )
            log.w { "标签屏蔽 -> 刷新详情需Cloudflare验证(sid=$sid)" }
          }

          is PageState.MatureBlocked -> {
            detailBySid[sid] =
                currentState.copy(
                    favoriteErrorMessage =
                        appString(Res.string.refresh_details_failed, refreshed.reason)
                )
            publishState()
            emitToast(appString(Res.string.refresh_details_failed, refreshed.reason))
            log.w { "标签屏蔽 -> 刷新详情受限(sid=$sid,reason=${refreshed.reason})" }
          }

          is PageState.Error -> {
            val message = refreshed.exception.message ?: refreshed.exception.toString()
            detailBySid[sid] =
                currentState.copy(
                    favoriteErrorMessage = appString(Res.string.refresh_details_failed, message)
                )
            publishState()
            emitToast(appString(Res.string.refresh_details_failed, message))
            log.e(refreshed.exception) { "标签屏蔽 -> 刷新详情失败(sid=$sid)" }
          }

          PageState.Loading -> Unit
        }
      }
      return
    }

    screenModelScope.launch {
      executeTagBlockRequest(
          sid = sid,
          tagName = normalizedTagName,
          toAdd = toAdd,
          nonce = nonce,
      )
    }
  }

  fun retryLoadMore() {
    log.d { "自动加载投稿列表 -> 手动重试" }
    maintainPagerBuffer(forceBoundary = true)
  }

  fun onContextChanged() {
    clearZoomOverlayState(publish = false)
    publishState()
    loadCurrentAndSchedulePrefetch()
    maintainPagerBuffer(forceBoundary = false)
  }

  private suspend fun executeTagBlockRequest(
      sid: Int,
      tagName: String,
      toAdd: Boolean,
      nonce: String,
  ) {
    when (
        val blocked =
            submissionSource.blockTag(
                sid = sid,
                tagName = tagName,
                nonce = nonce,
                toAdd = toAdd,
            )
    ) {
      is PageState.Success -> {
        val latest = detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
        val normalizedTagKey = normalizeTagKey(tagName)
        val latestBlocked = latest.blockedKeywords.toMutableSet()
        if (toAdd) {
          latestBlocked += normalizedTagKey
        } else {
          latestBlocked.remove(normalizedTagKey)
        }
        detailBySid[sid] =
            latest.copy(
                blockedKeywords = latestBlocked.toSet(),
                favoriteErrorMessage = null,
            )
        emitToast(
            if (toAdd) appString(Res.string.tag_blocked, tagName)
            else appString(Res.string.tag_unblocked, tagName)
        )
        log.i { "标签屏蔽 -> 成功(sid=$sid,tag=$tagName,toAdd=$toAdd)" }
      }

      is PageState.AuthRequired -> {
        val latest = detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
        detailBySid[sid] = latest.copy(favoriteErrorMessage = blocked.message)
        emitToast(appString(Res.string.tag_block_failed, blocked.message))
        log.w { "标签屏蔽 -> 需要重新登录(sid=$sid,tag=$tagName)" }
      }

      PageState.CfChallenge -> {
        val latest = detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
        detailBySid[sid] =
            latest.copy(favoriteErrorMessage = appString(Res.string.cloudflare_challenge_title))
        emitToast(
            appString(Res.string.tag_block_failed, appString(Res.string.cloudflare_challenge_title))
        )
        log.w { "标签屏蔽 -> Cloudflare验证(sid=$sid,tag=$tagName)" }
      }

      is PageState.MatureBlocked -> {
        val latest = detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
        detailBySid[sid] = latest.copy(favoriteErrorMessage = blocked.reason)
        emitToast(appString(Res.string.tag_block_failed, blocked.reason))
        log.w { "标签屏蔽 -> 受限(sid=$sid,tag=$tagName,reason=${blocked.reason})" }
      }

      is PageState.Error -> {
        val latest = detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
        detailBySid[sid] =
            latest.copy(
                favoriteErrorMessage = blocked.exception.message ?: blocked.exception.toString()
            )
        emitToast(
            appString(
                Res.string.tag_block_failed,
                blocked.exception.message ?: blocked.exception.toString(),
            )
        )
        log.e(blocked.exception) { "标签屏蔽 -> 失败(sid=$sid,tag=$tagName)" }
      }

      PageState.Loading -> Unit
    }
    publishState()
  }

  private fun publishState() {
    val size = contextController.size()
    if (size == 0) {
      stateSink(SubmissionPagerUiState.Empty)
      pageStateSink(
          PageState.Error(IllegalStateException(appString(Res.string.no_browsable_content)))
      )
      return
    }

    val index = contextController.currentIndex()
    val items = buildList {
      for (cursor in 0 until size) {
        val item = contextController.getAt(cursor) ?: continue
        add(item)
      }
    }

    val uiState =
        SubmissionPagerUiState.Data(
            submissions = items,
            detailBySid = detailBySid.toMap(),
            zoomOverlayImageUrl = zoomOverlayImageUrl,
            zoomImageOcrState = zoomImageOcrState,
            scrollToTopVersionBySid = scrollToTopVersionBySid.toMap(),
            currentIndex = index.coerceIn(0, (items.lastIndex).coerceAtLeast(0)),
            hasPrevious =
                contextController.hasPreviousCached() || contextController.hasPreviousPages(),
            hasNext = contextController.hasNextCached() || contextController.hasMorePages(),
            hasMore = contextController.hasMorePages(),
            isLoadingMore = contextController.isLoadingMore(),
            appendErrorMessage = contextController.appendErrorMessage(),
        )
    stateSink(uiState)
    pageStateSink(PageState.Success(uiState))
  }

  private fun loadCurrentAndSchedulePrefetch() {
    val current = contextController.current() ?: return
    loadDetail(current = current, force = false)
    scheduleDetailPrefetch()
  }

  private fun scheduleDetailPrefetch() {
    prefetchJob?.cancel()
    val targetSids = computePrefetchCandidateSids()
    if (targetSids.isEmpty()) return

    prefetchJob =
        screenModelScope.launch {
          delay(pagerPrefetchDebounceMs)
          targetSids.forEach { sid ->
            if (sid in prefetchedSuccessSids || sid in prefetchingSids) return@forEach
            prefetchingSids += sid
            launch {
              try {
                when (val next = submissionSource.loadBySid(sid)) {
                  is PageState.Success -> {
                    prefetchedSuccessSids += sid
                    val currentState = detailBySid[sid] as? SubmissionDetailUiState.Success
                    detailBySid[sid] =
                        buildSuccessDetailState(
                            sid = sid,
                            detail = next.data,
                            previous = currentState,
                            blockedKeywords =
                                currentState?.blockedKeywords ?: toBlockedKeywordSet(next.data),
                        )
                    publishState()
                  }

                  is PageState.AuthRequired,
                  PageState.CfChallenge,
                  is PageState.MatureBlocked,
                  is PageState.Error,
                  PageState.Loading -> Unit
                }
              } finally {
                prefetchingSids -= sid
              }
            }
          }
        }
  }

  private fun computePrefetchCandidateSids(): List<Int> {
    val totalSize = contextController.size()
    if (totalSize == 0) return emptyList()
    val targetIndices =
        computeSubmissionPrefetchIndices(
            currentIndex = contextController.currentIndex(),
            lastIndex = totalSize - 1,
        )
    return targetIndices.mapNotNull { index -> contextController.getAt(index)?.id }
  }

  private fun translateCurrent(target: SubmissionTranslationTarget) {
    val current = contextController.current() ?: return
    val sid = current.id
    val currentState = detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
    val translationState = currentState.translationStateOf(target) ?: return
    if (translationState.showTranslation) {
      if (translationState.translating) return
      detailBySid[sid] =
          currentState.withTranslationState(
              target = target,
              state = translationState.copy(showTranslation = false),
          )
      publishState()
      return
    }
    if (translationState.sourceBlocks.isEmpty()) return

    val sourceMode = translationState.sourceMode
    val activeVariant = translationState.variantOf(sourceMode)
    if (activeVariant.translating) return
    if (activeVariant.canReuseTranslationResult()) {
      detailBySid[sid] =
          currentState.withTranslationState(
              target = target,
              state = translationState.copy(showTranslation = true),
          )
      publishState()
      return
    }

    val jobKey = SubmissionTranslationJobKey(sid = sid, target = target)
    cancelTranslationJob(sid, target)

    val pendingVariant = activeVariant.toPendingState(sourceMode = sourceMode)
    val pendingState =
        translationState
            .withVariant(mode = sourceMode, variant = pendingVariant)
            .copy(showTranslation = true)
    detailBySid[sid] = currentState.withTranslationState(target = target, state = pendingState)
    publishState()

    val job =
        screenModelScope.launch {
          try {
            translationService.translateBlocks(pendingVariant.sourceBlocks) { index, result ->
              val latestState =
                  detailBySid[sid] as? SubmissionDetailUiState.Success ?: return@translateBlocks
              val latestTranslationState =
                  latestState.translationStateOf(target) ?: return@translateBlocks
              if (latestTranslationState.sourceKey != pendingState.sourceKey) return@translateBlocks

              val updatedState =
                  latestTranslationState.withVariant(
                      mode = sourceMode,
                      variant =
                          latestTranslationState
                              .variantOf(sourceMode)
                              .withBlockResult(
                                  index = index,
                                  result = result,
                              ),
                  )
              detailBySid[sid] =
                  latestState.withTranslationState(target = target, state = updatedState)
              publishState()
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (_: Throwable) {
            val latestState = detailBySid[sid] as? SubmissionDetailUiState.Success ?: return@launch
            val latestTranslationState = latestState.translationStateOf(target) ?: return@launch
            if (latestTranslationState.sourceKey == pendingState.sourceKey) {
              detailBySid[sid] =
                  latestState.withTranslationState(
                      target = target,
                      state =
                          latestTranslationState.withVariant(
                              mode = sourceMode,
                              variant =
                                  latestTranslationState
                                      .variantOf(sourceMode)
                                      .markPendingBlocksAsFailed(),
                          ),
                  )
              publishState()
            }
          } finally {
            val currentJob = currentCoroutineContext()[Job]
            val runningJob = translationJobs[jobKey]
            if (runningJob === currentJob) {
              translationJobs.remove(jobKey)
            }
            val latestState = detailBySid[sid] as? SubmissionDetailUiState.Success ?: return@launch
            val latestTranslationState = latestState.translationStateOf(target) ?: return@launch
            if (latestTranslationState.sourceKey == pendingState.sourceKey) {
              detailBySid[sid] =
                  latestState.withTranslationState(
                      target = target,
                      state =
                          latestTranslationState.withVariant(
                              mode = sourceMode,
                              variant =
                                  latestTranslationState
                                      .variantOf(sourceMode)
                                      .copy(translating = false),
                          ),
                  )
              publishState()
              val finalTranslationState =
                  (detailBySid[sid] as? SubmissionDetailUiState.Success)?.translationStateOf(target)
                      ?: return@launch
              val hasFailure =
                  finalTranslationState.variantOf(sourceMode).blocks.any { block ->
                    block.status == SubmissionDescriptionTranslationStatus.FAILURE
                  }
              if (hasFailure) {
                emitTranslationRetryFeedback(
                    message = translationFailureMessage(target),
                    sid = sid,
                    target = target,
                    sourceKey = pendingState.sourceKey,
                )
              }
            }
          }
        }
    translationJobs[jobKey] = job
  }

  private fun toggleWrapCurrent(target: SubmissionTranslationTarget) {
    val current = contextController.current() ?: return
    val sid = current.id
    val currentState = detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
    val translationState = currentState.translationStateOf(target) ?: return
    if (translationState.showTranslation || translationState.translating) return

    val nextMode =
        when (translationState.sourceMode) {
          SubmissionTranslationSourceMode.RAW -> SubmissionTranslationSourceMode.WRAPPED
          SubmissionTranslationSourceMode.WRAPPED -> SubmissionTranslationSourceMode.RAW
        }
    detailBySid[sid] =
        currentState.withTranslationState(
            target = target,
            state = translationState.copy(sourceMode = nextMode),
        )
    publishState()
  }

  private fun cancelTranslationJob(sid: Int, target: SubmissionTranslationTarget) {
    val key = SubmissionTranslationJobKey(sid = sid, target = target)
    translationJobs.remove(key)?.cancel()
  }

  private fun maintainPagerBuffer(forceBoundary: Boolean) {
    if (warmBufferIfNeeded()) return
    loadBoundaryPageIfNeeded(force = forceBoundary)
  }

  private fun warmBufferIfNeeded(): Boolean {
    if (contextController.sourceKind() != SubmissionContextSourceKind.SEQUENCE) return false
    val currentSize = contextController.size()
    if (currentSize <= 0 || currentSize >= seriesWarmBufferCount) return false
    return when {
      contextController.hasMorePages() -> {
        log.d { "自动加载投稿列表 -> 预热后续缓存(size=$currentSize,target=$seriesWarmBufferCount)" }
        contextController.requestAppend(force = false)
        true
      }

      contextController.hasPreviousPages() -> {
        log.d { "自动加载投稿列表 -> 预热前序缓存(size=$currentSize,target=$seriesWarmBufferCount)" }
        contextController.requestPrepend(force = false)
        true
      }

      else -> false
    }
  }

  private fun loadBoundaryPageIfNeeded(force: Boolean) {
    val currentSize = contextController.size()
    if (currentSize <= 0) return
    val currentIndex = contextController.currentIndex()
    val isNearStart = currentIndex <= seriesInitialReadyCount
    val isNearEnd = currentIndex >= currentSize - 1 - seriesInitialReadyCount
    when {
      contextController.hasMorePages() && (force || isNearEnd) -> {
        log.d { "自动加载投稿列表 -> 触发后续检查(force=$force,current=$currentIndex,size=$currentSize)" }
        contextController.requestAppend(force = force)
      }

      contextController.sourceKind() == SubmissionContextSourceKind.SEQUENCE &&
          contextController.hasPreviousPages() &&
          (force || isNearStart) -> {
        log.d { "自动加载投稿列表 -> 触发前序检查(force=$force,current=$currentIndex,size=$currentSize)" }
        contextController.requestPrepend(force = force)
      }
    }
  }

  private fun loadDetail(current: SubmissionThumbnail, force: Boolean) {
    val sid = current.id
    val previous = detailBySid[sid] as? SubmissionDetailUiState.Success
    if (!force) {
      when (detailBySid[sid]) {
        is SubmissionDetailUiState.Loading,
        is SubmissionDetailUiState.Success -> return

        is SubmissionDetailUiState.Error,
        null -> Unit
      }
    }
    log.d { "加载投稿详情 -> 开始(sid=$sid,force=$force)" }
    detailBySid[sid] = SubmissionDetailUiState.Loading
    publishState()

    screenModelScope.launch {
      val targetUrl = current.submissionUrl.ifBlank { FaUrls.submission(current.id) }
      val detailState =
          when (val next = submissionSource.loadByUrl(targetUrl)) {
            is PageState.Success -> {
              prefetchedSuccessSids += sid
              log.d { "加载投稿详情 -> ${summarizePageState(next)}(sid=$sid)" }
              buildSuccessDetailState(
                  sid = sid,
                  detail = next.data,
                  previous = previous,
              )
            }

            is PageState.AuthRequired -> {
              log.w { "加载投稿详情 -> 需要重新登录(sid=$sid)" }
              SubmissionDetailUiState.Error(next.message)
            }

            PageState.CfChallenge -> {
              log.w { "加载投稿详情 -> Cloudflare验证(sid=$sid)" }
              SubmissionDetailUiState.Error(appString(Res.string.cloudflare_challenge_title))
            }

            is PageState.MatureBlocked -> {
              log.w { "加载投稿详情 -> 受限(sid=$sid,reason=${next.reason})" }
              SubmissionDetailUiState.Error(next.reason)
            }

            is PageState.Error -> {
              log.e(next.exception) { "加载投稿详情 -> 失败(sid=$sid)" }
              SubmissionDetailUiState.Error(next.exception.message ?: next.exception.toString())
            }

            PageState.Loading -> {
              log.w { "加载投稿详情 -> 加载中断(sid=$sid)" }
              SubmissionDetailUiState.Error(appString(Res.string.interrupted_loading))
            }
          }
      detailBySid[sid] = detailState
      publishState()
    }
  }

  private fun buildSuccessDetailState(
      sid: Int,
      detail: Submission,
      previous: SubmissionDetailUiState.Success? = null,
      blockedKeywords: Set<String> = toBlockedKeywordSet(detail),
      favoriteUpdating: Boolean = false,
      favoriteErrorMessage: String? = null,
  ): SubmissionDetailUiState.Success {
    val descriptionTranslationState =
        resolveTranslationState(
            sourceHtml = detail.descriptionHtml,
            sourceFileName = null,
            previous = previous?.descriptionTranslationState,
            translationService = translationService,
        )
    val attachmentTextState =
        resolveAttachmentTextState(
            detail = detail,
            previous = previous?.attachmentTextState,
        )
    val attachmentTranslationState =
        resolveAttachmentTranslationState(
            attachmentTextState = attachmentTextState,
            previous = previous?.attachmentTranslationState,
            translationService = translationService,
        )

    if (previous?.descriptionTranslationState?.sourceKey != descriptionTranslationState.sourceKey) {
      cancelTranslationJob(sid, SubmissionTranslationTarget.DESCRIPTION)
    }
    if (previous?.attachmentTranslationState?.sourceKey != attachmentTranslationState?.sourceKey) {
      cancelTranslationJob(sid, SubmissionTranslationTarget.ATTACHMENT)
    }

    return SubmissionDetailUiState.Success(
        detail = detail,
        blockedKeywords = blockedKeywords,
        descriptionTranslationState = descriptionTranslationState,
        favoriteUpdating = favoriteUpdating,
        favoriteErrorMessage = favoriteErrorMessage,
        attachmentTextState = attachmentTextState,
        attachmentTranslationState = attachmentTranslationState,
        imageOcrTranslationExportSnapshot =
            previous?.imageOcrTranslationExportSnapshot?.takeIf { snapshot ->
              snapshot.imageUrl == exportableImageUrl(detail)
            },
    )
  }

  private fun exportableImageUrl(detail: Submission): String =
      detail.fullImageUrl.ifBlank { detail.previewImageUrl }.trim()

  private fun guessNextFavoriteActionUrl(currentActionUrl: String): String {
    val url = currentActionUrl.trim()
    if (url.isBlank()) return url
    return when {
      url.contains("/unfav/") -> url.replaceFirst("/unfav/", "/fav/")
      url.contains("/fav/") -> url.replaceFirst("/fav/", "/unfav/")
      else -> url
    }
  }

  private fun emitFeedback(request: AppFeedbackRequest) {
    feedbackSink(request)
  }

  private fun emitToast(message: String) {
    emitFeedback(AppFeedbackRequest(message = message))
  }

  private fun emitTranslationRetryFeedback(
      message: String,
      sid: Int,
      target: SubmissionTranslationTarget,
      sourceKey: String,
  ) {
    if (settingsService == null) {
      emitToast(message)
      return
    }
    val nextProvider = currentTranslationProvider().nextRetryProvider()
    emitFeedback(
        AppFeedbackRequest(
            message = message,
            actionLabel = switchProviderAndRetryLabel(nextProvider),
            onAction = {
              val retried =
                  switchTranslationProvider(nextProvider) {
                    retryTranslationIfPossible(
                        sid = sid,
                        target = target,
                        sourceKey = sourceKey,
                    )
                  }
              if (!retried) {
                emitToast(translationProviderSwitchedMessage(nextProvider))
              }
            },
        )
    )
  }

  private fun emitImageOcrRetryFeedback(
      message: String,
      targetImageUrl: String?,
  ) {
    if (settingsService == null) {
      emitToast(message)
      return
    }
    val nextProvider = currentTranslationProvider().nextRetryProvider()
    emitFeedback(
        AppFeedbackRequest(
            message = message,
            actionLabel = switchProviderAndRetryLabel(nextProvider),
            onAction = {
              val retried =
                  switchTranslationProvider(nextProvider) {
                    retryImageOcrIfPossible(targetImageUrl)
                  }
              if (!retried) {
                emitToast(translationProviderSwitchedMessage(nextProvider))
              }
            },
        )
    )
  }

  private suspend fun switchTranslationProvider(
      provider: TranslationProvider,
      retry: suspend () -> Boolean,
  ): Boolean {
    val currentSettingsService = settingsService ?: return false
    currentSettingsService.ensureLoaded()
    currentSettingsService.updateTranslationProvider(provider)
    return retry()
  }

  private fun currentTranslationProvider(): TranslationProvider =
      settingsService?.settings?.value?.translationProvider
          ?: AppSettings.defaultTranslationProvider

  private fun providerLabel(provider: TranslationProvider): String =
      when (provider) {
        TranslationProvider.GOOGLE ->
            safeAppString(Res.string.translation_provider_google) { "Google Translate" }
        TranslationProvider.MICROSOFT ->
            safeAppString(Res.string.translation_provider_microsoft) { "Microsoft Translator" }
        TranslationProvider.OPENAI_COMPATIBLE ->
            safeAppString(Res.string.translation_provider_openai_compatible) { "OpenAI Compatible" }
      }

  private fun translationFailureMessage(target: SubmissionTranslationTarget): String =
      when (target) {
        SubmissionTranslationTarget.DESCRIPTION ->
            safeAppString(Res.string.description_translation_failed) {
              "Description translation failed"
            }
        SubmissionTranslationTarget.ATTACHMENT ->
            safeAppString(Res.string.attachment_translation_failed) {
              "Attachment translation failed"
            }
      }

  private fun imageOcrEmptyMessage(): String =
      safeAppString(Res.string.image_ocr_empty) { "No text detected in image" }

  private fun imageOcrFailedMessage(reason: String): String =
      safeAppString(Res.string.image_ocr_failed, reason) { "Image OCR failed: $reason" }

  private fun imageOcrTranslationFailedMessage(): String =
      safeAppString(Res.string.image_ocr_translation_failed) { "Image OCR translation failed" }

  private fun imageOcrTranslationFailedWithReasonMessage(reason: String): String =
      safeAppString(Res.string.image_ocr_translation_failed_with_reason, reason) {
        "Image OCR translation failed: $reason"
      }

  private fun imageOcrTranslationEmptyMessage(): String =
      safeAppString(Res.string.image_ocr_translation_empty) { "Translation result is empty" }

  private fun switchProviderAndRetryLabel(provider: TranslationProvider): String =
      safeAppString(
          Res.string.translation_switch_provider_and_retry,
          providerLabel(provider),
      ) {
        "Switch to ${providerLabel(provider)} and retry"
      }

  private fun translationProviderSwitchedMessage(provider: TranslationProvider): String =
      safeAppString(
          Res.string.translation_provider_switched,
          providerLabel(provider),
      ) {
        "Switched translation provider to ${providerLabel(provider)}"
      }

  private fun safeAppString(
      resource: StringResource,
      vararg formatArgs: Any,
      fallback: () -> String,
  ): String = safeAppStringOrFallback(resource, *formatArgs, fallback = fallback)

  private suspend fun retryTranslationIfPossible(
      sid: Int,
      target: SubmissionTranslationTarget,
      sourceKey: String,
  ): Boolean {
    if (contextController.current()?.id != sid) return false
    val currentState = detailBySid[sid] as? SubmissionDetailUiState.Success ?: return false
    val translationState = currentState.translationStateOf(target) ?: return false
    if (translationState.sourceKey != sourceKey) return false
    if (translationState.translating || translationState.sourceBlocks.isEmpty()) return false
    if (translationState.showTranslation) {
      detailBySid[sid] =
          currentState.withTranslationState(
              target = target,
              state = translationState.copy(showTranslation = false),
          )
    }
    translateCurrent(target)
    return true
  }

  private fun retryImageOcrIfPossible(targetImageUrl: String?): Boolean {
    val showingState = zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return false
    if (zoomOverlayImageUrl != targetImageUrl) return false
    if (showingState.translationMode == SubmissionImageOcrTranslationMode.LOADING) return false
    if (showingState.blocks.isEmpty()) return false
    translateImageOcrCurrent()
    return true
  }

  private fun clearCurrentImageOcrTranslationExportSnapshot(publish: Boolean) {
    val current = contextController.current() ?: return
    val currentState = detailBySid[current.id] as? SubmissionDetailUiState.Success ?: return
    if (currentState.imageOcrTranslationExportSnapshot == null) return
    detailBySid[current.id] = currentState.copy(imageOcrTranslationExportSnapshot = null)
    if (publish) {
      publishState()
    }
  }

  private fun updateCurrentImageOcrTranslationExportSnapshot(
      imageUrl: String?,
      blocks: List<SubmissionImageOcrBlockUiState>,
      publish: Boolean,
  ) {
    val normalizedImageUrl = imageUrl?.trim().orEmpty()
    val current = contextController.current() ?: return
    val currentState = detailBySid[current.id] as? SubmissionDetailUiState.Success ?: return
    val nextSnapshot =
        normalizedImageUrl
            .takeIf { it.isNotBlank() }
            ?.let { validImageUrl ->
              blocks
                  .takeIf { candidates ->
                    candidates.any { block ->
                      block.translationStatus == SubmissionImageOcrTranslationStatus.SUCCESS &&
                          block.translatedText?.isNotBlank() == true
                    }
                  }
                  ?.let { translatedBlocks ->
                    SubmissionImageOcrTranslationExportSnapshot(
                        imageUrl = validImageUrl,
                        provider = currentTranslationProvider(),
                        blocks = translatedBlocks,
                    )
                  }
            }
    if (currentState.imageOcrTranslationExportSnapshot == nextSnapshot) return
    detailBySid[current.id] = currentState.copy(imageOcrTranslationExportSnapshot = nextSnapshot)
    if (publish) {
      publishState()
    }
  }

  private fun clearZoomOverlayState(publish: Boolean) {
    cancelImageOcrJobs()
    imageOcrMergedBlockCounter = 0L
    zoomOverlayImageUrl = null
    zoomImageOcrState = SubmissionImageOcrUiState.Idle
    if (publish) {
      publishState()
    }
  }

  private suspend fun translateImageOcrBlocks(
      blocks: List<SubmissionImageOcrBlockUiState>
  ): List<SubmissionImageOcrBlockUiState> {
    val translations =
        try {
          imageOcrTranslationService.translateTexts(blocks.map { it.originalText })
        } catch (error: Throwable) {
          List(blocks.size) {
            SubmissionImageOcrTranslationResult.Failure(
                error.message?.takeIf { it.isNotBlank() } ?: error.toString()
            )
          }
        }

    return blocks.mapIndexed { index, block ->
      val translation = translations.getOrNull(index) ?: SubmissionImageOcrTranslationResult.Empty
      when (translation) {
        is SubmissionImageOcrTranslationResult.Success ->
            block.copy(
                translatedText = translation.translatedText,
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

  private fun buildRecognizedImageOcrBlocks(
      blocks: List<RecognizedTextBlock>
  ): List<SubmissionImageOcrBlockUiState> =
      blocks.mapIndexed { index, block ->
        SubmissionImageOcrBlockUiState(
            id = "ocr-block-$index",
            points = block.points,
            originalText = block.text,
            translatedText = null,
            translationStatus = SubmissionImageOcrTranslationStatus.IDLE,
        )
      }

  private fun translateMergedImageOcrBlock(blockId: String) {
    val showingState = zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return
    val block = showingState.blocks.firstOrNull { candidate -> candidate.id == blockId } ?: return
    imageOcrLog.i { "开始翻译合并后的 OCR 框 -> blockId=$blockId" }
    cancelImageOcrTranslationJob()
    val targetImageUrl = zoomOverlayImageUrl
    imageOcrTranslationJob =
        screenModelScope.launch {
          try {
            when (val result = imageOcrTranslationService.translateText(block.originalText)) {
              is SubmissionImageOcrTranslationResult.Success ->
                  updateMergedImageOcrBlockTranslation(
                          blockId = blockId,
                          targetImageUrl = targetImageUrl,
                          translatedText = result.translatedText,
                          status = SubmissionImageOcrTranslationStatus.SUCCESS,
                      )
                      .also { imageOcrLog.i { "合并 OCR 框翻译成功 -> blockId=$blockId" } }

              SubmissionImageOcrTranslationResult.Empty -> {
                updateMergedImageOcrBlockTranslation(
                    blockId = blockId,
                    targetImageUrl = targetImageUrl,
                    translatedText = null,
                    status = SubmissionImageOcrTranslationStatus.EMPTY,
                )
                imageOcrLog.w { "合并 OCR 框翻译为空 -> blockId=$blockId" }
                emitToast(appString(Res.string.image_ocr_translation_empty))
              }

              is SubmissionImageOcrTranslationResult.Failure -> {
                updateMergedImageOcrBlockTranslation(
                    blockId = blockId,
                    targetImageUrl = targetImageUrl,
                    translatedText = null,
                    status = SubmissionImageOcrTranslationStatus.FAILURE,
                )
                imageOcrLog.w { "合并 OCR 框翻译失败 -> blockId=$blockId, message=${result.message}" }
                emitImageOcrRetryFeedback(
                    message =
                        appString(
                            Res.string.image_ocr_translation_failed_with_reason,
                            result.message,
                        ),
                    targetImageUrl = targetImageUrl,
                )
              }
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (error: Throwable) {
            imageOcrLog.e(error) { "合并 OCR 框翻译异常 -> blockId=$blockId" }
            updateMergedImageOcrBlockTranslation(
                blockId = blockId,
                targetImageUrl = targetImageUrl,
                translatedText = null,
                status = SubmissionImageOcrTranslationStatus.FAILURE,
            )
            emitImageOcrRetryFeedback(
                message =
                    appString(
                        Res.string.image_ocr_translation_failed_with_reason,
                        error.message?.takeIf { it.isNotBlank() } ?: error.toString(),
                    ),
                targetImageUrl = targetImageUrl,
            )
          } finally {
            val currentJob = currentCoroutineContext()[Job]
            if (imageOcrTranslationJob === currentJob) {
              imageOcrTranslationJob = null
            }
          }
        }
  }

  private fun updateMergedImageOcrBlockTranslation(
      blockId: String,
      targetImageUrl: String?,
      translatedText: String?,
      status: SubmissionImageOcrTranslationStatus,
  ) {
    val latestState = zoomImageOcrState as? SubmissionImageOcrUiState.Showing ?: return
    if (zoomOverlayImageUrl != targetImageUrl) return
    if (latestState.blocks.none { block -> block.id == blockId }) return
    zoomImageOcrState =
        latestState.copy(
            blocks =
                latestState.blocks.map { block ->
                  if (block.id != blockId) {
                    block
                  } else {
                    block.copy(
                        translatedText = translatedText,
                        translationStatus = status,
                    )
                  }
                }
        )
    updateCurrentImageOcrTranslationExportSnapshot(
        imageUrl = targetImageUrl,
        blocks = (zoomImageOcrState as? SubmissionImageOcrUiState.Showing)?.blocks.orEmpty(),
        publish = false,
    )
    publishState()
  }

  private fun cancelImageOcrJobs() {
    imageOcrRecognitionJob?.cancel()
    imageOcrRecognitionJob = null
    cancelImageOcrTranslationJob()
    cancelImageOcrDialogJob()
  }

  private fun cancelImageOcrTranslationJob() {
    imageOcrTranslationJob?.cancel()
    imageOcrTranslationJob = null
  }

  private fun cancelImageOcrDialogJob() {
    imageOcrDialogJob?.cancel()
    imageOcrDialogJob = null
  }

  private fun toBlockedKeywordSet(detail: Submission): Set<String> =
      detail.blockedTagNames.map(::normalizeTagKey).filter { it.isNotBlank() }.toSet()

  private fun nextMergedImageOcrBlockId(): String {
    val nextId = "ocr-merged-${imageOcrMergedBlockCounter}"
    imageOcrMergedBlockCounter += 1L
    return nextId
  }
}

private fun normalizeTagKey(tagName: String): String = tagName.trim().lowercase()

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
    RecognizedTextBlock(
        text = originalText,
        points = points,
        confidence = null,
    )

private fun SubmissionImageOcrBlockUiState.toSelectionKey(): ImageOcrSelectionKey =
    ImageOcrSelectionKey(originalText = originalText, points = points)

private fun RecognizedTextBlock.toSelectionKey(): ImageOcrSelectionKey =
    ImageOcrSelectionKey(originalText = text, points = points)

private fun TranslationProvider.nextRetryProvider(): TranslationProvider =
    when (this) {
      TranslationProvider.GOOGLE -> TranslationProvider.MICROSOFT
      TranslationProvider.MICROSOFT,
      TranslationProvider.OPENAI_COMPATIBLE -> TranslationProvider.GOOGLE
    }

private fun sortImageOcrBlocks(
    blocks: List<SubmissionImageOcrBlockUiState>
): List<SubmissionImageOcrBlockUiState> =
    blocks.sortedWith(
        compareBy(
            { block -> block.points.minOfOrNull { point -> point.y } ?: 0f },
            { block -> block.points.minOfOrNull { point -> point.x } ?: 0f },
        )
    )

private fun List<NormalizedImagePoint>.toLogString(): String =
    joinToString(prefix = "[", postfix = "]") { point -> "(${point.x},${point.y})" }

private fun resolveAttachmentTextState(
    detail: Submission,
    previous: SubmissionAttachmentTextUiState? = null,
): SubmissionAttachmentTextUiState? {
  val fileName =
      deriveAttachmentFileName(detail.downloadUrl, detail.downloadFileName)?.takeIf {
        AttachmentTextExtractor.isSupported(it)
      } ?: return null

  return when (previous) {
    null -> SubmissionAttachmentTextUiState.Idle(fileName)
    is SubmissionAttachmentTextUiState.Idle -> previous.copy(fileName = fileName)
    is SubmissionAttachmentTextUiState.Loading -> previous.copy(fileName = fileName)
    is SubmissionAttachmentTextUiState.Error -> previous.copy(fileName = fileName)
    is SubmissionAttachmentTextUiState.Success ->
        if (previous.fileName == fileName) previous
        else SubmissionAttachmentTextUiState.Idle(fileName)
  }
}

private fun resolveAttachmentTranslationState(
    attachmentTextState: SubmissionAttachmentTextUiState?,
    previous: SubmissionTranslationUiState?,
    translationService: SubmissionDescriptionTranslationService,
): SubmissionTranslationUiState? {
  val successState = attachmentTextState as? SubmissionAttachmentTextUiState.Success ?: return null
  return resolveTranslationState(
      sourceHtml = successState.document.html,
      sourceFileName = successState.fileName,
      previous = previous,
      translationService = translationService,
  )
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

private fun initialAttachmentTextProgress(
    fileName: String,
    _settingsService: AppSettingsService?,
    _systemLanguageProvider: SystemLanguageProvider?,
): AttachmentTextProgress {
  return AttachmentTextProgress(
      overallFraction = 0f,
      stageIndex = 1,
      stageCount = 1,
      stageId = "download_attachment",
      stageLabel =
          safeAppStringOrFallback(Res.string.download_attachment) { "Download attachment" },
      stageFraction = 0f,
      message =
          safeAppStringOrFallback(Res.string.preparing_attachment_download) {
            "Preparing attachment download"
          },
      currentItemLabel = fileName,
  )
}

private fun safeAppStringOrFallback(
    resource: StringResource,
    vararg formatArgs: Any,
    fallback: () -> String,
): String = runCatching { appString(resource, *formatArgs) }.getOrElse { fallback() }
