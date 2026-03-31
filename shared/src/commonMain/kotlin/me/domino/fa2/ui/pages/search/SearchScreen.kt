package me.domino.fa2.ui.pages.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import me.domino.fa2.ui.components.platform.PlatformBackHandler
import me.domino.fa2.ui.components.submission.SubmissionWaterfall
import me.domino.fa2.ui.components.submission.SubmissionWaterfallPageControls
import me.domino.fa2.ui.components.submission.SubmissionWaterfallViewportSnapshot
import me.domino.fa2.ui.components.submission.WaterfallLoadingSkeleton
import me.domino.fa2.ui.components.submission.WaterfallRefreshBox
import me.domino.fa2.ui.host.LocalAppSettings
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.ui.pages.search.component.SearchHint
import me.domino.fa2.ui.pages.search.component.SearchOverlayContent
import me.domino.fa2.ui.pages.search.component.SearchStatusCard
import me.domino.fa2.ui.pages.search.component.rememberSearchOverlayUiData
import me.domino.fa2.ui.pages.submission.WaterfallScrollRequest
import org.jetbrains.compose.resources.stringResource

@Composable
fun SearchScreen(
    state: SearchUiState,
    actions: SearchScreenActions,
    waterfallState: LazyStaggeredGridState,
    pageControls: SubmissionWaterfallPageControls? = null,
    canLoadPreviousPageAtTop: Boolean = false,
    loadingPreviousPage: Boolean = false,
    prependErrorMessage: String? = null,
    onLoadPreviousPageAtTop: (() -> Unit)? = null,
    onLoadFirstPage: (() -> Unit)? = null,
    onLoadPreviousPage: (() -> Unit)? = null,
    onJumpToPage: ((Int) -> Unit)? = null,
    onLoadNextPage: (() -> Unit)? = null,
    onLoadLastPage: (() -> Unit)? = null,
    pendingScrollRequest: WaterfallScrollRequest? = null,
    onConsumeScrollRequest: ((Long) -> Unit)? = null,
    onViewportChanged: ((SubmissionWaterfallViewportSnapshot) -> Unit)? = null,
) {
  val settings = LocalAppSettings.current
  val refreshEnabled = pageControls?.showFirstPage != true || !pageControls.canLoadFirstPage
  val overlayUiData = rememberSearchOverlayUiData()
  val canSearch = state.draft.query.trim().isNotBlank()

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      SearchBarShell(
          query = state.draft.query,
          overlayVisible = state.overlayVisible,
          onToggleOverlay = {
            if (state.overlayVisible) actions.onCloseOverlay() else actions.onOpenOverlay()
          },
      )

      if (!state.hasSearched) {
        SearchHint(
            text = stringResource(Res.string.search_hint),
            modifier = Modifier.fillMaxSize(),
        )
      } else {
        if (state.loading && state.submissions.isEmpty()) {
          WaterfallLoadingSkeleton(
              minCardWidthDp = settings.waterfallMinCardWidthDp,
              state = waterfallState,
              modifier = Modifier.fillMaxSize(),
          )
        } else {
          state.errorMessage
              ?.takeIf { message -> message.isNotBlank() && state.submissions.isNotEmpty() }
              ?.let { inlineErrorMessage ->
                SearchStatusCard(
                    title = stringResource(Res.string.load_failed),
                    body = inlineErrorMessage,
                    onRetry = actions.onRetry,
                )
              }
          val waterfallContent: @Composable () -> Unit = {
            SubmissionWaterfall(
                items = state.submissions,
                onItemClick = actions.onOpenSubmission,
                onLastVisibleIndexChanged = actions.onLastVisibleIndexChanged,
                canLoadMore = state.hasMore,
                loadingMore = state.isLoadingMore,
                appendErrorMessage = state.appendErrorMessage,
                onRetryLoadMore = actions.onRetryLoadMore,
                state = waterfallState,
                minCardWidthDp = settings.waterfallMinCardWidthDp,
                blockedSubmissionMode = settings.blockedSubmissionWaterfallMode,
                pageControls = pageControls,
                canLoadPreviousPageAtTop = canLoadPreviousPageAtTop,
                loadingPreviousPage = loadingPreviousPage,
                prependErrorMessage = prependErrorMessage,
                onLoadPreviousPageAtTop = onLoadPreviousPageAtTop,
                onLoadFirstPage = onLoadFirstPage,
                onLoadPreviousPage = onLoadPreviousPage,
                onJumpToPage = onJumpToPage,
                onLoadNextPage = onLoadNextPage,
                onLoadLastPage = onLoadLastPage,
                pendingScrollRequest = pendingScrollRequest,
                onConsumeScrollRequest = onConsumeScrollRequest,
                onViewportChanged = onViewportChanged,
            )
          }
          WaterfallRefreshBox(
              enabled = refreshEnabled,
              refreshing = state.refreshing,
              onRefresh = actions.onRefresh,
              modifier = Modifier.fillMaxSize(),
          ) {
            waterfallContent()
          }
        }
      }
    }

    if (state.overlayVisible) {
      SearchOverlayContent(
          form = state.draft,
          actions = actions,
          canSearch = canSearch,
          uiData = overlayUiData,
          modifier = Modifier.fillMaxSize(),
      )
    }
  }

  PlatformBackHandler(enabled = state.overlayVisible, onBack = actions.onCloseOverlay)
}

@Composable
private fun SearchBarShell(query: String, overlayVisible: Boolean, onToggleOverlay: () -> Unit) {
  Surface(
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = 2.dp,
      modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(modifier = Modifier.weight(1f)) {
        OutlinedTextField(
            value = query,
            onValueChange = {},
            readOnly = true,
            placeholder = { Text(stringResource(Res.string.search_bar_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            trailingIcon = {
              Icon(
                  imageVector =
                      if (overlayVisible) {
                        FaMaterialSymbols.AutoMirrored.Filled.ArrowBack
                      } else {
                        FaMaterialSymbols.Filled.Search
                      },
                  contentDescription =
                      if (overlayVisible) {
                        stringResource(Res.string.close_search_overlay)
                      } else {
                        stringResource(Res.string.open_search_overlay)
                      },
              )
            },
        )
        Box(modifier = Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onToggleOverlay))
      }
    }
  }
}
