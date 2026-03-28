package me.domino.fa2.ui.pages.user.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import fa2.shared.generated.resources.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.ui.layouts.UserWatchlistRouteTopBar
import me.domino.fa2.ui.navigation.goBackHome
import me.domino.fa2.ui.pages.user.route.UserChildRoute
import me.domino.fa2.ui.pages.user.route.UserRouteScreen
import me.domino.fa2.util.FaUrls
import org.jetbrains.compose.resources.stringResource
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
    val coroutineScope = rememberCoroutineScope()
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
          WatchlistCategory.WatchedBy -> stringResource(Res.string.followers)
          WatchlistCategory.Watching -> stringResource(Res.string.following)
        }

    Column(modifier = Modifier.fillMaxSize()) {
      UserWatchlistRouteTopBar(
          title = topBarTitle,
          onBack = { navigator.pop() },
          onGoHome = { navigator.goBackHome() },
          shareUrl = shareUrl,
          onTitleClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
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
                    title = stringResource(Res.string.load_failed),
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
                      title = stringResource(Res.string.load_failed),
                      body = inlineError,
                      onRetry = { screenModel.load(forceRefresh = true) },
                  )
                }
              }

              items(
                  items = state.users,
                  key = { item -> item.username.lowercase() },
              ) { item ->
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
                isLoadingMore -> stringResource(Res.string.loading_more_content)
                !appendErrorMessage.isNullOrBlank() && hasMore ->
                    stringResource(Res.string.auto_load_failed_manual_next_page)
                hasMore -> stringResource(Res.string.continue_auto_load_next_page)
                else -> stringResource(Res.string.reached_end)
              },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (hasMore && !isLoadingMore) {
        AssistChip(
            onClick = onRetryLoadMore,
            label = {
              Text(
                  text =
                      if (appendErrorMessage.isNullOrBlank()) {
                        stringResource(Res.string.load_next_page)
                      } else {
                        stringResource(Res.string.manual_load)
                      }
              )
            },
        )
      }
    }
  }
}
