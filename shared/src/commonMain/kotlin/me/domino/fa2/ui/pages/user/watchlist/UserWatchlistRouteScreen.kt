package me.domino.fa2.ui.pages.user

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.distinctUntilChanged
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistUser
import me.domino.fa2.ui.components.NetworkImage
import me.domino.fa2.ui.components.SkeletonBlock
import me.domino.fa2.ui.layouts.UserWatchlistRouteTopBar
import me.domino.fa2.ui.navigation.goBackHome
import me.domino.fa2.util.FaUrls
import org.koin.core.parameter.parametersOf

/** 用户关注列表路由页面。 */
class UserWatchlistRouteScreen(
  private val username: String,
  private val category: WatchlistCategory,
  private val initialUrl: String? = null,
) : Screen {
  override val key: String =
    "user-watchlist:${username.lowercase()}:${category.name.lowercase()}:${initialUrl.orEmpty()}"

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel =
      koinScreenModel<UserWatchlistScreenModel> { parametersOf(username, category, initialUrl) }
    val state by screenModel.state.collectAsState()
    val listState: LazyListState = rememberLazyListState()
    val latestOnLastVisible = rememberUpdatedState(screenModel::onLastVisibleIndexChanged)
    val shareUrl =
      when (category) {
        WatchlistCategory.WatchedBy -> FaUrls.watchlistTo(username)
        WatchlistCategory.Watching -> FaUrls.watchlistBy(username)
      }

    LaunchedEffect(listState) {
      snapshotFlow { listState.layoutInfo.visibleItemsInfo.maxOfOrNull { info -> info.index } ?: 0 }
        .distinctUntilChanged()
        .collect { lastIndex -> latestOnLastVisible.value(lastIndex) }
    }
    val topBarTitle =
      when (category) {
        WatchlistCategory.WatchedBy -> "关注者"
        WatchlistCategory.Watching -> "已关注"
      }

    Column(modifier = Modifier.fillMaxSize()) {
      UserWatchlistRouteTopBar(
        title = topBarTitle,
        onBack = { navigator.pop() },
        onGoHome = { navigator.goBackHome() },
        shareUrl = shareUrl,
      )

      PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { screenModel.load(forceRefresh = true) },
        modifier = Modifier.fillMaxSize(),
      ) {
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize().padding(top = 6.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          when {
            state.loading && state.users.isEmpty() -> {
              item(key = "watchlist-skeleton") { WatchlistSkeleton() }
            }

            !state.errorMessage.isNullOrBlank() && state.users.isEmpty() -> {
              item(key = "watchlist-error") {
                WatchlistStatusCard(
                  title = "加载失败",
                  body = state.errorMessage.orEmpty(),
                  onRetry = { screenModel.load(forceRefresh = true) },
                )
              }
            }

            else -> {
              val inlineError =
                state.errorMessage?.takeIf { value ->
                  value.isNotBlank() && state.users.isNotEmpty()
                }
              if (!inlineError.isNullOrBlank()) {
                item(key = "watchlist-inline-error") {
                  WatchlistStatusCard(
                    title = "加载失败",
                    body = inlineError,
                    onRetry = { screenModel.load(forceRefresh = true) },
                  )
                }
              }

              items(items = state.users, key = { item -> item.username.lowercase() }) { item ->
                WatchlistUserCard(
                  item = item,
                  onClick = {
                    navigator.push(
                      UserRouteScreen(
                        username = item.username,
                        initialChildRoute = UserChildRoute.Gallery,
                      )
                    )
                  },
                )
              }

              item(key = "watchlist-footer") {
                WatchlistFooter(
                  hasMore = state.hasMore,
                  isLoadingMore = state.isLoadingMore,
                  appendErrorMessage = state.appendErrorMessage,
                  onRetryLoadMore = screenModel::retryLoadMore,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun WatchlistSkeleton() {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    repeat(8) {
      SkeletonBlock(
        modifier = Modifier.fillMaxWidth().height(76.dp),
        shape = RoundedCornerShape(14.dp),
      )
    }
  }
}

@Composable
private fun WatchlistUserCard(item: WatchlistUser, onClick: () -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.size(42.dp),
      ) {
        NetworkImage(
          url = "https://a.furaffinity.net/${item.username.lowercase()}.gif",
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
          showLoadingPlaceholder = true,
        )
      }
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = item.displayName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "~${item.username}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun WatchlistFooter(
  hasMore: Boolean,
  isLoadingMore: Boolean,
  appendErrorMessage: String?,
  onRetryLoadMore: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text =
          when {
            isLoadingMore -> "正在自动加载更多用户"
            !appendErrorMessage.isNullOrBlank() && hasMore -> "自动加载失败，可手动加载下一页"
            hasMore -> "继续浏览将自动加载下一页"
            else -> "已经到达末页"
          },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (hasMore && !isLoadingMore) {
        AssistChip(
          onClick = onRetryLoadMore,
          label = { Text(text = if (appendErrorMessage.isNullOrBlank()) "加载下一页" else "手动加载") },
        )
      }
    }
  }
}

@Composable
private fun WatchlistStatusCard(title: String, body: String, onRetry: () -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
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
