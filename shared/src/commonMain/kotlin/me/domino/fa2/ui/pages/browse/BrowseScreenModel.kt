package me.domino.fa2.ui.pages.browse

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.domino.fa2.data.model.QueryState
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.model.errorOrNull
import me.domino.fa2.data.model.mapData
import me.domino.fa2.data.model.message
import me.domino.fa2.data.remote.fa.browse.BrowseRepository
import me.domino.fa2.ui.components.paging.reduceAppendResult
import me.domino.fa2.utils.FaUrls

private const val browseAutoLoadThreshold = 10

@Serializable
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
    val refreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val appendErrorMessage: String? = null,
) {
  val loading: Boolean
    get() = submissions.isEmpty()

  val hasMore: Boolean
    get() = nextPageUrl != null
}

class BrowseScreenModel(
    private val repository: BrowseRepository,
    initialFilter: BrowseFilterState = BrowseFilterState(),
) : ScreenModel {
  private val mutableState =
      MutableStateFlow<QueryState<BrowseUiState>>(
          QueryState.Success(BrowseUiState(initialFilter, initialFilter))
      )
  val state: StateFlow<QueryState<BrowseUiState>> = mutableState.asStateFlow()
  private var loadJob: Job? = null
  private var appendJob: Job? = null

  init {
    load(false)
  }

  fun updateCategory(value: Int) = updateDraft { copy(category = value) }

  fun updateType(value: Int) = updateDraft { copy(type = value) }

  fun updateSpecies(value: Int) = updateDraft { copy(species = value) }

  fun updateGender(value: String) = updateDraft { copy(gender = value) }

  fun setRatingGeneral(value: Boolean) = updateDraft { copy(ratingGeneral = value) }

  fun setRatingMature(value: Boolean) = updateDraft { copy(ratingMature = value) }

  fun setRatingAdult(value: Boolean) = updateDraft { copy(ratingAdult = value) }

  fun applyFilter() {
    update { copy(appliedFilter = draftFilter) }
    load(true)
  }

  fun applyFilter(filter: BrowseFilterState) {
    update { copy(draftFilter = filter, appliedFilter = filter) }
    load(true)
  }

  fun refresh() = load(true)

  fun onLastVisibleIndexChanged(index: Int) {
    val current = mutableState.value.data ?: return
    if (index > current.submissions.lastIndex - browseAutoLoadThreshold) loadMore(false)
  }

  fun retryLoadMore() = loadMore(true)

  private fun load(refresh: Boolean) {
    val current = mutableState.value.data ?: BrowseUiState()
    val url = buildBrowseUrl(current.appliedFilter)
    loadJob?.cancel()
    loadJob =
        screenModelScope.launch {
          repository.page(url).collect { query ->
            mutableState.value =
                query.mapData { page ->
                  val latest = mutableState.value.data ?: current
                  latest.copy(
                      submissions = page.submissions,
                      nextPageUrl = page.nextPageUrl,
                      refreshing = query is QueryState.Success && query.refreshing,
                  )
                }
          }
        }
    if (refresh) screenModelScope.launch { repository.refreshPage(url) }
  }

  private fun loadMore(force: Boolean) {
    val current = mutableState.value.data ?: return
    val url = current.nextPageUrl ?: return
    if (appendJob?.isActive == true || (!force && current.appendErrorMessage != null)) return
    mutableState.value =
        QueryState.Success(current.copy(isLoadingMore = true, appendErrorMessage = null))
    appendJob =
        screenModelScope.launch {
          val known = mutableState.value.data ?: current
          when (
              val outcome =
                  repository
                      .loadPage(url)
                      .reduceAppendResult(
                          known.submissions,
                          { it.submissions },
                          { it.nextPageUrl },
                          keyOf = { it.id },
                      )
          ) {
            is ResourceResult.Success ->
                mutableState.value =
                    QueryState.Success(
                        known.copy(
                            submissions = outcome.data.items,
                            nextPageUrl = outcome.data.nextPageUrl,
                            isLoadingMore = false,
                        )
                    )
            else ->
                update {
                  copy(isLoadingMore = false, appendErrorMessage = outcome.errorOrNull()?.message())
                }
          }
        }
  }

  private fun updateDraft(block: BrowseFilterState.() -> BrowseFilterState) = update {
    copy(draftFilter = draftFilter.block())
  }

  private fun update(block: BrowseUiState.() -> BrowseUiState) {
    val next = (mutableState.value.data ?: BrowseUiState()).block()
    mutableState.value = QueryState.Success(next)
  }
}

internal fun buildBrowseUrl(filter: BrowseFilterState): String =
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
