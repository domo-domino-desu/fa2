package me.domino.fa2.ui.pages.user.journal

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import fa2.shared.generated.resources.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.JournalPage
import me.domino.fa2.data.model.JournalSummary
import me.domino.fa2.data.model.PageState
import me.domino.fa2.ui.i18n.appString

internal data class JournalPagerUiState(
    val ownerUsername: String = "",
    val journals: List<JournalSummary> = emptyList(),
    val currentJournalId: Int = 0,
    val currentIndex: Int = 0,
    val nextPageUrl: String? = null,
    val loadingMore: Boolean = false,
    val appendErrorMessage: String? = null,
) {
  val hasMore: Boolean
    get() = !nextPageUrl.isNullOrBlank()
}

internal class JournalPagerContextScreenModel :
    StateScreenModel<JournalPagerUiState>(JournalPagerUiState()) {
  private var appendJob: Job? = null

  fun seed(
      ownerUsername: String,
      journals: List<JournalSummary>,
      selectedJournalId: Int,
      nextPageUrl: String?,
  ) {
    val normalizedJournals = journals.distinctBy { it.id }
    val selectedIndex = normalizedJournals.indexOfFirst { it.id == selectedJournalId }
    mutableState.value =
        JournalPagerUiState(
            ownerUsername = ownerUsername,
            journals = normalizedJournals,
            currentJournalId =
                normalizedJournals.getOrNull(selectedIndex.coerceAtLeast(0))?.id
                    ?: selectedJournalId,
            currentIndex = selectedIndex.takeIf { it >= 0 } ?: 0,
            nextPageUrl = nextPageUrl,
        )
  }

  fun setCurrentIndex(index: Int) {
    val snapshot = state.value
    val safeIndex = index.coerceIn(0, (snapshot.journals.size - 1).coerceAtLeast(0))
    val journal = snapshot.journals.getOrNull(safeIndex) ?: return
    if (snapshot.currentIndex == safeIndex && snapshot.currentJournalId == journal.id) return
    mutableState.value = snapshot.copy(currentIndex = safeIndex, currentJournalId = journal.id)
  }

  fun requestAppend(
      force: Boolean = false,
      loadPage: suspend (nextPageUrl: String) -> PageState<JournalPage>,
  ) {
    val snapshot = state.value
    val nextUrl = snapshot.nextPageUrl?.trim().takeUnless { it.isNullOrBlank() } ?: return
    if (appendJob?.isActive == true) return
    if (!force && snapshot.loadingMore) return
    mutableState.value = snapshot.copy(loadingMore = true, appendErrorMessage = null)
    appendJob =
        screenModelScope.launch {
          val result = loadPage(nextUrl)
          val current = state.value
          mutableState.value =
              when (result) {
                is PageState.Success -> {
                  val merged = (current.journals + result.data.journals).distinctBy { it.id }
                  val anchoredIndex = merged.indexOfFirst { it.id == current.currentJournalId }
                  val fallbackIndex =
                      current.currentIndex.coerceIn(0, (merged.size - 1).coerceAtLeast(0))
                  val nextIndex = anchoredIndex.takeIf { it >= 0 } ?: fallbackIndex
                  current.copy(
                      journals = merged,
                      currentIndex = nextIndex,
                      currentJournalId =
                          merged.getOrNull(nextIndex)?.id ?: current.currentJournalId,
                      nextPageUrl = result.data.nextPageUrl,
                      loadingMore = false,
                      appendErrorMessage = null,
                  )
                }

                is PageState.AuthRequired ->
                    current.copy(loadingMore = false, appendErrorMessage = result.message)

                PageState.CfChallenge ->
                    current.copy(
                        loadingMore = false,
                        appendErrorMessage = appString(Res.string.cloudflare_challenge_title),
                    )

                is PageState.MatureBlocked ->
                    current.copy(loadingMore = false, appendErrorMessage = result.reason)

                is PageState.Error ->
                    current.copy(
                        loadingMore = false,
                        appendErrorMessage =
                            result.exception.message
                                ?: appString(Res.string.load_failed_please_retry),
                    )

                PageState.Loading -> current.copy(loadingMore = false)
              }
        }
  }
}
