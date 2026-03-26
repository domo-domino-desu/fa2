package me.domino.fa2.ui.pages.search

import co.touchlab.kermit.Logger
import fa2.shared.generated.resources.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.repository.ActivityHistoryRepository
import me.domino.fa2.data.repository.SearchRepository
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.taxonomy.FaTaxonomyRepository
import me.domino.fa2.i18n.AppI18nSnapshot
import me.domino.fa2.i18n.SystemLanguageProvider
import me.domino.fa2.i18n.appString
import me.domino.fa2.ui.navigation.SubmissionListHolder
import me.domino.fa2.ui.search.SearchUiLabelsRepository
import me.domino.fa2.ui.state.PaginationStateMachine
import me.domino.fa2.util.logging.summarizePageState

internal data class SearchAppliedRequest(
    val applied: SearchFormState,
    val firstUrl: String,
)

internal class SearchQueryStateCoordinator(
    private val stateProvider: () -> SearchUiState,
    private val stateSink: (SearchUiState) -> Unit,
) {
  fun openOverlay() {
    stateSink(stateProvider().copy(overlayVisible = true))
  }

  fun closeOverlay() {
    stateSink(stateProvider().copy(overlayVisible = false))
  }

  fun updateQuery(query: String) {
    val genders = parseGendersFromQuery(query)
    updateDraft { draft -> draft.copy(query = query, selectedGenders = genders) }
  }

  fun toggleGender(gender: SearchGender, checked: Boolean) {
    updateDraft { draft ->
      val updatedGenders =
          draft.selectedGenders
              .toMutableSet()
              .apply { if (checked) add(gender) else remove(gender) }
              .toSet()
      draft.copy(
          query = rewriteQueryWithGenders(query = draft.query, genders = updatedGenders),
          selectedGenders = updatedGenders,
      )
    }
  }

  fun updateCategory(category: Int) = updateDraft { draft -> draft.copy(category = category) }

  fun updateType(type: Int) = updateDraft { draft -> draft.copy(type = type) }

  fun updateSpecies(species: Int) = updateDraft { draft -> draft.copy(species = species) }

  fun updateOrderBy(orderBy: String) = updateDraft { draft -> draft.copy(orderBy = orderBy) }

  fun updateOrderDirection(orderDirection: String) = updateDraft { draft ->
    draft.copy(orderDirection = orderDirection)
  }

  fun updateRange(range: String) = updateDraft { draft -> draft.copy(range = range) }

  fun updateRangeFrom(value: String) = updateDraft { draft -> draft.copy(rangeFrom = value) }

  fun updateRangeTo(value: String) = updateDraft { draft -> draft.copy(rangeTo = value) }

  fun setRatingGeneral(enabled: Boolean) = updateDraft { draft ->
    draft.copy(ratingGeneral = enabled)
  }

  fun setRatingMature(enabled: Boolean) = updateDraft { draft ->
    draft.copy(ratingMature = enabled)
  }

  fun setRatingAdult(enabled: Boolean) = updateDraft { draft -> draft.copy(ratingAdult = enabled) }

  fun setTypeArt(enabled: Boolean) = updateDraft { draft -> draft.copy(typeArt = enabled) }

  fun setTypeMusic(enabled: Boolean) = updateDraft { draft -> draft.copy(typeMusic = enabled) }

  fun setTypeFlash(enabled: Boolean) = updateDraft { draft -> draft.copy(typeFlash = enabled) }

  fun setTypeStory(enabled: Boolean) = updateDraft { draft -> draft.copy(typeStory = enabled) }

  fun setTypePhoto(enabled: Boolean) = updateDraft { draft -> draft.copy(typePhoto = enabled) }

  fun setTypePoetry(enabled: Boolean) = updateDraft { draft -> draft.copy(typePoetry = enabled) }

  fun buildAppliedSearchRequest(): SearchAppliedRequest? {
    val snapshot = stateProvider()
    val applied = snapshot.draft.copy(query = snapshot.draft.query.trim())
    if (applied.query.isBlank()) return null
    return SearchAppliedRequest(
        applied = applied,
        firstUrl = buildSearchUrl(form = applied, page = 1),
    )
  }

  fun applySubmittedSearch(request: SearchAppliedRequest) {
    val snapshot = stateProvider()
    stateSink(
        snapshot.copy(
            overlayVisible = false,
            draft = request.applied,
            applied = request.applied,
            hasSearched = true,
        )
    )
  }

  fun restoreSearchForm(url: String, fallbackQuery: String): SearchFormState? {
    val restored = parseSearchFormFromUrl(url = url, fallbackQuery = fallbackQuery)
    return restored.takeIf { form -> form.query.isNotBlank() }
  }

  fun applyRestoredSearch(restored: SearchFormState) {
    val snapshot = stateProvider()
    stateSink(
        snapshot.copy(
            overlayVisible = false,
            draft = restored,
            applied = restored,
            hasSearched = true,
        )
    )
  }

  private fun updateDraft(transform: (SearchFormState) -> SearchFormState) {
    val snapshot = stateProvider()
    stateSink(snapshot.copy(draft = transform(snapshot.draft)))
  }
}

internal class SearchHistoryCoordinator(
    private val historyRepository: ActivityHistoryRepository,
    private val settingsService: AppSettingsService,
    private val systemLanguageProvider: SystemLanguageProvider,
    private val taxonomyRepository: FaTaxonomyRepository,
    private val searchUiLabelsRepository: SearchUiLabelsRepository,
) {
  suspend fun recordAppliedSearch(request: SearchAppliedRequest) {
    val appI18n = AppI18nSnapshot.from(settingsService.settings.value, systemLanguageProvider)
    val filtersSummary =
        buildSearchFiltersSummary(
            request.applied,
            appI18n,
            taxonomyRepository,
            searchUiLabelsRepository,
        )
    historyRepository.recordSearchQuery(
        query = request.applied.query,
        filtersSummary = filtersSummary,
        searchUrl = request.firstUrl,
    )
  }
}

internal class SearchPaginationCoordinator(
    private val repository: SearchRepository,
    private val submissionListHolder: SubmissionListHolder,
    private val log: Logger,
    private val paginationStateMachine: PaginationStateMachine<SubmissionThumbnail, Int>,
    private val screenModelScope: CoroutineScope,
    private val stateProvider: () -> SearchUiState,
    private val stateSink: (SearchUiState) -> Unit,
    private val pageStateSink: (PageState<SearchUiState>) -> Unit,
) {
  private var loadJob: Job? = null
  private var appendJob: Job? = null

  fun load(firstPageUrl: String, forceRefresh: Boolean) {
    log.i { "加载Search -> 开始(forceRefresh=$forceRefresh)" }
    if (loadJob?.isActive == true) {
      log.d { "加载Search -> 跳过(已有任务)" }
      return
    }
    val snapshot = stateProvider()
    stateSink(
        snapshot.applyPagination(
            paginationStateMachine.beginLoad(
                snapshot = snapshot.toPaginationSnapshot(),
                forceRefresh = forceRefresh,
            )
        )
    )
    if (snapshot.submissions.isEmpty()) {
      pageStateSink(PageState.Loading)
    }
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
                  snapshot = stateProvider().toPaginationSnapshot(),
                  result = pageState,
                  itemsOf = { page -> page.submissions },
                  nextPageUrlOf = { page -> page.nextPageUrl },
              )
          val updated = stateProvider().applyPagination(reduced)
          stateSink(updated)
          when (pageState) {
            is PageState.Success -> {
              syncSubmissionListHolder(updated)
              pageStateSink(PageState.Success(updated))
              log.i {
                "加载Search -> ${summarizePageState(pageState)}(count=${updated.submissions.size})"
              }
            }

            PageState.CfChallenge -> {
              pageStateSink(PageState.CfChallenge)
              log.w { "加载Search -> Cloudflare验证" }
            }

            is PageState.MatureBlocked -> {
              pageStateSink(PageState.MatureBlocked(pageState.reason))
              log.w { "加载Search -> 受限(${pageState.reason})" }
            }

            is PageState.Error -> {
              pageStateSink(PageState.Error(pageState.exception))
              log.e(pageState.exception) { "加载Search -> 失败" }
            }

            PageState.Loading -> {
              pageStateSink(PageState.Loading)
              log.d { "加载Search -> 加载中" }
            }
          }
        }
  }

  fun loadMore(force: Boolean) {
    val snapshot = stateProvider()
    val nextUrl = snapshot.nextPageUrl ?: return
    if (appendJob?.isActive == true) {
      log.d { "自动加载Search -> 跳过(已有追加任务)" }
      return
    }
    if (!paginationStateMachine.canLoadMore(snapshot.toPaginationSnapshot(), force = force)) {
      log.d { "自动加载Search -> 跳过(条件未满足)" }
      return
    }
    log.d { "自动加载Search -> 开始(force=$force)" }

    stateSink(
        snapshot.applyPagination(
            paginationStateMachine.beginAppend(snapshot.toPaginationSnapshot())
        )
    )

    appendJob =
        screenModelScope.launch {
          val pageState = repository.loadPage(nextUrl)
          val reduced =
              paginationStateMachine.reduceAppend(
                  snapshot = stateProvider().toPaginationSnapshot(),
                  result = pageState,
                  itemsOf = { page -> page.submissions },
                  nextPageUrlOf = { page -> page.nextPageUrl },
              )
          val updated = stateProvider().applyPagination(reduced)
          stateSink(updated)
          when (pageState) {
            is PageState.Success -> {
              syncSubmissionListHolder(updated)
              pageStateSink(PageState.Success(updated))
              log.d {
                "自动加载Search -> ${summarizePageState(pageState)}(count=${updated.submissions.size})"
              }
            }

            PageState.CfChallenge -> log.w { "自动加载Search -> Cloudflare验证" }
            is PageState.MatureBlocked -> log.w { "自动加载Search -> 受限(${pageState.reason})" }
            is PageState.Error -> log.e(pageState.exception) { "自动加载Search -> 失败" }
            PageState.Loading -> log.d { "自动加载Search -> 加载中" }
          }
        }
  }

  fun syncCurrentSubmission(sid: Int) {
    submissionListHolder.setCurrentBySid(sid)
  }

  private fun syncSubmissionListHolder(state: SearchUiState) {
    submissionListHolder.replace(
        submissions = state.submissions,
        nextPageUrl = state.nextPageUrl,
    )
  }
}

internal class SearchScreenWorkflow(
    repository: SearchRepository,
    submissionListHolder: SubmissionListHolder,
    historyRepository: ActivityHistoryRepository,
    settingsService: AppSettingsService,
    systemLanguageProvider: SystemLanguageProvider,
    taxonomyRepository: FaTaxonomyRepository,
    searchUiLabelsRepository: SearchUiLabelsRepository,
    private val screenModelScope: CoroutineScope,
    private val log: Logger,
    private val stateProvider: () -> SearchUiState,
    private val stateSink: (SearchUiState) -> Unit,
    private val pageStateSink: (PageState<SearchUiState>) -> Unit,
) {
  private val queryStateCoordinator =
      SearchQueryStateCoordinator(
          stateProvider = stateProvider,
          stateSink = stateSink,
      )
  private val historyCoordinator =
      SearchHistoryCoordinator(
          historyRepository = historyRepository,
          settingsService = settingsService,
          systemLanguageProvider = systemLanguageProvider,
          taxonomyRepository = taxonomyRepository,
          searchUiLabelsRepository = searchUiLabelsRepository,
      )
  private val paginationCoordinator =
      SearchPaginationCoordinator(
          repository = repository,
          submissionListHolder = submissionListHolder,
          log = log,
          paginationStateMachine =
              PaginationStateMachine<SubmissionThumbnail, Int>(
                  keyOf = { item -> item.id },
                  challengeMessage = { appString(Res.string.cloudflare_challenge_title) },
                  appendFallbackErrorMessage = { appString(Res.string.load_failed_please_retry) },
              ),
          screenModelScope = screenModelScope,
          stateProvider = stateProvider,
          stateSink = stateSink,
          pageStateSink = pageStateSink,
      )

  private var firstPageUrl: String? = null

  fun openOverlay() = queryStateCoordinator.openOverlay()

  fun closeOverlay() = queryStateCoordinator.closeOverlay()

  fun updateQuery(query: String) = queryStateCoordinator.updateQuery(query)

  fun toggleGender(gender: SearchGender, checked: Boolean) =
      queryStateCoordinator.toggleGender(gender, checked)

  fun updateCategory(category: Int) = queryStateCoordinator.updateCategory(category)

  fun updateType(type: Int) = queryStateCoordinator.updateType(type)

  fun updateSpecies(species: Int) = queryStateCoordinator.updateSpecies(species)

  fun updateOrderBy(orderBy: String) = queryStateCoordinator.updateOrderBy(orderBy)

  fun updateOrderDirection(orderDirection: String) =
      queryStateCoordinator.updateOrderDirection(orderDirection)

  fun updateRange(range: String) = queryStateCoordinator.updateRange(range)

  fun updateRangeFrom(value: String) = queryStateCoordinator.updateRangeFrom(value)

  fun updateRangeTo(value: String) = queryStateCoordinator.updateRangeTo(value)

  fun setRatingGeneral(enabled: Boolean) = queryStateCoordinator.setRatingGeneral(enabled)

  fun setRatingMature(enabled: Boolean) = queryStateCoordinator.setRatingMature(enabled)

  fun setRatingAdult(enabled: Boolean) = queryStateCoordinator.setRatingAdult(enabled)

  fun setTypeArt(enabled: Boolean) = queryStateCoordinator.setTypeArt(enabled)

  fun setTypeMusic(enabled: Boolean) = queryStateCoordinator.setTypeMusic(enabled)

  fun setTypeFlash(enabled: Boolean) = queryStateCoordinator.setTypeFlash(enabled)

  fun setTypeStory(enabled: Boolean) = queryStateCoordinator.setTypeStory(enabled)

  fun setTypePhoto(enabled: Boolean) = queryStateCoordinator.setTypePhoto(enabled)

  fun setTypePoetry(enabled: Boolean) = queryStateCoordinator.setTypePoetry(enabled)

  fun applySearch() {
    log.i { "应用Search -> 开始" }
    val request = queryStateCoordinator.buildAppliedSearchRequest()
    if (request == null) {
      log.w { "应用Search -> 跳过(空关键词)" }
      return
    }
    firstPageUrl = request.firstUrl
    queryStateCoordinator.applySubmittedSearch(request)
    pageStateSink(PageState.Loading)
    screenModelScope.launch { historyCoordinator.recordAppliedSearch(request) }
    log.i { "应用Search -> 已提交(keywordLen=${request.applied.query.length})" }
    paginationCoordinator.load(firstPageUrl = request.firstUrl, forceRefresh = true)
  }

  fun applySearchFromUrl(url: String, fallbackQuery: String = "") {
    val normalizedUrl = url.trim()
    if (normalizedUrl.isBlank()) return
    val restored =
        queryStateCoordinator.restoreSearchForm(
            url = normalizedUrl,
            fallbackQuery = fallbackQuery,
        ) ?: return
    log.i { "应用Search(历史) -> 开始" }
    firstPageUrl = normalizedUrl
    queryStateCoordinator.applyRestoredSearch(restored)
    pageStateSink(PageState.Loading)
    paginationCoordinator.load(firstPageUrl = normalizedUrl, forceRefresh = true)
  }

  fun refresh() {
    val firstUrl = firstPageUrl
    if (firstUrl == null) {
      log.d { "刷新Search -> 跳过(未搜索)" }
      return
    }
    log.i { "刷新Search -> 开始" }
    paginationCoordinator.load(firstPageUrl = firstUrl, forceRefresh = true)
  }

  fun onLastVisibleIndexChanged(lastVisibleIndex: Int) {
    val snapshot = stateProvider()
    if (snapshot.submissions.isEmpty()) return
    log.d { "自动加载Search -> 触发检查(last=$lastVisibleIndex,total=${snapshot.submissions.size})" }
    if (lastVisibleIndex > snapshot.submissions.lastIndex - searchAutoLoadThreshold) {
      paginationCoordinator.loadMore(force = false)
    }
  }

  fun retryLoadMore() {
    paginationCoordinator.loadMore(force = true)
  }

  fun setCurrentSubmission(sid: Int) {
    paginationCoordinator.syncCurrentSubmission(sid)
  }
}
