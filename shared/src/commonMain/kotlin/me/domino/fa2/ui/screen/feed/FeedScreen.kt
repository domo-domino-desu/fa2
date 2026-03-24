package me.domino.fa2.ui.screen.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.ui.component.SubmissionWaterfall
import me.domino.fa2.ui.component.WaterfallLoadingSkeleton
import org.koin.compose.koinInject

/** Feed 页面。 */
@Composable
fun FeedScreen(
  /** Feed 页面状态。 */
  state: FeedUiState,
  /** 重试首页回调。 */
  onRetry: () -> Unit,
  /** 下拉刷新回调。 */
  onRefresh: () -> Unit,
  /** 点击投稿回调。 */
  onOpenSubmission: (SubmissionThumbnail) -> Unit,
  /** 瀑布流可见索引变更回调。 */
  onLastVisibleIndexChanged: (Int) -> Unit,
  /** 手动重试加载更多回调。 */
  onRetryLoadMore: () -> Unit,
  /** 外部托管的瀑布流状态。 */
  waterfallState: LazyStaggeredGridState,
) {
  val settingsService = koinInject<AppSettingsService>()
  val settings by settingsService.settings.collectAsState()

  Column(
    modifier = Modifier.fillMaxSize().padding(top = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    when {
      state.loading && state.submissions.isEmpty() -> {
        WaterfallLoadingSkeleton(
          minCardWidthDp = settings.waterfallMinCardWidthDp,
          state = waterfallState,
        )
        return
      }

      !state.errorMessage.isNullOrBlank() && state.submissions.isEmpty() -> {
        FeedStatusCard(title = "加载失败", body = state.errorMessage.orEmpty(), onRetry = onRetry)
        return
      }
    }

    state.errorMessage
      ?.takeIf { message -> message.isNotBlank() && state.submissions.isNotEmpty() }
      ?.let { inlineErrorMessage ->
        FeedStatusCard(title = "加载失败", body = inlineErrorMessage, onRetry = onRetry)
      }

    PullToRefreshBox(
      isRefreshing = state.refreshing,
      onRefresh = onRefresh,
      modifier = Modifier.fillMaxSize(),
    ) {
      SubmissionWaterfall(
        items = state.submissions,
        onItemClick = onOpenSubmission,
        onLastVisibleIndexChanged = onLastVisibleIndexChanged,
        canLoadMore = state.hasMore,
        loadingMore = state.isLoadingMore,
        appendErrorMessage = state.appendErrorMessage,
        onRetryLoadMore = onRetryLoadMore,
        state = waterfallState,
        minCardWidthDp = settings.waterfallMinCardWidthDp,
        blockedSubmissionMode = settings.blockedSubmissionWaterfallMode,
      )
    }
  }
}

/** Feed 状态消息卡。 */
@Composable
private fun FeedStatusCard(title: String, body: String, onRetry: () -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(14.dp),
    border =
      BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
      ),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(text = title, style = MaterialTheme.typography.titleMedium)
      Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(onClick = onRetry) { Text("重试") }
    }
  }
}
