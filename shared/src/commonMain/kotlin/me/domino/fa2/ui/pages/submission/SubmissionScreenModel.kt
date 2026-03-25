package me.domino.fa2.ui.pages.submission

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.translation.SubmissionDescriptionBlock
import me.domino.fa2.data.translation.SubmissionDescriptionBlockResult
import me.domino.fa2.data.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.ui.navigation.SubmissionListHolder
import me.domino.fa2.ui.state.PaginationSnapshot
import me.domino.fa2.ui.state.PaginationStateMachine
import me.domino.fa2.ui.state.SubmissionDescriptionDisplayBlock
import me.domino.fa2.ui.state.SubmissionDescriptionTranslationStatus
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.attachmenttext.AttachmentTextExtractor
import me.domino.fa2.util.attachmenttext.AttachmentTextProgress
import me.domino.fa2.util.attachmenttext.deriveAttachmentFileName
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizePageState

private const val pagerAppendThreshold = 2
internal const val pagerPrefetchDebounceMs: Long = 500L

private enum class SubmissionTranslationTarget {
  DESCRIPTION,
  ATTACHMENT,
}

private data class SubmissionTranslationJobKey(
    val sid: Int,
    val target: SubmissionTranslationTarget,
)

/** 投稿详情浏览页面状态模型。 */
class SubmissionScreenModel(
    /** 初始投稿 ID。 */
    private val initialSid: Int,
    /** 投稿列表共享持有器。 */
    private val holder: SubmissionListHolder,
    /** Feed 数据源。 */
    private val feedSource: SubmissionPagerFeedSource,
    /** Submission 数据源。 */
    private val submissionSource: SubmissionPagerDetailSource,
    /** 描述/附件翻译编排服务。 */
    private val translationService: SubmissionDescriptionTranslationService,
) : StateScreenModel<SubmissionPagerUiState>(SubmissionPagerUiState.Empty) {
  private val log = FaLog.withTag("SubmissionScreenModel")
  private val paginationStateMachine =
      PaginationStateMachine<SubmissionThumbnail, Int>(keyOf = { item -> item.id })
  private val mutablePageState =
      MutableStateFlow<PageState<SubmissionPagerUiState>>(PageState.Loading)
  val pageState: StateFlow<PageState<SubmissionPagerUiState>> = mutablePageState.asStateFlow()

  /** 每个 sid 的详情状态。 */
  private val detailBySid: MutableMap<Int, SubmissionDetailUiState> = mutableMapOf()

  /** 每个 sid 的正文滚动偏移。 */
  private val scrollOffsetBySid: MutableMap<Int, Int> = mutableMapOf()

  /** 每个 sid 的回顶命令版本。 */
  private val scrollToTopVersionBySid: MutableMap<Int, Long> = mutableMapOf()

  /** 翻译任务。 */
  private val translationJobs: MutableMap<SubmissionTranslationJobKey, Job> = mutableMapOf()

  /** 当前追加任务。 */
  private var appendJob: Job? = null

  /** 当前预载任务。 */
  private var prefetchJob: Job? = null

  /** 当前下一页 URL。 */
  private var nextPageUrl: String? = null

  /** 当前是否在追加。 */
  private var isLoadingMore: Boolean = false

  /** 追加错误。 */
  private var appendErrorMessage: String? = null

  /** 已成功预载的 sid。 */
  private val prefetchedSuccessSids: MutableSet<Int> = mutableSetOf()

  /** 正在预载中的 sid。 */
  private val prefetchingSids: MutableSet<Int> = mutableSetOf()

  /** UI toast 事件。 */
  private val toastMessagesMutable = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val toastMessages: SharedFlow<String> = toastMessagesMutable.asSharedFlow()

  init {
    initialize()
  }

  /** 初始化当前详情游标。 */
  private fun initialize() {
    log.i { "初始化投稿详情页 -> 开始(initialSid=$initialSid)" }
    holder.setCurrentBySid(initialSid)
    nextPageUrl = holder.nextPageUrl()
    refreshState()
    loadCurrentAndSchedulePrefetch()
    appendIfNearEnd(force = false)
  }

  /** 上一条。 */
  fun previous() {
    holder.setCurrentIndex(holder.currentIndex() - 1)
    refreshState()
    loadCurrentAndSchedulePrefetch()
  }

  /** 下一条。 */
  fun next() {
    if (holder.next() != null) {
      holder.setCurrentIndex(holder.currentIndex() + 1)
      refreshState()
      loadCurrentAndSchedulePrefetch()
      appendIfNearEnd(force = false)
      return
    }
    appendIfNearEnd(force = true)
  }

  /**
   * 响应 pager 页面变更。
   *
   * @param index 目标索引。
   */
  fun onPageChanged(index: Int) {
    holder.setCurrentIndex(index)
    refreshState()
    loadCurrentAndSchedulePrefetch()
    appendIfNearEnd(force = false)
  }

  /** 记录当前 submission 页面滚动偏移。 */
  fun setCurrentPageScrollOffset(sid: Int, offset: Int) {
    val normalized = offset.coerceAtLeast(0)
    if (scrollOffsetBySid[sid] == normalized) return
    scrollOffsetBySid[sid] = normalized
  }

  /** 查询某个 sid 当前记住的滚动偏移。 */
  fun scrollOffsetForSid(sid: Int): Int = scrollOffsetBySid[sid] ?: 0

  /** 查询某个 sid 当前的回顶版本。 */
  fun scrollToTopVersionForSid(sid: Int): Long = scrollToTopVersionBySid[sid] ?: 0L

  /** 请求当前页回顶。 */
  fun requestCurrentPageScrollToTop() {
    val current = holder.current() ?: return
    val sid = current.id
    scrollOffsetBySid[sid] = 0
    scrollToTopVersionBySid[sid] = (scrollToTopVersionBySid[sid] ?: 0L) + 1L
    refreshState()
  }

  /** 重试当前详情页。 */
  fun retryCurrentDetail() {
    val current = holder.current() ?: return
    log.i { "重试详情加载 -> sid=${current.id}" }
    loadDetail(current = current, force = true)
  }

  /** 触发当前投稿描述翻译。 */
  fun translateDescriptionCurrent() {
    translateCurrent(target = SubmissionTranslationTarget.DESCRIPTION)
  }

  /** 触发当前投稿附件文本翻译。 */
  fun translateAttachmentCurrent() {
    translateCurrent(target = SubmissionTranslationTarget.ATTACHMENT)
  }

  /** 解析当前投稿附件文本。 */
  fun loadAttachmentTextCurrent() {
    val current = holder.current() ?: return
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
                      message = "缺少附件下载地址",
                  ),
              attachmentTranslationState = null,
          )
      refreshState()
      return
    }

    cancelTranslationJob(sid, SubmissionTranslationTarget.ATTACHMENT)
    detailBySid[sid] =
        currentState.copy(
            attachmentTextState =
                SubmissionAttachmentTextUiState.Loading(
                    fileName = fileName,
                    progress = initialAttachmentTextProgress(fileName),
                ),
            attachmentTranslationState = null,
        )
    refreshState()

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
                    refreshState()
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

            PageState.CfChallenge ->
                latestState.copy(
                    attachmentTextState =
                        SubmissionAttachmentTextUiState.Error(
                            fileName = fileName,
                            message = "需要 Cloudflare 验证",
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
      refreshState()
    }
  }

  /** 收藏/取消收藏当前投稿（乐观更新）。 */
  fun toggleFavoriteCurrent() {
    val current = holder.current() ?: return
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
    refreshState()

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

            PageState.CfChallenge -> {
              detailBySid[sid] =
                  currentState.copy(
                      detail =
                          optimisticDetail.copy(
                              favoriteActionUrl = guessNextFavoriteActionUrl(actionUrl)
                          ),
                      favoriteUpdating = false,
                      favoriteErrorMessage = "收藏已提交，但状态同步需要 Cloudflare 验证",
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
                      favoriteErrorMessage = "收藏已提交，但状态同步失败：${refreshed.reason}",
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
                          "收藏已提交，但状态同步失败：${refreshed.exception.message ?: refreshed.exception}",
                  )
              log.e(refreshed.exception) { "收藏操作 -> 已提交, 但刷新失败(sid=$sid)" }
            }

            PageState.Loading -> Unit
          }
        }

        PageState.CfChallenge -> {
          detailBySid[sid] =
              currentState.copy(
                  detail = originalDetail,
                  favoriteUpdating = false,
                  favoriteErrorMessage = "需要 Cloudflare 验证",
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
      refreshState()
    }
  }

  /** 屏蔽当前投稿中的指定标签。 */
  fun blockKeywordCurrent(tagName: String) {
    val normalizedTagName = tagName.trim()
    if (normalizedTagName.isBlank()) {
      log.w { "标签屏蔽 -> 跳过(空标签)" }
      return
    }
    val current = holder.current() ?: return
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
            refreshState()

            val refreshedNonce = refreshed.data.tagBlockNonce.trim()
            if (refreshedNonce.isBlank()) {
              detailBySid[sid] = refreshedState.copy(favoriteErrorMessage = "当前页面缺少标签屏蔽凭据")
              refreshState()
              emitToast("缺少标签屏蔽凭据")
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

          PageState.CfChallenge -> {
            detailBySid[sid] = currentState.copy(favoriteErrorMessage = "刷新详情失败：需要 Cloudflare 验证")
            refreshState()
            emitToast("刷新详情失败：需要 Cloudflare 验证")
            log.w { "标签屏蔽 -> 刷新详情需Cloudflare验证(sid=$sid)" }
          }

          is PageState.MatureBlocked -> {
            detailBySid[sid] =
                currentState.copy(favoriteErrorMessage = "刷新详情失败：${refreshed.reason}")
            refreshState()
            emitToast("刷新详情失败：${refreshed.reason}")
            log.w { "标签屏蔽 -> 刷新详情受限(sid=$sid,reason=${refreshed.reason})" }
          }

          is PageState.Error -> {
            val message = refreshed.exception.message ?: refreshed.exception.toString()
            detailBySid[sid] = currentState.copy(favoriteErrorMessage = "刷新详情失败：$message")
            refreshState()
            emitToast("刷新详情失败：$message")
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
        emitToast(if (toAdd) "已屏蔽标签：$tagName" else "已解除屏蔽：$tagName")
        log.i { "标签屏蔽 -> 成功(sid=$sid,tag=$tagName,toAdd=$toAdd)" }
      }

      PageState.CfChallenge -> {
        val latest = detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
        detailBySid[sid] = latest.copy(favoriteErrorMessage = "需要 Cloudflare 验证")
        emitToast("屏蔽失败：需要 Cloudflare 验证")
        log.w { "标签屏蔽 -> Cloudflare验证(sid=$sid,tag=$tagName)" }
      }

      is PageState.MatureBlocked -> {
        val latest = detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
        detailBySid[sid] = latest.copy(favoriteErrorMessage = blocked.reason)
        emitToast("屏蔽失败：${blocked.reason}")
        log.w { "标签屏蔽 -> 受限(sid=$sid,tag=$tagName,reason=${blocked.reason})" }
      }

      is PageState.Error -> {
        val latest = detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
        detailBySid[sid] =
            latest.copy(
                favoriteErrorMessage = blocked.exception.message ?: blocked.exception.toString()
            )
        emitToast("屏蔽失败：${blocked.exception.message ?: blocked.exception}")
        log.e(blocked.exception) { "标签屏蔽 -> 失败(sid=$sid,tag=$tagName)" }
      }

      PageState.Loading -> Unit
    }
    refreshState()
  }

  /** 手动重试加载更多。 */
  fun retryLoadMore() {
    log.d { "自动加载投稿列表 -> 手动重试" }
    appendIfNearEnd(force = true)
  }

  /** 重建 UI 状态。 */
  private fun refreshState() {
    val size = holder.size()
    if (size == 0) {
      mutableState.value = SubmissionPagerUiState.Empty
      mutablePageState.value = PageState.Error(IllegalStateException("当前没有可浏览内容。"))
      return
    }

    val index = holder.currentIndex()
    val items = buildList {
      for (cursor in 0 until size) {
        val item = holder.getAt(cursor) ?: continue
        add(item)
      }
    }

    val uiState =
        SubmissionPagerUiState.Data(
            submissions = items,
            detailBySid = detailBySid.toMap(),
            scrollToTopVersionBySid = scrollToTopVersionBySid.toMap(),
            currentIndex = index.coerceIn(0, (items.lastIndex).coerceAtLeast(0)),
            hasPrevious = holder.previous() != null,
            hasNext = holder.next() != null || !nextPageUrl.isNullOrBlank(),
            hasMore = !nextPageUrl.isNullOrBlank(),
            isLoadingMore = isLoadingMore,
            appendErrorMessage = appendErrorMessage,
        )
    mutableState.value = uiState
    mutablePageState.value = PageState.Success(uiState)
  }

  /** 加载当前投稿详情，并按窗口策略调度预载。 */
  private fun loadCurrentAndSchedulePrefetch() {
    val current = holder.current() ?: return
    loadDetail(current = current, force = false)
    scheduleDetailPrefetch()
  }

  /** 预载窗口：next 1..3 + previous 1，统一 500ms 节流。 */
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
                    refreshState()
                  }

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
    val totalSize = holder.size()
    if (totalSize == 0) return emptyList()
    val targetIndices =
        computeSubmissionPrefetchIndices(
            currentIndex = holder.currentIndex(),
            lastIndex = totalSize - 1,
        )
    return targetIndices.mapNotNull { index -> holder.getAt(index)?.id }
  }

  private fun guessNextFavoriteActionUrl(currentActionUrl: String): String {
    val url = currentActionUrl.trim()
    if (url.isBlank()) return url
    return when {
      url.contains("/unfav/") -> url.replaceFirst("/unfav/", "/fav/")
      url.contains("/fav/") -> url.replaceFirst("/fav/", "/unfav/")
      else -> url
    }
  }

  private fun emitToast(message: String) {
    toastMessagesMutable.tryEmit(message)
  }

  private fun toBlockedKeywordSet(detail: Submission): Set<String> =
      detail.blockedTagNames.map(::normalizeTagKey).filter { it.isNotBlank() }.toSet()

  private fun translateCurrent(target: SubmissionTranslationTarget) {
    val current = holder.current() ?: return
    val sid = current.id
    val currentState = detailBySid[sid] as? SubmissionDetailUiState.Success ?: return
    val translationState = currentState.translationStateOf(target) ?: return
    if (translationState.translating || translationState.sourceBlocks.isEmpty()) return

    val jobKey = SubmissionTranslationJobKey(sid = sid, target = target)
    cancelTranslationJob(sid, target)

    val pendingState = translationState.toPendingState()
    detailBySid[sid] = currentState.withTranslationState(target = target, state = pendingState)
    refreshState()

    val job =
        screenModelScope.launch {
          try {
            translationService.translateBlocks(pendingState.sourceBlocks) { index, result ->
              val latestState =
                  detailBySid[sid] as? SubmissionDetailUiState.Success ?: return@translateBlocks
              val latestTranslationState =
                  latestState.translationStateOf(target) ?: return@translateBlocks
              if (latestTranslationState.sourceKey != pendingState.sourceKey) return@translateBlocks

              val updatedState =
                  latestTranslationState.withBlockResult(
                      index = index,
                      result = result,
                  )
              detailBySid[sid] =
                  latestState.withTranslationState(target = target, state = updatedState)
              refreshState()
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
                      state = latestTranslationState.markPendingBlocksAsFailed(),
                  )
              refreshState()
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
                      state = latestTranslationState.copy(translating = false),
                  )
              refreshState()
            }
          }
        }
    translationJobs[jobKey] = job
  }

  private fun cancelTranslationJob(sid: Int, target: SubmissionTranslationTarget) {
    val key = SubmissionTranslationJobKey(sid = sid, target = target)
    translationJobs.remove(key)?.cancel()
  }

  /** 加载当前页附近的下一页数据。 */
  private fun appendIfNearEnd(force: Boolean) {
    val currentSize = holder.size()
    if (currentSize <= 0) return
    val isNearEnd = holder.currentIndex() >= currentSize - 1 - pagerAppendThreshold
    if (!force && !isNearEnd) return

    val targetNextPageUrl = nextPageUrl ?: return
    log.d { "自动加载投稿列表 -> 触发检查(force=$force,current=${holder.currentIndex()},size=$currentSize)" }
    if (appendJob?.isActive == true) {
      log.d { "自动加载投稿列表 -> 跳过(已有追加任务)" }
      return
    }
    val snapshot = toPaginationSnapshot()
    if (!paginationStateMachine.canLoadMore(snapshot, force = force)) {
      log.d { "自动加载投稿列表 -> 跳过(条件未满足)" }
      return
    }
    log.d { "自动加载投稿列表 -> 开始(force=$force)" }

    applyPaginationSnapshot(paginationStateMachine.beginAppend(snapshot))
    refreshState()

    appendJob =
        screenModelScope.launch {
          val next = feedSource.loadPageByNextUrl(targetNextPageUrl)
          val reduced =
              paginationStateMachine.reduceAppend(
                  snapshot = toPaginationSnapshot(),
                  result = next,
                  itemsOf = { page -> page.submissions },
                  nextPageUrlOf = { page -> page.nextPageUrl },
              )
          applyPaginationSnapshot(reduced)
          refreshState()
          when (next) {
            is PageState.Success -> {
              scheduleDetailPrefetch()
              log.d { "自动加载投稿列表 -> ${summarizePageState(next)}(count=${reduced.items.size})" }
            }

            PageState.CfChallenge -> log.w { "自动加载投稿列表 -> Cloudflare验证" }
            is PageState.MatureBlocked -> log.w { "自动加载投稿列表 -> 受限(${next.reason})" }
            is PageState.Error -> log.e(next.exception) { "自动加载投稿列表 -> 失败" }
            PageState.Loading -> log.d { "自动加载投稿列表 -> 加载中" }
          }
        }
  }

  private fun toPaginationSnapshot(): PaginationSnapshot<SubmissionThumbnail> =
      PaginationSnapshot(
          items =
              buildList {
                for (cursor in 0 until holder.size()) {
                  val item = holder.getAt(cursor) ?: continue
                  add(item)
                }
              },
          nextPageUrl = nextPageUrl,
          loading = false,
          refreshing = false,
          isLoadingMore = isLoadingMore,
          errorMessage = null,
          appendErrorMessage = appendErrorMessage,
      )

  private fun applyPaginationSnapshot(snapshot: PaginationSnapshot<SubmissionThumbnail>) {
    holder.replace(submissions = snapshot.items, nextPageUrl = snapshot.nextPageUrl)
    nextPageUrl = snapshot.nextPageUrl
    isLoadingMore = snapshot.isLoadingMore
    appendErrorMessage = snapshot.appendErrorMessage
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
    refreshState()

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

            PageState.CfChallenge -> {
              log.w { "加载投稿详情 -> Cloudflare验证(sid=$sid)" }
              SubmissionDetailUiState.Error("需要 Cloudflare 验证")
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
              SubmissionDetailUiState.Error("加载中断")
            }
          }
      detailBySid[sid] = detailState
      refreshState()
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
    )
  }
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

private fun resolveTranslationState(
    sourceHtml: String,
    sourceFileName: String?,
    previous: SubmissionTranslationUiState?,
    translationService: SubmissionDescriptionTranslationService,
): SubmissionTranslationUiState {
  val sourceBlocks = translationService.extractBlocks(sourceHtml)
  val sourceKey =
      buildTranslationSourceKey(sourceHtml = sourceHtml, sourceFileName = sourceFileName)
  if (previous?.sourceKey == sourceKey) return previous

  return SubmissionTranslationUiState(
      sourceKey = sourceKey,
      sourceHtml = sourceHtml,
      sourceBlocks = sourceBlocks,
      blocks = sourceBlocks.toIdleDisplayBlocks(),
      sourceFileName = sourceFileName,
  )
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

private fun buildTranslationSourceKey(sourceHtml: String, sourceFileName: String?): String =
    listOfNotNull(sourceFileName?.trim()?.takeIf { it.isNotBlank() }, sourceHtml)
        .joinToString("\n@@\n")

private fun List<SubmissionDescriptionBlock>.toIdleDisplayBlocks():
    List<SubmissionDescriptionDisplayBlock> = map { block ->
  SubmissionDescriptionDisplayBlock(
      originalHtml = block.originalHtml,
      translated = null,
      status = SubmissionDescriptionTranslationStatus.IDLE,
  )
}

private fun SubmissionTranslationUiState.toPendingState(): SubmissionTranslationUiState =
    copy(
        blocks =
            sourceBlocks.map { block ->
              SubmissionDescriptionDisplayBlock(
                  originalHtml = block.originalHtml,
                  translated = null,
                  status = SubmissionDescriptionTranslationStatus.PENDING,
              )
            },
        translating = true,
        hasTriggered = true,
    )

private fun SubmissionTranslationUiState.withBlockResult(
    index: Int,
    result: SubmissionDescriptionBlockResult,
): SubmissionTranslationUiState =
    copy(
        blocks =
            blocks.mapIndexed { currentIndex, block ->
              if (currentIndex != index) {
                block
              } else {
                when (result) {
                  is SubmissionDescriptionBlockResult.Success ->
                      block.copy(
                          translated = result.translatedText,
                          status = SubmissionDescriptionTranslationStatus.SUCCESS,
                      )

                  SubmissionDescriptionBlockResult.EmptyResult ->
                      block.copy(
                          translated = null,
                          status = SubmissionDescriptionTranslationStatus.EMPTY,
                      )

                  is SubmissionDescriptionBlockResult.Failure ->
                      block.copy(
                          translated = null,
                          status = SubmissionDescriptionTranslationStatus.FAILURE,
                      )
                }
              }
            }
    )

private fun SubmissionTranslationUiState.markPendingBlocksAsFailed(): SubmissionTranslationUiState =
    copy(
        blocks =
            blocks.map { block ->
              if (block.status == SubmissionDescriptionTranslationStatus.PENDING) {
                block.copy(status = SubmissionDescriptionTranslationStatus.FAILURE)
              } else {
                block
              }
            },
        translating = false,
    )

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

private fun initialAttachmentTextProgress(fileName: String): AttachmentTextProgress =
    AttachmentTextProgress(
        overallFraction = 0f,
        stageIndex = 1,
        stageCount = 1,
        stageId = "download_attachment",
        stageLabel = "下载附件",
        stageFraction = 0f,
        message = "准备下载附件",
        currentItemLabel = fileName,
    )
