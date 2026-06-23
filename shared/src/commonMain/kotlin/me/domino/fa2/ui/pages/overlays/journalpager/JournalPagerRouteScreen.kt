package me.domino.fa2.ui.pages.overlays.journalpager

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.no_content
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.domino.fa2.data.fa.journal.JournalsRepository
import me.domino.fa2.ui.app.navigation.goBackHome
import me.domino.fa2.ui.app.scaffold.JournalDetailRouteTopBar
import me.domino.fa2.ui.pages.overlays.journaldetail.JournalDetailBody
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val journalPagerAppendThreshold = 3

internal class JournalPagerRouteScreen(
    private val initialJournalId: Int,
    private val contextId: String,
) : Screen {
  override val key: String = "journal-pager:$contextId:$initialJournalId"

  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val contextScreenModel =
        navigator.rememberNavigatorScreenModel<JournalPagerContextScreenModel>(
            tag = "journal-pager-context:$contextId"
        ) {
          JournalPagerContextScreenModel()
        }
    val journalsRepository = koinInject<JournalsRepository>()
    val state by contextScreenModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val journals = state.journals
    val safeInitialIndex = state.currentIndex.coerceIn(0, (journals.size - 1).coerceAtLeast(0))
    val pagerState =
        rememberPagerState(initialPage = safeInitialIndex, pageCount = { journals.size })
    val listStates = remember {
      mutableMapOf<Int, androidx.compose.foundation.lazy.LazyListState>()
    }
    val focusRequester = remember { FocusRequester() }
    val currentJournal =
        journals.getOrNull(state.currentIndex)
            ?: journals.firstOrNull { it.id == state.currentJournalId }
    val shareUrl = currentJournal?.journalUrl.orEmpty()

    fun requestJournalAppendIfPossible() {
      if (state.ownerUsername.isNotBlank()) {
        contextScreenModel.requestAppend { nextPageUrl ->
          journalsRepository.loadJournalsPage(
              username = state.ownerUsername,
              nextPageUrl = nextPageUrl,
          )
        }
      }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(state.currentIndex, journals.size) {
      if (journals.isNotEmpty() && pagerState.currentPage != state.currentIndex) {
        pagerState.scrollToPage(state.currentIndex.coerceIn(0, journals.lastIndex))
      }
    }

    LaunchedEffect(pagerState, journals.size, state.nextPageUrl) {
      snapshotFlow { pagerState.currentPage }
          .distinctUntilChanged()
          .collect { page ->
            contextScreenModel.setCurrentIndex(page)
            if (
                journals.isNotEmpty() &&
                    page >= journals.lastIndex - journalPagerAppendThreshold &&
                    state.ownerUsername.isNotBlank()
            ) {
              requestJournalAppendIfPossible()
            }
          }
    }

    Column(
        modifier =
            Modifier.fillMaxSize().focusRequester(focusRequester).focusable().onPreviewKeyEvent {
                event ->
              if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
              when (event.key) {
                Key.DirectionLeft -> {
                  if (pagerState.currentPage > 0) {
                    coroutineScope.launch {
                      pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                    true
                  } else {
                    false
                  }
                }

                Key.DirectionRight -> {
                  when {
                    pagerState.currentPage < pagerState.pageCount - 1 -> {
                      coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                      }
                      true
                    }

                    state.hasMore -> {
                      requestJournalAppendIfPossible()
                      true
                    }

                    else -> false
                  }
                }

                else -> false
              }
            }
    ) {
      JournalDetailRouteTopBar(
          onBack = { navigator.pop() },
          onGoHome = { navigator.goBackHome() },
          shareUrl = shareUrl,
          onTitleClick = {
            val currentId = currentJournal?.id ?: return@JournalDetailRouteTopBar
            coroutineScope.launch { listStates[currentId]?.animateScrollToItem(0) }
          },
      )

      if (journals.isEmpty()) {
        Text(
            text = stringResource(Res.string.no_content),
            modifier = Modifier.padding(16.dp),
        )
        return@Column
      }

      HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val journal = journals[page]
        key(journal.id) {
          val listState = rememberLazyListState()
          LaunchedEffect(journal.id, listState) { listStates[journal.id] = listState }
          JournalDetailBody(
              journalId = journal.id,
              journalUrl = journal.journalUrl,
              listState = listState,
          )
        }
      }
    }
  }
}
