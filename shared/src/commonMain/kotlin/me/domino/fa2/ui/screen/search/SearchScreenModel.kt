package me.domino.fa2.ui.screen.search

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.repository.SearchRepository
import me.domino.fa2.ui.navigation.SubmissionListHolder
import me.domino.fa2.ui.state.PaginationSnapshot
import me.domino.fa2.ui.state.PaginationStateMachine
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizePageState

private const val searchAutoLoadThreshold = 10

enum class SearchGender(val token: String) {
  Male("male"),
  Female("female"),
  TransMale("trans_male"),
  TransFemale("trans_female"),
  Intersex("intersex"),
  NonBinary("non_binary"),
}

data class SearchFormState(
  val query: String = "",
  val category: Int = 1,
  val type: Int = 1,
  val species: Int = 1,
  val orderBy: String = "relevancy",
  val orderDirection: String = "desc",
  val range: String = "all",
  val rangeFrom: String = "",
  val rangeTo: String = "",
  val ratingGeneral: Boolean = true,
  val ratingMature: Boolean = true,
  val ratingAdult: Boolean = true,
  val typeArt: Boolean = true,
  val typeMusic: Boolean = true,
  val typeFlash: Boolean = true,
  val typeStory: Boolean = true,
  val typePhoto: Boolean = true,
  val typePoetry: Boolean = true,
  val selectedGenders: Set<SearchGender> = emptySet(),
)

data class SearchUiState(
  val overlayVisible: Boolean = false,
  val draft: SearchFormState = SearchFormState(),
  val applied: SearchFormState? = null,
  val hasSearched: Boolean = false,
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

/** Search 页面状态模型。 */
class SearchScreenModel(
  private val repository: SearchRepository,
  private val submissionListHolder: SubmissionListHolder,
) : StateScreenModel<SearchUiState>(SearchUiState()) {
  private val log = FaLog.withTag("SearchScreenModel")
  private val paginationStateMachine =
    PaginationStateMachine<SubmissionThumbnail, Int>(keyOf = { item -> item.id })
  private var loadJob: Job? = null
  private var appendJob: Job? = null
  private var firstPageUrl: String? = null

  fun openOverlay() {
    mutableState.value = state.value.copy(overlayVisible = true)
  }

  fun closeOverlay() {
    mutableState.value = state.value.copy(overlayVisible = false)
  }

  fun updateQuery(query: String) {
    val genders = parseGendersFromQuery(query)
    mutableState.value =
      state.value.copy(draft = state.value.draft.copy(query = query, selectedGenders = genders))
  }

  fun toggleGender(gender: SearchGender, checked: Boolean) {
    val current = state.value.draft
    val updatedGenders =
      current.selectedGenders
        .toMutableSet()
        .apply { if (checked) add(gender) else remove(gender) }
        .toSet()
    val rewrittenQuery = rewriteQueryWithGenders(query = current.query, genders = updatedGenders)
    mutableState.value =
      state.value.copy(
        draft = current.copy(query = rewrittenQuery, selectedGenders = updatedGenders)
      )
  }

  fun updateCategory(category: Int) {
    mutableState.value = state.value.copy(draft = state.value.draft.copy(category = category))
  }

  fun updateType(type: Int) {
    mutableState.value = state.value.copy(draft = state.value.draft.copy(type = type))
  }

  fun updateSpecies(species: Int) {
    mutableState.value = state.value.copy(draft = state.value.draft.copy(species = species))
  }

  fun updateOrderBy(orderBy: String) {
    mutableState.value = state.value.copy(draft = state.value.draft.copy(orderBy = orderBy))
  }

  fun updateOrderDirection(orderDirection: String) {
    mutableState.value =
      state.value.copy(draft = state.value.draft.copy(orderDirection = orderDirection))
  }

  fun updateRange(range: String) {
    mutableState.value = state.value.copy(draft = state.value.draft.copy(range = range))
  }

  fun updateRangeFrom(value: String) {
    mutableState.value = state.value.copy(draft = state.value.draft.copy(rangeFrom = value))
  }

  fun updateRangeTo(value: String) {
    mutableState.value = state.value.copy(draft = state.value.draft.copy(rangeTo = value))
  }

  fun setRatingGeneral(enabled: Boolean) {
    mutableState.value = state.value.copy(draft = state.value.draft.copy(ratingGeneral = enabled))
  }

  fun setRatingMature(enabled: Boolean) {
    mutableState.value = state.value.copy(draft = state.value.draft.copy(ratingMature = enabled))
  }

  fun setRatingAdult(enabled: Boolean) {
    mutableState.value = state.value.copy(draft = state.value.draft.copy(ratingAdult = enabled))
  }

  fun setTypeArt(enabled: Boolean) {
    mutableState.value = state.value.copy(draft = state.value.draft.copy(typeArt = enabled))
  }

  fun setTypeMusic(enabled: Boolean) {
    mutableState.value = state.value.copy(draft = state.value.draft.copy(typeMusic = enabled))
  }

  fun setTypeFlash(enabled: Boolean) {
    mutableState.value = state.value.copy(draft = state.value.draft.copy(typeFlash = enabled))
  }

  fun setTypeStory(enabled: Boolean) {
    mutableState.value = state.value.copy(draft = state.value.draft.copy(typeStory = enabled))
  }

  fun setTypePhoto(enabled: Boolean) {
    mutableState.value = state.value.copy(draft = state.value.draft.copy(typePhoto = enabled))
  }

  fun setTypePoetry(enabled: Boolean) {
    mutableState.value = state.value.copy(draft = state.value.draft.copy(typePoetry = enabled))
  }

  fun applySearch() {
    log.i { "应用Search -> 开始" }
    val applied = state.value.draft.copy(query = state.value.draft.query.trim())
    if (applied.query.isBlank()) {
      log.w { "应用Search -> 跳过(空关键词)" }
      return
    }
    firstPageUrl = buildSearchUrl(form = applied, page = 1)
    mutableState.value =
      state.value.copy(overlayVisible = false, applied = applied, hasSearched = true)
    log.i { "应用Search -> 已提交(keywordLen=${applied.query.length})" }
    load(forceRefresh = true)
  }

  fun refresh() {
    if (firstPageUrl == null) {
      log.d { "刷新Search -> 跳过(未搜索)" }
      return
    }
    log.i { "刷新Search -> 开始" }
    load(forceRefresh = true)
  }

  fun onLastVisibleIndexChanged(lastVisibleIndex: Int) {
    val snapshot = state.value
    if (snapshot.submissions.isEmpty()) return
    log.d { "自动加载Search -> 触发检查(last=$lastVisibleIndex,total=${snapshot.submissions.size})" }
    if (lastVisibleIndex > snapshot.submissions.lastIndex - searchAutoLoadThreshold) {
      loadMore(force = false)
    }
  }

  fun retryLoadMore() {
    loadMore(force = true)
  }

  fun setCurrentSubmission(sid: Int) {
    submissionListHolder.setCurrentBySid(sid)
  }

  private fun load(forceRefresh: Boolean) {
    val firstUrl = firstPageUrl ?: return
    log.i { "加载Search -> 开始(forceRefresh=$forceRefresh)" }
    if (loadJob?.isActive == true) {
      log.d { "加载Search -> 跳过(已有任务)" }
      return
    }
    val snapshot = state.value
    mutableState.value =
      snapshot.applyPagination(
        paginationStateMachine.beginLoad(
          snapshot = snapshot.toPaginationSnapshot(),
          forceRefresh = forceRefresh,
        )
      )
    loadJob = screenModelScope.launch {
      val pageState =
        if (forceRefresh) {
          repository.refreshPage(firstUrl)
        } else {
          repository.loadPage(firstUrl)
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
          syncSubmissionListHolder(updated)
          log.i {
            "加载Search -> ${summarizePageState(pageState)}(count=${updated.submissions.size})"
          }
        }

        PageState.CfChallenge -> log.w { "加载Search -> Cloudflare验证" }
        is PageState.MatureBlocked -> log.w { "加载Search -> 受限(${pageState.reason})" }
        is PageState.Error -> log.e(pageState.exception) { "加载Search -> 失败" }
        PageState.Loading -> log.d { "加载Search -> 加载中" }
      }
    }
  }

  private fun loadMore(force: Boolean) {
    val snapshot = state.value
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

    mutableState.value =
      snapshot.applyPagination(paginationStateMachine.beginAppend(snapshot.toPaginationSnapshot()))

    appendJob = screenModelScope.launch {
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
          syncSubmissionListHolder(updated)
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

  private fun buildSearchUrl(form: SearchFormState, page: Int): String =
    FaUrls.search(
      FaUrls.SearchParams(
        q = form.query,
        page = page,
        category = form.category,
        arttype = form.type,
        species = form.species,
        orderBy = form.orderBy,
        orderDirection = form.orderDirection,
        range = form.range,
        rangeFrom = form.rangeFrom,
        rangeTo = form.rangeTo,
        ratingGeneral = form.ratingGeneral,
        ratingMature = form.ratingMature,
        ratingAdult = form.ratingAdult,
        typeArt = form.typeArt,
        typeMusic = form.typeMusic,
        typeFlash = form.typeFlash,
        typeStory = form.typeStory,
        typePhoto = form.typePhoto,
        typePoetry = form.typePoetry,
      )
    )

  private fun syncSubmissionListHolder(state: SearchUiState) {
    submissionListHolder.replace(submissions = state.submissions, nextPageUrl = state.nextPageUrl)
  }
}

private fun SearchUiState.toPaginationSnapshot(): PaginationSnapshot<SubmissionThumbnail> =
  PaginationSnapshot(
    items = submissions,
    nextPageUrl = nextPageUrl,
    loading = loading,
    refreshing = refreshing,
    isLoadingMore = isLoadingMore,
    errorMessage = errorMessage,
    appendErrorMessage = appendErrorMessage,
  )

private fun SearchUiState.applyPagination(
  snapshot: PaginationSnapshot<SubmissionThumbnail>
): SearchUiState =
  copy(
    submissions = snapshot.items,
    nextPageUrl = snapshot.nextPageUrl,
    loading = snapshot.loading,
    refreshing = snapshot.refreshing,
    isLoadingMore = snapshot.isLoadingMore,
    errorMessage = snapshot.errorMessage,
    appendErrorMessage = snapshot.appendErrorMessage,
  )

internal fun parseGendersFromQuery(query: String): Set<SearchGender> {
  val tokens = query.split(Regex("\\s+")).filter { token -> token.isNotBlank() }
  val keywordsIndex = tokens.indexOfFirst { token -> token.equals("@keywords", ignoreCase = true) }
  if (keywordsIndex < 0) return emptySet()
  val keywordTokens = tokens.drop(keywordsIndex + 1)
  return keywordTokens.mapNotNullTo(linkedSetOf()) { token ->
    SearchGender.entries.firstOrNull { gender ->
      gender.token.equals(token.trim().lowercase(), ignoreCase = true)
    }
  }
}

internal fun rewriteQueryWithGenders(query: String, genders: Set<SearchGender>): String {
  val tokens = query.split(Regex("\\s+")).filter { token -> token.isNotBlank() }.toMutableList()
  val keywordsIndex = tokens.indexOfFirst { token -> token.equals("@keywords", ignoreCase = true) }
  val selectedGenderTokens =
    SearchGender.entries.filter { gender -> gender in genders }.map { gender -> gender.token }

  if (keywordsIndex < 0) {
    if (selectedGenderTokens.isEmpty()) {
      return tokens.joinToString(" ").trim()
    }
    val base = tokens.joinToString(" ").trim()
    return if (base.isBlank()) {
      "@keywords ${selectedGenderTokens.joinToString(" ")}"
    } else {
      "$base @keywords ${selectedGenderTokens.joinToString(" ")}"
    }
  }

  val beforeKeywords = tokens.take(keywordsIndex)
  val afterKeywords = tokens.drop(keywordsIndex + 1)
  val nonGenderKeywords = afterKeywords.filterNot { token ->
    SearchGender.entries.any { gender ->
      gender.token.equals(token.trim().lowercase(), ignoreCase = true)
    }
  }

  if (selectedGenderTokens.isEmpty() && nonGenderKeywords.isEmpty()) {
    return beforeKeywords.joinToString(" ").trim()
  }

  val rebuilt = buildList {
    addAll(beforeKeywords)
    add("@keywords")
    addAll(nonGenderKeywords)
    addAll(selectedGenderTokens)
  }
  return rebuilt.joinToString(" ").trim()
}
