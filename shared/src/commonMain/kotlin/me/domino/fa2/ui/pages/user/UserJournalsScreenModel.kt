package me.domino.fa2.ui.pages.user

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import fa2.shared.generated.resources.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.JournalSummary
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.summarizeResourceResult
import me.domino.fa2.data.remote.fa.journal.JournalsRepository
import me.domino.fa2.ui.i18n.appString
import me.domino.fa2.ui.utils.PaginationReducer
import me.domino.fa2.ui.utils.PaginationSnapshot
import me.domino.fa2.utils.logging.FaLog

private const val journalsAutoLoadThreshold = 6

/** Journals 子页状态。 */
data class UserJournalsUiState(
    /** 当前日志列表。 */
    val journals: List<JournalSummary> = emptyList(),
    /** 下一页 URL。 */
    val nextPageUrl: String? = null,
    /** 是否加载中。 */
    val loading: Boolean = false,
    /** 是否刷新中。 */
    val refreshing: Boolean = false,
    /** 是否正在加载更多。 */
    val isLoadingMore: Boolean = false,
    /** 首页错误。 */
    val errorMessage: String? = null,
    /** 追加错误。 */
    val appendErrorMessage: String? = null,
) {
  /** 是否有更多。 */
  val hasMore: Boolean
    get() = !nextPageUrl.isNullOrBlank()
}

/** Journals 子页状态模型。 */
class UserJournalsScreenModel(
    /** 用户名。 */
    private val username: String,
    /** Journals 仓储。 */
    private val repository: JournalsRepository,
) : StateScreenModel<UserJournalsUiState>(UserJournalsUiState()) {
  private val log = FaLog.withTag("UserJournalsScreenModel")
  private val paginationStateMachine =
      PaginationReducer<JournalSummary, Int>(
          keyOf = { item -> item.id },
          challengeMessage = { appString(Res.string.cloudflare_challenge_title) },
      )
  private var loadJob: Job? = null
  private var appendJob: Job? = null

  init {
    load()
  }

  /** 加载首页。 */
  fun load(forceRefresh: Boolean = false) {
    log.i { "加载Journals页 -> 开始(user=$username,forceRefresh=$forceRefresh)" }
    if (loadJob?.isActive == true) {
      log.d { "加载Journals页 -> 跳过(已有任务)" }
      return
    }
    val snapshot = state.value
    if (!forceRefresh && snapshot.journals.isNotEmpty()) {
      log.d { "加载Journals页 -> 跳过(已有数据)" }
      return
    }

    mutableState.value =
        snapshot.applyPagination(
            paginationStateMachine.beginLoad(
                snapshot = snapshot.toPaginationSnapshot(),
                forceRefresh = forceRefresh,
            )
        )

    loadJob =
        screenModelScope.launch {
          val firstPageState =
              if (forceRefresh) {
                repository.refreshJournalsFirstPage(username)
              } else {
                repository.loadJournalsPage(username, nextPageUrl = null)
              }
          val reduced =
              paginationStateMachine.reduceFirstPage(
                  snapshot = state.value.toPaginationSnapshot(),
                  result = firstPageState,
                  itemsOf = { page -> page.journals },
                  nextPageUrlOf = { page -> page.nextPageUrl },
              )
          val updated = state.value.applyPagination(reduced)
          mutableState.value = updated
          when (val next = firstPageState) {
            is ResourceResult.Success -> {
              log.i {
                "加载Journals页 -> ${summarizeResourceResult(next)}(count=${updated.journals.size})"
              }
            }

            is ResourceResult.AuthRequired -> log.w { "加载Journals页 -> 需要重新登录" }
            ResourceResult.CfChallenge -> log.w { "加载Journals页 -> Cloudflare验证" }
            is ResourceResult.MatureBlocked -> log.w { "加载Journals页 -> 受限(${next.reason})" }
            is ResourceResult.Error -> log.e(next.exception) { "加载Journals页 -> 失败" }
          }
        }
  }

  /** 触底回调。 */
  fun onLastVisibleIndexChanged(lastVisibleIndex: Int) {
    val snapshot = state.value
    if (snapshot.journals.isEmpty()) return
    log.d { "自动加载Journals页 -> 触发检查(last=$lastVisibleIndex,total=${snapshot.journals.size})" }
    if (lastVisibleIndex > snapshot.journals.lastIndex - journalsAutoLoadThreshold) {
      loadMore(force = false)
    }
  }

  /** 手动重试加载更多。 */
  fun retryLoadMore() {
    loadMore(force = true)
  }

  private fun loadMore(force: Boolean) {
    val snapshot = state.value
    val nextUrl = snapshot.nextPageUrl ?: return
    if (appendJob?.isActive == true) {
      log.d { "自动加载Journals页 -> 跳过(已有追加任务)" }
      return
    }
    if (!paginationStateMachine.canLoadMore(snapshot.toPaginationSnapshot(), force = force)) {
      log.d { "自动加载Journals页 -> 跳过(条件未满足)" }
      return
    }
    log.d { "自动加载Journals页 -> 开始(force=$force)" }

    mutableState.value =
        snapshot.applyPagination(
            paginationStateMachine.beginAppend(snapshot.toPaginationSnapshot())
        )

    appendJob =
        screenModelScope.launch {
          val next = repository.loadJournalsPage(username, nextPageUrl = nextUrl)
          val reduced =
              paginationStateMachine.reduceAppend(
                  snapshot = state.value.toPaginationSnapshot(),
                  result = next,
                  itemsOf = { page -> page.journals },
                  nextPageUrlOf = { page -> page.nextPageUrl },
              )
          val updated = state.value.applyPagination(reduced)
          mutableState.value = updated
          when (next) {
            is ResourceResult.Success -> {
              log.d {
                "自动加载Journals页 -> ${summarizeResourceResult(next)}(count=${updated.journals.size})"
              }
            }

            is ResourceResult.AuthRequired -> log.w { "自动加载Journals页 -> 需要重新登录" }
            ResourceResult.CfChallenge -> log.w { "自动加载Journals页 -> Cloudflare验证" }
            is ResourceResult.MatureBlocked -> log.w { "自动加载Journals页 -> 受限(${next.reason})" }
            is ResourceResult.Error -> log.e(next.exception) { "自动加载Journals页 -> 失败" }
          }
        }
  }
}

private fun UserJournalsUiState.toPaginationSnapshot(): PaginationSnapshot<JournalSummary> =
    PaginationSnapshot(
        items = journals,
        nextPageUrl = nextPageUrl,
        loading = loading,
        refreshing = refreshing,
        isLoadingMore = isLoadingMore,
        errorMessage = errorMessage,
        appendErrorMessage = appendErrorMessage,
    )

private fun UserJournalsUiState.applyPagination(
    snapshot: PaginationSnapshot<JournalSummary>
): UserJournalsUiState =
    copy(
        journals = snapshot.items,
        nextPageUrl = snapshot.nextPageUrl,
        loading = snapshot.loading,
        refreshing = snapshot.refreshing,
        isLoadingMore = snapshot.isLoadingMore,
        errorMessage = snapshot.errorMessage,
        appendErrorMessage = snapshot.appendErrorMessage,
    )
