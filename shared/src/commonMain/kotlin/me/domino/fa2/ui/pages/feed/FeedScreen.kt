package me.domino.fa2.ui.pages.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.ui.components.ExpressiveFilledTonalButton
import me.domino.fa2.ui.components.submission.SubmissionWaterfall
import me.domino.fa2.ui.components.submission.SubmissionWaterfallPageControls
import me.domino.fa2.ui.components.submission.SubmissionWaterfallViewportSnapshot
import me.domino.fa2.ui.components.submission.WaterfallLoadingSkeleton
import me.domino.fa2.ui.components.submission.WaterfallRefreshBox
import me.domino.fa2.ui.host.LocalAppSettings
import me.domino.fa2.ui.pages.submission.WaterfallScrollRequest
import org.jetbrains.compose.resources.stringResource

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
    /** 分页导航控件。 */
    pageControls: SubmissionWaterfallPageControls? = null,
    /** 顶部是否可加载上一页。 */
    canLoadPreviousPageAtTop: Boolean = false,
    /** 顶部上一页加载状态。 */
    loadingPreviousPage: Boolean = false,
    /** 顶部上一页加载错误。 */
    prependErrorMessage: String? = null,
    /** 顶部加载上一页。 */
    onLoadPreviousPageAtTop: (() -> Unit)? = null,
    /** 第一页。 */
    onLoadFirstPage: (() -> Unit)? = null,
    /** 上一页。 */
    onLoadPreviousPage: (() -> Unit)? = null,
    /** 跳页。 */
    onJumpToPage: ((Int) -> Unit)? = null,
    /** 下一页。 */
    onLoadNextPage: (() -> Unit)? = null,
    /** 最后一页。 */
    onLoadLastPage: (() -> Unit)? = null,
    /** 待消费滚动请求。 */
    pendingScrollRequest: WaterfallScrollRequest? = null,
    /** 消费滚动请求。 */
    onConsumeScrollRequest: ((Long) -> Unit)? = null,
    /** 视口变化。 */
    onViewportChanged: ((SubmissionWaterfallViewportSnapshot) -> Unit)? = null,
) {
  val settings = LocalAppSettings.current
  val refreshEnabled = pageControls?.showFirstPage != true || !pageControls.canLoadFirstPage

  Column(
      modifier = Modifier.fillMaxSize().testTag("feed-screen"),
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
        FeedStatusCard(
            title = stringResource(Res.string.load_failed),
            body = state.errorMessage.orEmpty(),
            onRetry = onRetry,
        )
        return
      }
    }

    state.errorMessage
        ?.takeIf { message -> message.isNotBlank() && state.submissions.isNotEmpty() }
        ?.let { inlineErrorMessage ->
          FeedStatusCard(
              title = stringResource(Res.string.load_failed),
              body = inlineErrorMessage,
              onRetry = onRetry,
          )
        }

    val waterfallContent: @Composable () -> Unit = {
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
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
      waterfallContent()
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
      ExpressiveFilledTonalButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
    }
  }
}
