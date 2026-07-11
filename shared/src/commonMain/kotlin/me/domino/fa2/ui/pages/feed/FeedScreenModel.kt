package me.domino.fa2.ui.pages.feed

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.QueryState
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.model.mapData
import me.domino.fa2.data.model.message
import me.domino.fa2.data.remote.fa.feed.FeedRepository
import me.domino.fa2.ui.utils.AppendOutcome
import me.domino.fa2.ui.utils.appendTo

private const val autoLoadThreshold = 10

data class FeedUiState(
    val submissions: List<SubmissionThumbnail> = emptyList(),
    val nextPageUrl: String? = null,
    val refreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val appendErrorMessage: String? = null,
) {
  val loading: Boolean
    get() = submissions.isEmpty()

  val hasMore: Boolean
    get() = !nextPageUrl.isNullOrBlank()
}

class FeedScreenModel(private val repository: FeedRepository) : ScreenModel {
  private val mutableState = MutableStateFlow<QueryState<FeedUiState>>(QueryState.Loading())
  val state: StateFlow<QueryState<FeedUiState>> = mutableState.asStateFlow()
  private var loadJob: Job? = null
  private var appendJob: Job? = null

  fun load(forceRefresh: Boolean = false) {
    if (loadJob?.isActive != true) {
      loadJob =
          screenModelScope.launch {
            repository.streamFirstPage().collect { query ->
              mutableState.value =
                  query.mapData { page ->
                    FeedUiState(
                        page.submissions,
                        page.nextPageUrl,
                        refreshing = query is QueryState.Success && query.refreshing,
                    )
                  }
            }
          }
    }
    if (forceRefresh) screenModelScope.launch { repository.refreshFirstPage() }
  }

  fun refresh() = load(forceRefresh = true)

  fun onLastVisibleIndexChanged(lastVisibleIndex: Int) {
    val current = mutableState.value.data ?: return
    if (lastVisibleIndex > current.submissions.lastIndex - autoLoadThreshold) loadMore(false)
  }

  fun retryLoadMore() = loadMore(true)

  private fun loadMore(force: Boolean) {
    val current = mutableState.value.data ?: return
    val nextUrl = current.nextPageUrl ?: return
    if (appendJob?.isActive == true || (!force && current.appendErrorMessage != null)) return
    mutableState.value =
        QueryState.Success(current.copy(isLoadingMore = true, appendErrorMessage = null))
    appendJob =
        screenModelScope.launch {
          val known = mutableState.value.data ?: current
          when (
              val outcome =
                  repository.loadPageByNextUrl(nextUrl).appendTo(
                      known.submissions,
                      { it.submissions },
                      { it.nextPageUrl },
                  ) {
                    it.id
                  }
          ) {
            is AppendOutcome.Appended ->
                mutableState.value =
                    QueryState.Success(
                        known.copy(
                            submissions = outcome.items,
                            nextPageUrl = outcome.nextPageUrl,
                            isLoadingMore = false,
                        )
                    )
            is AppendOutcome.Failed ->
                mutableState.value =
                    QueryState.Success(
                        known.copy(
                            isLoadingMore = false,
                            appendErrorMessage = outcome.error.message(),
                        )
                    )
          }
        }
  }
}
