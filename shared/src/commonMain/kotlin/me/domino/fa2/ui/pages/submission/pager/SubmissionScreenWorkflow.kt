package me.domino.fa2.ui.pages.submission.pager

import co.touchlab.kermit.Logger
import fa2.shared.generated.resources.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.model.summarizeResourceResult
import me.domino.fa2.data.remote.fa.media.deriveAttachmentFileName
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.domain.attachmenttext.AttachmentTextExtractor
import me.domino.fa2.domain.ocr.NormalizedImagePoint
import me.domino.fa2.domain.ocr.SubmissionImageOcrService
import me.domino.fa2.domain.submissionseries.seriesInitialReadyCount
import me.domino.fa2.domain.submissionseries.seriesWarmBufferCount
import me.domino.fa2.domain.submissionviewer.api.SubmissionContextSourceKind
import me.domino.fa2.domain.submissionviewer.api.SubmissionPagerDetailSource
import me.domino.fa2.domain.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.domain.translation.SubmissionImageOcrTranslationService
import me.domino.fa2.ui.components.feedback.AppFeedbackRequest
import me.domino.fa2.ui.i18n.appString
import me.domino.fa2.ui.pages.submission.attachmenttext.SubmissionAttachmentTextUiState
import me.domino.fa2.ui.pages.submission.content.SubmissionDetailUiState
import me.domino.fa2.ui.state.SubmissionTranslationUiState
import me.domino.fa2.ui.state.UiPageState
import me.domino.fa2.ui.state.resolveTranslationState
import me.domino.fa2.utils.FaUrls

internal const val pagerPrefetchDebounceMs: Long = 500L

internal class SubmissionScreenWorkflow(
    private val initialSid: Int,
    private val contextController: SubmissionPagerContextController,
    private val submissionSource: SubmissionPagerDetailSource,
    private val translationService: SubmissionDescriptionTranslationService,
    private val imageOcrService: SubmissionImageOcrService,
    private val imageOcrTranslationService: SubmissionImageOcrTranslationService,
    private val settingsService: AppSettingsService? = null,
    private val screenModelScope: CoroutineScope,
    private val log: Logger,
    private val stateSink: (SubmissionPagerUiState) -> Unit,
    private val pageStateSink: (UiPageState<SubmissionPagerUiState>) -> Unit,
    private val feedbackSink: (AppFeedbackRequest) -> Unit,
) {
  private val viewState = SubmissionViewStateStore()
  private val translationRetry =
      SubmissionTranslationRetryCoordinator(
          settingsService = settingsService,
          feedbackSink = feedbackSink,
      )
  private val textTranslationController =
      SubmissionTextTranslationController(
          scope = screenModelScope,
          viewState = viewState,
          contextController = contextController,
          service = translationService,
          retryCoordinator = translationRetry,
          publishState = ::publishState,
      )
  private val attachmentTextController =
      SubmissionAttachmentTextController(
          scope = screenModelScope,
          viewState = viewState,
          contextController = contextController,
          source = submissionSource,
          translationService = translationService,
          textTranslationController = textTranslationController,
          publishState = ::publishState,
      )
  private val imageOcrController =
      SubmissionImageOcrController(
          scope = screenModelScope,
          viewState = viewState,
          contextController = contextController,
          recognitionService = imageOcrService,
          translationService = imageOcrTranslationService,
          translationRetry = translationRetry,
          publishState = ::publishState,
      )
  private val prefetchController =
      SubmissionDetailPrefetchController(
          scope = screenModelScope,
          source = submissionSource,
          candidateSids = ::computePrefetchCandidateSids,
          onLoaded = { sid, detail ->
            val currentState = viewState.detailBySid[sid] as? SubmissionDetailUiState.Success
            viewState.detailBySid[sid] =
                buildSuccessDetailState(
                    sid = sid,
                    detail = detail,
                    previous = currentState,
                    blockedKeywords = currentState?.blockedKeywords ?: toBlockedKeywordSet(detail),
                )
            publishState()
          },
      )
  private val socialActionController =
      SubmissionSocialActionController(
          scope = screenModelScope,
          viewState = viewState,
          contextController = contextController,
          source = submissionSource,
          prefetchController = prefetchController,
          log = log,
          publishState = ::publishState,
          feedbackSink = feedbackSink,
          rebuildDetailState = { sid, detail, previous, blockedKeywords ->
            buildSuccessDetailState(
                sid = sid,
                detail = detail,
                previous = previous,
                blockedKeywords = blockedKeywords,
            )
          },
      )

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
    imageOcrController.clear(publish = false)
    contextController.setCurrentIndex(contextController.currentIndex() - 1)
    publishState()
    loadCurrentAndSchedulePrefetch()
    maintainPagerBuffer(forceBoundary = false)
  }

  fun next() {
    if (contextController.hasNextCached()) {
      imageOcrController.clear(publish = false)
      contextController.setCurrentIndex(contextController.currentIndex() + 1)
      publishState()
      loadCurrentAndSchedulePrefetch()
      maintainPagerBuffer(forceBoundary = false)
      return
    }
    maintainPagerBuffer(forceBoundary = true)
  }

  fun onPageChanged(index: Int) {
    imageOcrController.clear(publish = false)
    contextController.setCurrentIndex(index)
    publishState()
    loadCurrentAndSchedulePrefetch()
    maintainPagerBuffer(forceBoundary = false)
  }

  fun setCurrentPageScrollOffset(sid: Int, offset: Int) {
    val normalized = offset.coerceAtLeast(0)
    if (viewState.scrollOffsetBySid[sid] == normalized) return
    viewState.scrollOffsetBySid[sid] = normalized
  }

  fun scrollOffsetForSid(sid: Int): Int = viewState.scrollOffsetBySid[sid] ?: 0

  fun scrollToTopVersionForSid(sid: Int): Long = viewState.scrollToTopVersionBySid[sid] ?: 0L

  fun requestCurrentPageScrollToTop() {
    val current = contextController.current() ?: return
    val sid = current.id
    viewState.scrollOffsetBySid[sid] = 0
    viewState.scrollToTopVersionBySid[sid] = (viewState.scrollToTopVersionBySid[sid] ?: 0L) + 1L
    publishState()
  }

  fun retryCurrentDetail() {
    val current = contextController.current() ?: return
    log.i { "重试详情加载 -> sid=${current.id}" }
    loadDetail(current = current, force = true)
  }

  fun openImageZoom(imageUrl: String) {
    imageOcrController.openImageZoom(imageUrl)
  }

  fun dismissImageZoom() {
    imageOcrController.dismissImageZoom()
  }

  fun toggleImageOcrCurrent() {
    imageOcrController.toggleRecognition()
  }

  fun translateImageOcrCurrent() {
    imageOcrController.translateCurrent()
  }

  fun openImageOcrDialog(blockId: String) {
    imageOcrController.openDialog(blockId)
  }

  fun dismissImageOcrDialog() {
    imageOcrController.dismissDialog()
  }

  fun updateImageOcrDialogDraft(text: String) {
    imageOcrController.updateDialogDraft(text)
  }

  fun refreshImageOcrDialogTranslation() {
    imageOcrController.refreshDialogTranslation()
  }

  fun mergeImageOcrBlocks(draggedBlockId: String, mergeRegionPoints: List<NormalizedImagePoint>) {
    imageOcrController.mergeBlocks(draggedBlockId, mergeRegionPoints)
  }

  fun translateDescriptionCurrent() {
    textTranslationController.translateCurrent(SubmissionTranslationTarget.DESCRIPTION)
  }

  fun toggleDescriptionWrapCurrent() {
    textTranslationController.toggleWrapCurrent(SubmissionTranslationTarget.DESCRIPTION)
  }

  fun translateAttachmentCurrent() {
    textTranslationController.translateCurrent(SubmissionTranslationTarget.ATTACHMENT)
  }

  fun toggleAttachmentWrapCurrent() {
    textTranslationController.toggleWrapCurrent(SubmissionTranslationTarget.ATTACHMENT)
  }

  fun loadAttachmentTextCurrent() {
    attachmentTextController.loadCurrent()
  }

  fun toggleFavoriteCurrent() {
    socialActionController.toggleFavoriteCurrent()
  }

  fun blockKeywordCurrent(tagName: String) {
    socialActionController.blockKeywordCurrent(tagName)
  }

  fun revealBlockedMedia(sid: Int) {
    viewState.revealedBlockedMediaSids += sid
    publishState()
  }

  fun retryLoadMore() {
    log.d { "自动加载投稿列表 -> 手动重试" }
    maintainPagerBuffer(forceBoundary = true)
  }

  fun onContextChanged() {
    imageOcrController.clear(publish = false)
    publishState()
    loadCurrentAndSchedulePrefetch()
    maintainPagerBuffer(forceBoundary = false)
  }

  private fun publishState() {
    val size = contextController.size()
    if (size == 0) {
      stateSink(SubmissionPagerUiState.Empty)
      pageStateSink(UiPageState.Error(IllegalStateException("No browsable content")))
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
            detailBySid = viewState.detailBySid.toMap(),
            zoomOverlayImageUrl = viewState.zoomOverlayImageUrl,
            zoomImageOcrState = viewState.zoomImageOcrState,
            scrollToTopVersionBySid = viewState.scrollToTopVersionBySid.toMap(),
            currentIndex = index.coerceIn(0, (items.lastIndex).coerceAtLeast(0)),
            hasPrevious =
                contextController.hasPreviousCached() || contextController.hasPreviousPages(),
            hasNext = contextController.hasNextCached() || contextController.hasMorePages(),
            hasMore = contextController.hasMorePages(),
            isLoadingMore = contextController.isLoadingMore(),
            appendErrorMessage = contextController.appendErrorMessage(),
            revealedBlockedMediaSids = viewState.revealedBlockedMediaSids.toSet(),
        )
    stateSink(uiState)
    pageStateSink(UiPageState.Success(uiState))
  }

  private fun loadCurrentAndSchedulePrefetch() {
    val current = contextController.current() ?: return
    loadDetail(current = current, force = false)
    prefetchController.schedule()
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
    val previous = viewState.detailBySid[sid] as? SubmissionDetailUiState.Success
    if (!force) {
      when (viewState.detailBySid[sid]) {
        is SubmissionDetailUiState.Loading,
        is SubmissionDetailUiState.Success -> return

        is SubmissionDetailUiState.Error,
        null -> Unit
      }
    }
    log.d { "加载投稿详情 -> 开始(sid=$sid,force=$force)" }
    viewState.detailBySid[sid] = SubmissionDetailUiState.Loading
    publishState()

    screenModelScope.launch {
      val targetUrl = current.submissionUrl.ifBlank { FaUrls.submission(current.id) }
      val detailState =
          when (val next = submissionSource.loadByUrl(targetUrl)) {
            is ResourceResult.Success -> {
              prefetchController.markLoaded(sid)
              log.d { "加载投稿详情 -> ${summarizeResourceResult(next)}(sid=$sid)" }
              buildSuccessDetailState(sid = sid, detail = next.data, previous = previous)
            }

            is ResourceResult.AuthRequired -> {
              log.w { "加载投稿详情 -> 需要重新登录(sid=$sid)" }
              SubmissionDetailUiState.Error(next.message)
            }

            ResourceResult.CfChallenge -> {
              log.w { "加载投稿详情 -> Cloudflare验证(sid=$sid)" }
              SubmissionDetailUiState.Error(appString(Res.string.cloudflare_challenge_title))
            }

            is ResourceResult.MatureBlocked -> {
              log.w { "加载投稿详情 -> 受限(sid=$sid,reason=${next.reason})" }
              SubmissionDetailUiState.Error(next.reason)
            }

            is ResourceResult.Error -> {
              log.e(next.exception) { "加载投稿详情 -> 失败(sid=$sid)" }
              SubmissionDetailUiState.Error(next.exception.message ?: next.exception.toString())
            }
          }
      viewState.detailBySid[sid] = detailState
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
        resolveAttachmentTextState(detail = detail, previous = previous?.attachmentTextState)
    val attachmentTranslationState =
        resolveAttachmentTranslationState(
            attachmentTextState = attachmentTextState,
            previous = previous?.attachmentTranslationState,
            translationService = translationService,
        )

    if (previous?.descriptionTranslationState?.sourceKey != descriptionTranslationState.sourceKey) {
      textTranslationController.cancel(sid, SubmissionTranslationTarget.DESCRIPTION)
    }
    if (previous?.attachmentTranslationState?.sourceKey != attachmentTranslationState?.sourceKey) {
      textTranslationController.cancel(sid, SubmissionTranslationTarget.ATTACHMENT)
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

  private fun toBlockedKeywordSet(detail: Submission): Set<String> =
      detail.blockedTagNames.map(::normalizeTagKey).filter { it.isNotBlank() }.toSet()
}

private fun normalizeTagKey(tagName: String): String = tagName.trim().lowercase()

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
