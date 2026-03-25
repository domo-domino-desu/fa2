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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.ui.components.platform.PlatformBackHandler
import me.domino.fa2.ui.components.submission.SubmissionWaterfall
import me.domino.fa2.ui.components.submission.WaterfallLoadingSkeleton
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.ui.pages.search.component.SearchHint
import me.domino.fa2.ui.pages.search.component.SearchOverlayContent
import me.domino.fa2.ui.pages.search.component.SearchStatusCard
import org.koin.compose.koinInject

@Composable
fun SearchScreen(
    state: SearchUiState,
    actions: SearchScreenActions,
    waterfallState: LazyStaggeredGridState,
) {
  val settingsService = koinInject<AppSettingsService>()
  val settings by settingsService.settings.collectAsState()
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
        SearchHint(text = "点击搜索框设置条件后提交搜索。", modifier = Modifier.fillMaxSize())
      } else {
        when {
          state.loading && state.submissions.isEmpty() -> {
            WaterfallLoadingSkeleton(
                minCardWidthDp = settings.waterfallMinCardWidthDp,
                state = waterfallState,
                modifier = Modifier.fillMaxSize(),
            )
          }

          !state.errorMessage.isNullOrBlank() && state.submissions.isEmpty() -> {
            SearchStatusCard(
                title = "搜索失败",
                body = state.errorMessage.orEmpty(),
                onRetry = actions.onRetry,
            )
          }

          else -> {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = actions.onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
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
              )
            }
          }
        }
      }
    }

    if (state.overlayVisible) {
      SearchOverlayContent(
          form = state.draft,
          actions = actions,
          canSearch = canSearch,
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
            placeholder = { Text("输入关键字，例如 wolf @keywords female") },
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
                  contentDescription = if (overlayVisible) "关闭搜索遮罩" else "打开搜索遮罩",
              )
            },
        )
        Box(modifier = Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onToggleOverlay))
      }
    }
  }
}
