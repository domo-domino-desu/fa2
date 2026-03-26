package me.domino.fa2.ui.pages.user.gallery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import me.domino.fa2.data.model.GalleryFolderGroup
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.ui.components.submission.SubmissionWaterfall
import me.domino.fa2.ui.components.submission.WaterfallLoadingSkeleton
import me.domino.fa2.ui.pages.user.profile.UserBodyScrollPosition
import me.domino.fa2.ui.pages.user.profile.UserChildRouteTabs
import me.domino.fa2.ui.pages.user.profile.UserSectionTopDefaults
import me.domino.fa2.ui.pages.user.profile.UserSharedTopScrollState
import me.domino.fa2.ui.pages.user.profile.handleUserSectionTabSelection
import me.domino.fa2.ui.pages.user.profile.resolveUserBodyScrollPosition
import me.domino.fa2.ui.pages.user.profile.resolveUserSharedTopScrollState
import me.domino.fa2.ui.pages.user.profile.shouldStickUserSectionTabs
import me.domino.fa2.ui.pages.user.profile.userSubmissionSectionScrollLayout
import me.domino.fa2.ui.pages.user.route.UserChildRoute
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** User 投稿子页。 */
@Composable
internal fun UserSubmissionSectionScreen(
    /** 子路由。 */
    route: UserChildRoute,
    /** 页面状态。 */
    state: UserSubmissionSectionUiState,
    /** 重试首页。 */
    onRetry: () -> Unit,
    /** 下拉刷新。 */
    onRefresh: () -> Unit,
    /** 打开投稿。 */
    onOpenSubmission: (SubmissionThumbnail) -> Unit,
    /** 打开文件夹。 */
    onOpenFolder: (String) -> Unit,
    /** 触底索引回调。 */
    onLastVisibleIndexChanged: (Int) -> Unit,
    /** 重试加载更多。 */
    onRetryLoadMore: () -> Unit,
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
    /** 列表头部（随滚动）。 */
    headerContent: (@Composable () -> Unit)? = null,
    /** 外部托管滚动状态（可选）。 */
    waterfallState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
) {
  val settingsService = koinInject<AppSettingsService>()
  val settings by settingsService.settings.collectAsState()
  val scope = rememberCoroutineScope()
  val sectionGridContentPadding =
      PaddingValues(start = 12.dp, top = 0.dp, end = 12.dp, bottom = 12.dp)
  val latestOnSharedTopScrollChanged = rememberUpdatedState(onSharedTopScrollChanged)
  val latestOnBodyScrollPositionChanged = rememberUpdatedState(onBodyScrollPositionChanged)
  val latestOnDeferredBodyScrollPositionConsumed =
      rememberUpdatedState(onDeferredBodyScrollPositionConsumed)
  val scrollLayout =
      remember(headerContent != null) {
        userSubmissionSectionScrollLayout(hasHeader = headerContent != null)
      }
  val onTabSelected: (UserChildRoute) -> Unit = { targetRoute ->
    handleUserSectionTabSelection(
        targetRoute = targetRoute,
        currentRoute = route,
        isAtTop = isStaggeredGridAtTop(waterfallState),
        onSelectRoute = onSelectRoute,
        onRefreshCurrentRoute = onRefresh,
        onScrollCurrentRouteToTop = { scope.launch { waterfallState.animateScrollToItem(0) } },
    )
  }

  LaunchedEffect(waterfallState) {
    snapshotFlow {
          waterfallState.firstVisibleItemIndex to waterfallState.firstVisibleItemScrollOffset
        }
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

  LaunchedEffect(waterfallState, deferredBodyScrollPosition, scrollLayout) {
    val pendingBodyScrollPosition =
        deferredBodyScrollPosition?.takeUnless { it.isAtStart } ?: return@LaunchedEffect
    snapshotFlow {
          resolveUserSharedTopScrollState(
              firstVisibleItemIndex = waterfallState.firstVisibleItemIndex,
              firstVisibleItemScrollOffset = waterfallState.firstVisibleItemScrollOffset,
              layout = scrollLayout,
          )
        }
        .distinctUntilChanged()
        .collect { sharedTopState ->
          if (sharedTopState == UserSharedTopScrollState.Sticky) {
            waterfallState.scrollToItem(
                index =
                    scrollLayout.bodyStartIndex + pendingBodyScrollPosition.firstVisibleItemIndex,
                scrollOffset = pendingBodyScrollPosition.firstVisibleItemScrollOffset,
            )
            latestOnDeferredBodyScrollPositionConsumed.value()
            return@collect
          }
        }
  }

  val tabsContent: @Composable () -> Unit = {
    UserChildRouteTabs(
        currentRoute = route,
        onSelectRoute = onTabSelected,
        horizontalPadding = UserSectionTopDefaults.tabsHorizontalPaddingInGrid,
    )
  }

  val topItemsContent: LazyStaggeredGridScope.() -> Unit = {
    item(key = "user-tabs-inline", span = StaggeredGridItemSpan.FullLine) { tabsContent() }

    if (state.folderGroups.isNotEmpty()) {
      item(key = "user-folder-groups", span = StaggeredGridItemSpan.FullLine) {
        UserFolderGroupsCard(groups = state.folderGroups, onOpenFolder = onOpenFolder)
      }
    }
  }

  val shouldStickTabs by
      remember(waterfallState, headerContent) {
        derivedStateOf {
          shouldStickUserSectionTabs(
              firstVisibleItemIndex = waterfallState.firstVisibleItemIndex,
              firstVisibleItemScrollOffset = waterfallState.firstVisibleItemScrollOffset,
              hasHeader = headerContent != null,
          )
        }
      }

  Box(modifier = Modifier.fillMaxSize()) {
    val blockingMessage =
        state.errorMessage?.takeIf { state.submissions.isEmpty() && it.isNotBlank() }
    when {
      state.loading && state.submissions.isEmpty() -> {
        WaterfallLoadingSkeleton(
            minCardWidthDp = settings.waterfallMinCardWidthDp,
            state = waterfallState,
            itemCount = 72,
            contentPadding = sectionGridContentPadding,
            headerContent = headerContent,
            preItemsContent = topItemsContent,
        )
      }

      !blockingMessage.isNullOrBlank() -> {
        UserSubmissionSectionSingleStateGrid(
            minCardWidthDp = settings.waterfallMinCardWidthDp,
            state = waterfallState,
            contentPadding = sectionGridContentPadding,
            headerContent = headerContent,
            preItemsContent = topItemsContent,
        ) {
          UserStatusCard(
              title = stringResource(Res.string.load_failed),
              body = blockingMessage,
              onRetry = onRetry,
          )
        }
      }

      else -> {
        val inlineError =
            state.errorMessage?.takeIf { value ->
              value.isNotBlank() && state.submissions.isNotEmpty()
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
              contentPadding = sectionGridContentPadding,
              blockedSubmissionMode = settings.blockedSubmissionWaterfallMode,
              headerContent = headerContent,
              preItemsContent = {
                topItemsContent()
                if (!inlineError.isNullOrBlank()) {
                  item(
                      key = "user-inline-error",
                      span = StaggeredGridItemSpan.FullLine,
                  ) {
                    UserStatusCard(
                        title = stringResource(Res.string.load_failed),
                        body = inlineError,
                        onRetry = onRetry,
                    )
                  }
                }
              },
          )
        }
      }
    }

    if (shouldStickTabs) {
      UserChildRouteTabs(
          currentRoute = route,
          onSelectRoute = onTabSelected,
          horizontalPadding = UserSectionTopDefaults.stickyTabsHorizontalPadding,
      )
    }
  }
}

@Composable
private fun UserSubmissionSectionSingleStateGrid(
    minCardWidthDp: Int,
    state: LazyStaggeredGridState,
    contentPadding: PaddingValues,
    headerContent: (@Composable () -> Unit)?,
    preItemsContent: LazyStaggeredGridScope.() -> Unit,
    content: @Composable () -> Unit,
) {
  LazyVerticalStaggeredGrid(
      state = state,
      columns = StaggeredGridCells.Adaptive(minCardWidthDp.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalItemSpacing = 12.dp,
      contentPadding = contentPadding,
      modifier = Modifier.fillMaxSize(),
  ) {
    if (headerContent != null) {
      item(
          key = "user-submission-single-state-header",
          span = StaggeredGridItemSpan.FullLine,
      ) {
        headerContent()
      }
    }
    preItemsContent()
    item(key = "user-submission-single-state-content", span = StaggeredGridItemSpan.FullLine) {
      content()
    }
  }
}

@Composable
private fun UserStatusCard(title: String, body: String, onRetry: () -> Unit) {
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
      Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserFolderGroupsCard(groups: List<GalleryFolderGroup>, onOpenFolder: (String) -> Unit) {
  Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    groups.forEach { group ->
      if (!group.title.isNullOrBlank()) {
        Text(
            text = group.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
      }
      FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        group.folders.forEach { folder ->
          Surface(
              modifier = Modifier.clickable { onOpenFolder(folder.url) },
              color =
                  if (folder.isActive) {
                    MaterialTheme.colorScheme.secondaryContainer
                  } else {
                    MaterialTheme.colorScheme.surfaceVariant
                  },
              shape = RoundedCornerShape(999.dp),
          ) {
            Text(
                text = folder.title,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (folder.isActive) {
                      MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                      MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
          }
        }
      }
    }
  }
}

private fun isStaggeredGridAtTop(state: LazyStaggeredGridState): Boolean =
    state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0
