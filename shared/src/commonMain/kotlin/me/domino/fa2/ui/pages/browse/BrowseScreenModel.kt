package me.domino.fa2.ui.pages.browse

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
import me.domino.fa2.data.repository.BrowseRepository
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.i18n.SystemLanguageProvider
import me.domino.fa2.i18n.appString
import me.domino.fa2.ui.state.PaginationReducer
import me.domino.fa2.ui.state.PaginationSnapshot
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizePageState

private const val browseAutoLoadThreshold = 10

data class BrowseFilterState(
    val category: Int = 1,
    val type: Int = 1,
    val species: Int = 1,
    val gender: String = "",
    val ratingGeneral: Boolean = true,
    val ratingMature: Boolean = true,
    val ratingAdult: Boolean = true,
)

data class BrowseUiState(
    val draftFilter: BrowseFilterState = BrowseFilterState(),
    val appliedFilter: BrowseFilterState = BrowseFilterState(),
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

/** Browse 页面状态模型。 */
class BrowseScreenModel(
    private val repository: BrowseRepository,
    private val settingsService: AppSettingsService? = null,
    private val systemLanguageProvider: SystemLanguageProvider? = null,
) : StateScreenModel<BrowseUiState>(BrowseUiState()) {
  private val log = FaLog.withTag("BrowseScreenModel")
  private val paginationStateMachine =
      PaginationReducer<SubmissionThumbnail, Int>(
          keyOf = { item -> item.id },
          challengeMessage = { appString(Res.string.cloudflare_challenge_title) },
          appendFallbackErrorMessage = { appString(Res.string.load_failed_please_retry) },
      )
  private val mutablePageState = MutableStateFlow<PageState<BrowseUiState>>(PageState.Loading)
  val pageState: StateFlow<PageState<BrowseUiState>> = mutablePageState.asStateFlow()
  private var loadJob: Job? = null
  private var appendJob: Job? = null

  init {
    load(forceRefresh = false)
  }

  fun updateCategory(category: Int) {
    mutableState.value =
        state.value.copy(draftFilter = state.value.draftFilter.copy(category = category))
  }

  fun updateType(type: Int) {
    mutableState.value = state.value.copy(draftFilter = state.value.draftFilter.copy(type = type))
  }

  fun updateSpecies(species: Int) {
    mutableState.value =
        state.value.copy(draftFilter = state.value.draftFilter.copy(species = species))
  }

  fun updateGender(gender: String) {
    mutableState.value =
        state.value.copy(draftFilter = state.value.draftFilter.copy(gender = gender))
  }

  fun setRatingGeneral(enabled: Boolean) {
    mutableState.value =
        state.value.copy(draftFilter = state.value.draftFilter.copy(ratingGeneral = enabled))
  }

  fun setRatingMature(enabled: Boolean) {
    mutableState.value =
        state.value.copy(draftFilter = state.value.draftFilter.copy(ratingMature = enabled))
  }

  fun setRatingAdult(enabled: Boolean) {
    mutableState.value =
        state.value.copy(draftFilter = state.value.draftFilter.copy(ratingAdult = enabled))
  }

  fun applyFilter() {
    log.i { "应用Browse筛选 -> 开始" }
    mutableState.value = state.value.copy(appliedFilter = state.value.draftFilter)
    load(forceRefresh = true)
  }

  fun applyFilter(filter: BrowseFilterState) {
    log.i { "应用Browse筛选 -> 使用外部筛选" }
    mutableState.value = state.value.copy(draftFilter = filter, appliedFilter = filter)
    load(forceRefresh = true)
  }

  fun refresh() {
    log.i { "刷新Browse -> 开始" }
    load(forceRefresh = true)
  }

  fun onLastVisibleIndexChanged(lastVisibleIndex: Int) {
    val snapshot = state.value
    if (snapshot.submissions.isEmpty()) return
    log.d { "自动加载Browse -> 触发检查(last=$lastVisibleIndex,total=${snapshot.submissions.size})" }
    if (lastVisibleIndex > snapshot.submissions.lastIndex - browseAutoLoadThreshold) {
      loadMore(force = false)
    }
  }

  fun retryLoadMore() {
    loadMore(force = true)
  }

  private fun load(forceRefresh: Boolean) {
    log.i { "加载Browse -> 开始(forceRefresh=$forceRefresh)" }
    if (loadJob?.isActive == true) return
    val snapshot = state.value
    mutableState.value =
        snapshot.applyPagination(
            paginationStateMachine.beginLoad(
                snapshot = snapshot.toPaginationSnapshot(),
                forceRefresh = forceRefresh,
            )
        )
    if (snapshot.submissions.isEmpty()) {
      mutablePageState.value = PageState.Loading
    }
    val firstPageUrl = buildBrowseUrl(snapshot.appliedFilter)
    loadJob =
        screenModelScope.launch {
          val pageState =
              if (forceRefresh) {
                repository.refreshPage(firstPageUrl)
              } else {
                repository.loadPage(firstPageUrl)
              }
          val reduced =
              paginationStateMachine.reduceFirstPage(
                  snapshot = state.value.toPaginationSnapshot(),
                  result = pageState,
                  itemsOf = { page -> page.submissions },
                  nextPageUrlOf = { page -> page.nextPageUrl },
              )
          val updated = state.value.applyPagination(reduced)
          mutableState.value = updated
          when (pageState) {
            is PageState.Success -> {
              mutablePageState.value = PageState.Success(updated)
              log.i { "加载Browse -> 成功(count=${updated.submissions.size})" }
            }

            is PageState.AuthRequired -> {
              mutablePageState.value =
                  PageState.AuthRequired(pageState.requestUrl, pageState.message)
              log.w { "加载Browse -> 需要重新登录" }
            }

            PageState.CfChallenge -> {
              mutablePageState.value = PageState.CfChallenge
              log.w { "加载Browse -> Cloudflare验证" }
            }

            is PageState.MatureBlocked -> {
              mutablePageState.value = PageState.MatureBlocked(pageState.reason)
              log.w { "加载Browse -> 受限(${pageState.reason})" }
            }

            is PageState.Error -> {
              mutablePageState.value = PageState.Error(pageState.exception)
              log.e(pageState.exception) { "加载Browse -> 失败" }
            }

            PageState.Loading -> {
              mutablePageState.value = PageState.Loading
              log.d { "加载Browse -> 加载中" }
            }
          }
        }
  }

  private fun loadMore(force: Boolean) {
    val snapshot = state.value
    val nextUrl = snapshot.nextPageUrl ?: return
    if (appendJob?.isActive == true) return
    if (!paginationStateMachine.canLoadMore(snapshot.toPaginationSnapshot(), force = force)) return
    log.d { "自动加载Browse -> 开始(force=$force)" }

    mutableState.value =
        snapshot.applyPagination(
            paginationStateMachine.beginAppend(snapshot.toPaginationSnapshot())
        )

    appendJob =
        screenModelScope.launch {
          val pageState = repository.loadPage(nextUrl)
          val reduced =
              paginationStateMachine.reduceAppend(
                  snapshot = state.value.toPaginationSnapshot(),
                  result = pageState,
                  itemsOf = { page -> page.submissions },
                  nextPageUrlOf = { page -> page.nextPageUrl },
              )
          val updated = state.value.applyPagination(reduced)
          mutableState.value = updated
          when (pageState) {
            is PageState.Success -> {
              mutablePageState.value = PageState.Success(updated)
              log.d {
                "自动加载Browse -> ${summarizePageState(pageState)}(count=${updated.submissions.size})"
              }
            }

            is PageState.AuthRequired -> log.w { "自动加载Browse -> 需要重新登录" }
            PageState.CfChallenge -> log.w { "自动加载Browse -> Cloudflare验证" }
            is PageState.MatureBlocked -> log.w { "自动加载Browse -> 受限(${pageState.reason})" }
            is PageState.Error -> log.e(pageState.exception) { "自动加载Browse -> 失败" }
            PageState.Loading -> log.d { "自动加载Browse -> 加载中" }
          }
        }
  }

  private fun buildBrowseUrl(filter: BrowseFilterState): String =
      FaUrls.browse(
          cat = filter.category,
          atype = filter.type,
          species = filter.species,
          gender = filter.gender,
          page = 1,
          ratingGeneral = filter.ratingGeneral,
          ratingMature = filter.ratingMature,
          ratingAdult = filter.ratingAdult,
      )
}

private fun BrowseUiState.toPaginationSnapshot(): PaginationSnapshot<SubmissionThumbnail> =
    PaginationSnapshot(
        items = submissions,
        nextPageUrl = nextPageUrl,
        loading = loading,
        refreshing = refreshing,
        isLoadingMore = isLoadingMore,
        errorMessage = errorMessage,
        appendErrorMessage = appendErrorMessage,
    )

private fun BrowseUiState.applyPagination(
    snapshot: PaginationSnapshot<SubmissionThumbnail>
): BrowseUiState =
    copy(
        submissions = snapshot.items,
        nextPageUrl = snapshot.nextPageUrl,
        loading = snapshot.loading,
        refreshing = snapshot.refreshing,
        isLoadingMore = snapshot.isLoadingMore,
        errorMessage = snapshot.errorMessage,
        appendErrorMessage = snapshot.appendErrorMessage,
    )
