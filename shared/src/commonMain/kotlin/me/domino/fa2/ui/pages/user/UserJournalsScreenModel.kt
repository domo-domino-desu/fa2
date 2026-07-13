package me.domino.fa2.ui.pages.user

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.JournalSummary
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.errorMessageOrNull
import me.domino.fa2.data.model.errorOrNull
import me.domino.fa2.data.model.message
import me.domino.fa2.data.remote.fa.journal.JournalsRepository
import me.domino.fa2.ui.components.paging.reduceAppendResult

private const val journalsAutoLoadThreshold = 6

data class UserJournalsUiState(
    val journals: List<JournalSummary> = emptyList(),
    val nextPageUrl: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val appendErrorMessage: String? = null,
) {
  val hasMore: Boolean
    get() = nextPageUrl != null
}

class UserJournalsScreenModel(
    private val username: String,
    private val repository: JournalsRepository,
) : StateScreenModel<UserJournalsUiState>(UserJournalsUiState()) {
  private var loadJob: Job? = null
  private var appendJob: Job? = null

  init {
    load()
  }

  fun load(forceRefresh: Boolean = false) {
    if (loadJob?.isActive == true || (!forceRefresh && state.value.journals.isNotEmpty())) return
    mutableState.value =
        state.value.copy(loading = state.value.journals.isEmpty(), refreshing = forceRefresh)
    loadJob =
        screenModelScope.launch {
          val result =
              if (forceRefresh) repository.refreshJournalsFirstPage(username)
              else repository.loadJournalsPage(username)
          mutableState.value =
              when (result) {
                is ResourceResult.Success ->
                    state.value.copy(
                        journals = result.data.journals,
                        nextPageUrl = result.data.nextPageUrl,
                        loading = false,
                        refreshing = false,
                        errorMessage = null,
                    )
                else ->
                    state.value.copy(
                        loading = false,
                        refreshing = false,
                        errorMessage = result.errorMessageOrNull(),
                    )
              }
        }
  }

  fun onLastVisibleIndexChanged(index: Int) {
    if (index > state.value.journals.lastIndex - journalsAutoLoadThreshold) loadMore(false)
  }

  fun retryLoadMore() = loadMore(true)

  private fun loadMore(force: Boolean) {
    val current = state.value
    val cursor = current.nextPageUrl ?: return
    if (appendJob?.isActive == true || (!force && current.appendErrorMessage != null)) return
    mutableState.value = current.copy(isLoadingMore = true, appendErrorMessage = null)
    appendJob =
        screenModelScope.launch {
          when (
              val outcome =
                  repository
                      .loadJournalsPage(username, cursor)
                      .reduceAppendResult(
                          state.value.journals,
                          { it.journals },
                          { it.nextPageUrl },
                          keyOf = { it.id },
                      )
          ) {
            is ResourceResult.Success ->
                mutableState.value =
                    state.value.copy(
                        journals = outcome.data.items,
                        nextPageUrl = outcome.data.nextPageUrl,
                        isLoadingMore = false,
                    )
            else ->
                mutableState.value =
                    state.value.copy(
                        isLoadingMore = false,
                        appendErrorMessage = outcome.errorOrNull()?.message(),
                    )
          }
        }
  }
}
