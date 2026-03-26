package me.domino.fa2.ui.pages.feed

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import fa2.shared.generated.resources.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.repository.FeedRepository
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.i18n.SystemLanguageProvider
import me.domino.fa2.i18n.appString
import me.domino.fa2.ui.navigation.SubmissionListHolder
import me.domino.fa2.ui.state.PaginationSnapshot
import me.domino.fa2.ui.state.PaginationStateMachine
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizePageState

private const val autoLoadThreshold = 10

/** Feed 页 UI 状态。 */
data class FeedUiState(
    val submissions: List<SubmissionThumbnail> = emptyList(),
    val nextPageUrl: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val appendErrorMessage: String? = null,
) {
  val hasMore: Boolean
    get() = !nextPageUrl.isNullOrBlank()
}

/** Feed 页面状态模型。 负责分页聚合、加载状态管理与共享列表同步。 */
class FeedScreenModel(
    /** Feed 仓储。 */
    private val repository: FeedRepository,
    /** 投稿列表共享持有器。 */
    private val submissionListHolder: SubmissionListHolder,
    private val settingsService: AppSettingsService? = null,
    private val systemLanguageProvider: SystemLanguageProvider? = null,
) : StateScreenModel<FeedUiState>(FeedUiState()) {
  private val log = FaLog.withTag("FeedScreenModel")
  private val paginationStateMachine =
      PaginationStateMachine<SubmissionThumbnail, Int>(
          keyOf = { item -> item.id },
          challengeMessage = { appString(Res.string.cloudflare_challenge_title) },
          appendFallbackErrorMessage = { appString(Res.string.load_failed_please_retry) },
      )

  private val mutablePageState = MutableStateFlow<PageState<FeedUiState>>(PageState.Loading)
  val pageState: StateFlow<PageState<FeedUiState>> = mutablePageState.asStateFlow()

  /** 当前首页加载任务。 */
  private var loadJob: Job? = null

  /** 当前追加任务。 */
  private var appendJob: Job? = null

  /**
   * 加载首页。
   *
   * @param forceRefresh 是否强制刷新。
   */
  fun load(forceRefresh: Boolean = false) {
    log.i { "加载Feed -> 开始(forceRefresh=$forceRefresh)" }
    if (loadJob?.isActive == true) {
      log.d { "加载Feed -> 跳过(已有任务)" }
      return
    }
    val current = state.value
    if (!forceRefresh && current.submissions.isNotEmpty()) {
      log.d { "加载Feed -> 跳过(已有数据)" }
      return
    }

    mutableState.value =
        current.applyPagination(
            paginationStateMachine.beginLoad(
                snapshot = current.toPaginationSnapshot(),
                forceRefresh = forceRefresh,
            )
        )
    if (current.submissions.isEmpty()) {
      mutablePageState.value = PageState.Loading
    }

    loadJob =
        screenModelScope.launch {
          val firstPageState =
              if (forceRefresh) {
                repository.refreshFirstPage()
              } else {
                repository.loadFirstPage()
              }
          val reduced =
              paginationStateMachine.reduceFirstPage(
                  snapshot = state.value.toPaginationSnapshot(),
                  result = firstPageState,
                  itemsOf = { page -> page.submissions },
                  nextPageUrlOf = { page -> page.nextPageUrl },
              )
          val updated = state.value.applyPagination(reduced)
          mutableState.value = updated
          when (val next = firstPageState) {
            is PageState.Success -> {
              syncSubmissionListHolder(updated)
              mutablePageState.value = PageState.Success(updated)
              log.i { "加载Feed -> 成功(count=${updated.submissions.size})" }
            }

            PageState.CfChallenge -> {
              mutablePageState.value = PageState.CfChallenge
              log.w { "加载Feed -> Cloudflare验证" }
            }

            is PageState.MatureBlocked -> {
              mutablePageState.value = PageState.MatureBlocked(next.reason)
              log.w { "加载Feed -> 受限(${next.reason})" }
            }

            is PageState.Error -> {
              mutablePageState.value = PageState.Error(next.exception)
              log.e(next.exception) { "加载Feed -> 失败" }
            }

            PageState.Loading -> {
              mutablePageState.value = PageState.Loading
              log.d { "加载Feed -> 加载中" }
            }
          }
        }
  }

  /** 刷新首页。 */
  fun refresh() {
    load(forceRefresh = true)
  }

  /**
   * 触底回调：只负责上报当前可见位置，是否加载由 ViewModel 判断。
   *
   * @param lastVisibleIndex 当前最大可见项索引。
   */
  fun onLastVisibleIndexChanged(lastVisibleIndex: Int) {
    val snapshot = state.value
    if (snapshot.submissions.isEmpty()) return
    val thresholdIndex = snapshot.submissions.lastIndex - autoLoadThreshold
    log.d {
      "自动加载Feed -> 触发检查(last=$lastVisibleIndex,threshold=$thresholdIndex,total=${snapshot.submissions.size})"
    }
    if (lastVisibleIndex > snapshot.submissions.lastIndex - autoLoadThreshold) {
      loadMore(force = false)
    }
  }

  /** 手动重试追加。 */
  fun retryLoadMore() {
    loadMore(force = true)
  }

  /**
   * 按 sid 设置当前详情索引。
   *
   * @param sid 投稿 ID。
   */
  fun setCurrentSubmission(sid: Int) {
    submissionListHolder.setCurrentBySid(sid)
  }

  /**
   * 追加下一页。
   *
   * @param force 是否忽略 appendError 直接重试。
   */
  private fun loadMore(force: Boolean) {
    val snapshot = state.value
    val nextUrl = snapshot.nextPageUrl
    if (nextUrl.isNullOrBlank()) {
      log.d { "自动加载Feed -> 跳过(无下一页)" }
      return
    }
    if (appendJob?.isActive == true) {
      log.d { "自动加载Feed -> 跳过(已有追加任务)" }
      return
    }
    if (!paginationStateMachine.canLoadMore(snapshot.toPaginationSnapshot(), force = force)) {
      log.d { "自动加载Feed -> 跳过(条件未满足)" }
      return
    }

    log.d { "自动加载Feed -> 开始(force=$force)" }

    mutableState.value =
        snapshot.applyPagination(
            paginationStateMachine.beginAppend(snapshot.toPaginationSnapshot())
        )

    appendJob =
        screenModelScope.launch {
          val pageState = repository.loadPageByNextUrl(nextUrl)
          val reduced =
              paginationStateMachine.reduceAppend(
                  snapshot = state.value.toPaginationSnapshot(),
                  result = pageState,
                  itemsOf = { page -> page.submissions },
                  nextPageUrlOf = { page -> page.nextPageUrl },
              )
          val updated = state.value.applyPagination(reduced)
          mutableState.value = updated
          when (val next = pageState) {
            is PageState.Success -> {
              syncSubmissionListHolder(updated)
              mutablePageState.value = PageState.Success(updated)
              log.d { "自动加载Feed -> ${summarizePageState(next)}(count=${updated.submissions.size})" }
            }

            PageState.CfChallenge -> log.w { "自动加载Feed -> Cloudflare验证" }
            is PageState.MatureBlocked -> log.w { "自动加载Feed -> 受限(${next.reason})" }
            is PageState.Error -> log.e(next.exception) { "自动加载Feed -> 失败" }
            PageState.Loading -> log.d { "自动加载Feed -> 加载中" }
          }
        }
  }

  private fun syncSubmissionListHolder(state: FeedUiState) {
    submissionListHolder.replace(
        submissions = state.submissions,
        nextPageUrl = state.nextPageUrl,
    )
  }
}

private fun FeedUiState.toPaginationSnapshot(): PaginationSnapshot<SubmissionThumbnail> =
    PaginationSnapshot(
        items = submissions,
        nextPageUrl = nextPageUrl,
        loading = loading,
        refreshing = refreshing,
        isLoadingMore = isLoadingMore,
        errorMessage = errorMessage,
        appendErrorMessage = appendErrorMessage,
    )

private fun FeedUiState.applyPagination(
    snapshot: PaginationSnapshot<SubmissionThumbnail>
): FeedUiState =
    copy(
        submissions = snapshot.items,
        nextPageUrl = snapshot.nextPageUrl,
        loading = snapshot.loading,
        refreshing = snapshot.refreshing,
        isLoadingMore = snapshot.isLoadingMore,
        errorMessage = snapshot.errorMessage,
        appendErrorMessage = snapshot.appendErrorMessage,
    )
