package me.domino.fa2.ui.pages.user.journal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.JournalSummary
import me.domino.fa2.ui.components.PaginationRetryBar
import me.domino.fa2.ui.components.PaginationRetryDirection
import me.domino.fa2.ui.components.SkeletonBlock
import me.domino.fa2.ui.components.StatusSurface
import me.domino.fa2.ui.components.StatusSurfaceVariant
import me.domino.fa2.ui.components.accessibleClickableSummary
import me.domino.fa2.ui.pages.user.profile.UserBodyScrollPosition
import me.domino.fa2.ui.pages.user.profile.UserChildRouteTabs
import me.domino.fa2.ui.pages.user.profile.UserSectionTopDefaults
import me.domino.fa2.ui.pages.user.profile.UserSharedTopScrollState
import me.domino.fa2.ui.pages.user.profile.handleUserSectionTabSelection
import me.domino.fa2.ui.pages.user.profile.resolveUserBodyScrollPosition
import me.domino.fa2.ui.pages.user.profile.resolveUserSharedTopScrollState
import me.domino.fa2.ui.pages.user.profile.shouldStickUserSectionTabs
import me.domino.fa2.ui.pages.user.profile.userJournalsScrollLayout
import me.domino.fa2.ui.pages.user.route.UserChildRoute
import org.jetbrains.compose.resources.stringResource

/** Journals 子页。 */
@Composable
internal fun UserJournalsScreen(
    /** 页面状态。 */
    state: UserJournalsUiState,
    /** 打开日志详情。 */
    onOpenJournal: (JournalSummary) -> Unit,
    /** 重试首页。 */
    onRetry: () -> Unit,
    /** 下拉刷新。 */
    onRefresh: () -> Unit,
    /** 触底索引回调。 */
    onLastVisibleIndexChanged: (Int) -> Unit,
    /** 重试加载更多。 */
    onRetryLoadMore: () -> Unit,
    /** 当前路由。 */
    currentRoute: UserChildRoute,
    /** 切换路由。 */
    onSelectRoute: (UserChildRoute) -> Unit,
    /** 延迟恢复的内容区滚动。 */
    deferredBodyScrollPosition: UserBodyScrollPosition? = null,
    /** 延迟恢复已消费。 */
    onDeferredBodyScrollPositionConsumed: () -> Unit = {},
    /** 共享顶部滚动变化。 */
    onSharedTopScrollChanged: (UserSharedTopScrollState) -> Unit = {},
    /** 内容区滚动变化。 */
    onBodyScrollPositionChanged: (UserBodyScrollPosition) -> Unit = {},
    /** 列表状态。 */
    listState: LazyListState = rememberLazyListState(),
    /** 列表头部（随滚动）。 */
    headerContent: (@Composable () -> Unit)? = null,
) {
  val latestOnLastVisible = rememberUpdatedState(onLastVisibleIndexChanged)
  val latestOnSharedTopScrollChanged = rememberUpdatedState(onSharedTopScrollChanged)
  val latestOnBodyScrollPositionChanged = rememberUpdatedState(onBodyScrollPositionChanged)
  val latestOnDeferredBodyScrollPositionConsumed =
      rememberUpdatedState(onDeferredBodyScrollPositionConsumed)
  val scope = rememberCoroutineScope()
  val scrollLayout =
      remember(headerContent != null) {
        userJournalsScrollLayout(hasHeader = headerContent != null)
      }
  val onTabSelected: (UserChildRoute) -> Unit = { targetRoute ->
    handleUserSectionTabSelection(
        targetRoute = targetRoute,
        currentRoute = currentRoute,
        isAtTop = isListAtTop(listState),
        onSelectRoute = onSelectRoute,
        onRefreshCurrentRoute = onRefresh,
        onScrollCurrentRouteToTop = { scope.launch { listState.animateScrollToItem(0) } },
    )
  }

  LaunchedEffect(listState) {
    snapshotFlow { listState.layoutInfo.visibleItemsInfo.maxOfOrNull { info -> info.index } ?: 0 }
        .distinctUntilChanged()
        .collect { lastVisible -> latestOnLastVisible.value(lastVisible) }
  }

  LaunchedEffect(listState) {
    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
        .distinctUntilChanged()
        .collect { (index, offset) ->
          val sharedTopState =
              resolveUserSharedTopScrollState(
                  firstVisibleItemIndex = index,
                  firstVisibleItemScrollOffset = offset,
                  layout = scrollLayout,
              )
          latestOnSharedTopScrollChanged.value(sharedTopState)
          if (sharedTopState == UserSharedTopScrollState.Sticky) {
            latestOnBodyScrollPositionChanged.value(
                resolveUserBodyScrollPosition(
                    firstVisibleItemIndex = index,
                    firstVisibleItemScrollOffset = offset,
                    layout = scrollLayout,
                )
            )
          }
        }
  }

  LaunchedEffect(listState, deferredBodyScrollPosition, scrollLayout) {
    val pendingBodyScrollPosition =
        deferredBodyScrollPosition?.takeUnless { it.isAtStart } ?: return@LaunchedEffect
    snapshotFlow {
          resolveUserSharedTopScrollState(
              firstVisibleItemIndex = listState.firstVisibleItemIndex,
              firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
              layout = scrollLayout,
          )
        }
        .distinctUntilChanged()
        .collect { sharedTopState ->
          if (sharedTopState == UserSharedTopScrollState.Sticky) {
            listState.scrollToItem(
                index =
                    scrollLayout.bodyStartIndex + pendingBodyScrollPosition.firstVisibleItemIndex,
                scrollOffset = pendingBodyScrollPosition.firstVisibleItemScrollOffset,
            )
            latestOnDeferredBodyScrollPositionConsumed.value()
            return@collect
          }
        }
  }

  val tabsContent: @Composable (Modifier) -> Unit = { modifier ->
    UserChildRouteTabs(
        currentRoute = currentRoute,
        onSelectRoute = onTabSelected,
        modifier = modifier,
        horizontalPadding = UserSectionTopDefaults.tabsHorizontalPaddingInList,
    )
  }

  val shouldStickTabs by
      remember(listState, headerContent) {
        derivedStateOf {
          shouldStickUserSectionTabs(
              firstVisibleItemIndex = listState.firstVisibleItemIndex,
              firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
              hasHeader = headerContent != null,
          )
        }
      }

  Box(modifier = Modifier.fillMaxSize()) {
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
      LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (headerContent != null) {
          item(key = "user-header") {
            Box(modifier = Modifier.padding(horizontal = 12.dp)) { headerContent() }
          }
        }

        item(key = "user-tabs-inline") { tabsContent(Modifier) }

        when {
          state.loading && state.journals.isEmpty() -> {
            item(key = "journals-skeleton") { UserJournalsSkeleton() }
          }

          !state.errorMessage.isNullOrBlank() && state.journals.isEmpty() -> {
            item(key = "journals-blocking-error") {
              UserJournalsStatusCard(
                  title = stringResource(Res.string.load_failed),
                  body = state.errorMessage.orEmpty(),
                  onRetry = onRetry,
              )
            }
          }

          else -> {
            val inlineError =
                state.errorMessage?.takeIf { value ->
                  value.isNotBlank() && state.journals.isNotEmpty()
                }
            if (!inlineError.isNullOrBlank()) {
              item(key = "journals-inline-error") {
                UserJournalsStatusCard(
                    title = stringResource(Res.string.load_failed),
                    body = inlineError,
                    onRetry = onRetry,
                )
              }
            }

            items(items = state.journals, key = { item -> item.id }) { item ->
              JournalSummaryCard(item = item, onClick = { onOpenJournal(item) })
            }

            item(key = "journals-footer") {
              UserJournalsFooter(
                  hasMore = state.hasMore,
                  isLoadingMore = state.isLoadingMore,
                  appendErrorMessage = state.appendErrorMessage,
                  onRetryLoadMore = onRetryLoadMore,
              )
            }
          }
        }
      }
    }

    if (shouldStickTabs) {
      UserChildRouteTabs(
          currentRoute = currentRoute,
          onSelectRoute = onTabSelected,
          modifier = Modifier,
          horizontalPadding = UserSectionTopDefaults.stickyTabsHorizontalPadding,
      )
    }
  }
}

@Composable
private fun UserJournalsSkeleton() {
  val blockHeights = remember {
    List(18) { index ->
      when (index % 4) {
        0 -> 116.dp
        1 -> 94.dp
        2 -> 128.dp
        else -> 102.dp
      }
    }
  }
  Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    blockHeights.forEach { blockHeight ->
      SkeletonBlock(
          modifier = Modifier.fillMaxWidth().height(blockHeight),
          shape = RoundedCornerShape(14.dp),
      )
    }
  }
}

@Composable
private fun JournalSummaryCard(item: JournalSummary, onClick: () -> Unit) {
  val metadataText =
      "${item.timestampNatural} · ${stringResource(Res.string.comments_inline_count, item.commentCount)}"
  Card(
      modifier =
          Modifier.fillMaxWidth()
              .padding(horizontal = 12.dp)
              .clickable(onClick = onClick)
              .accessibleClickableSummary(
                  title = item.title,
                  subtitle = item.excerpt.ifBlank { metadataText },
              ),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border =
          BorderStroke(
              width = 1.dp,
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
          ),
  ) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
          text = item.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
      )
      Text(
          text = metadataText,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (item.excerpt.isNotBlank()) {
        Text(
            text = item.excerpt,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
        )
      }
    }
  }
}

@Composable
private fun UserJournalsFooter(
    hasMore: Boolean,
    isLoadingMore: Boolean,
    appendErrorMessage: String?,
    onRetryLoadMore: () -> Unit,
) {
  PaginationRetryBar(
      direction = PaginationRetryDirection.Append,
      canLoad = hasMore,
      loading = isLoadingMore,
      errorMessage = appendErrorMessage,
      onRetry = onRetryLoadMore,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
  )
}

@Composable
private fun UserJournalsStatusCard(title: String, body: String, onRetry: () -> Unit) {
  StatusSurface(
      title = title,
      body = body,
      modifier = Modifier.padding(horizontal = 12.dp),
      onAction = onRetry,
      variant = StatusSurfaceVariant.Section,
  )
}

private fun isListAtTop(listState: LazyListState): Boolean =
    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
