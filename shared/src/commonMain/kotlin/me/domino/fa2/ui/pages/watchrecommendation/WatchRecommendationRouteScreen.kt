package me.domino.fa2.ui.pages.watchrecommendation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import fa2.shared.generated.resources.*
import kotlinx.coroutines.launch
import me.domino.fa2.domain.watchrecommendation.RecommendedWatchUser
import me.domino.fa2.ui.app.navigation.goBackHome
import me.domino.fa2.ui.app.scaffold.WatchRecommendationRouteTopBar
import me.domino.fa2.ui.components.ExpressiveFilledTonalButton
import me.domino.fa2.ui.components.ExpressiveIconButton
import me.domino.fa2.ui.components.user.UserHorizontalCard
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.ui.pages.user.route.UserPagerContextScreenModel
import me.domino.fa2.ui.pages.user.route.UserPagerRouteScreen
import me.domino.fa2.ui.pages.user.route.UserPagerSource
import me.domino.fa2.ui.pages.user.watchlist.WatchlistStatusCard
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf

class WatchRecommendationRouteScreen(
    private val username: String,
) : Screen {
  override val key: String = "watch-recommendation:${username.lowercase()}"

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val pagerContextId = remember(username) { "watch-recommendation:${username.lowercase()}" }
    val pagerContextScreenModel =
        navigator.rememberNavigatorScreenModel<UserPagerContextScreenModel>(
            tag = "user-pager-context:$pagerContextId"
        ) {
          UserPagerContextScreenModel()
        }
    val screenModel = koinScreenModel<WatchRecommendationScreenModel> { parametersOf(username) }
    val state by screenModel.state.collectAsState()
    val rankedListState = rememberLazyListState()
    val randomListState = rememberLazyListState()
    val activeListState =
        if ((state as? WatchRecommendationUiState.Success)?.useRandomOrder == true) {
          randomListState
        } else {
          rankedListState
        }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
      WatchRecommendationRouteTopBar(
          onBack = { navigator.pop() },
          onGoHome = { navigator.goBackHome() },
          showRandomOrder =
              (state as? WatchRecommendationUiState.Success)?.users?.isNotEmpty() == true,
          randomOrderEnabled =
              (state as? WatchRecommendationUiState.Success)?.useRandomOrder == true,
          onToggleRandomOrder = screenModel::toggleRandomOrder,
          onTitleClick = { coroutineScope.launch { activeListState.animateScrollToItem(0) } },
      )

      when (val snapshot = state) {
        WatchRecommendationUiState.Idle -> {
          RecommendationIdleContent(
              description = Res.string.following_recommendation_description,
              action = Res.string.following_recommendation_action,
              onStart = screenModel::loadRecommendations,
          )
        }

        is WatchRecommendationUiState.Loading -> {
          RecommendationLoadingContent(logLines = snapshot.logLines)
        }

        is WatchRecommendationUiState.Error -> {
          LazyColumn(
              state = rankedListState,
              modifier = Modifier.fillMaxSize().padding(top = 6.dp),
          ) {
            item("watch-recommendation-error") {
              WatchlistStatusCard(
                  title = stringResource(Res.string.load_failed),
                  body =
                      snapshot.message.ifBlank {
                        stringResource(Res.string.following_recommendation_load_failed)
                      },
                  onRetry = screenModel::loadRecommendations,
              )
            }
          }
        }

        is WatchRecommendationUiState.Success -> {
          WatchRecommendationSuccessContent(
              state = snapshot,
              listState = activeListState,
              emptyMessage = Res.string.following_recommendation_empty,
              onRefresh = screenModel::refreshRecommendations,
              onRetry = screenModel::loadRecommendations,
              onBlockUser = screenModel::blockRecommendation,
              onOpenUser = { user ->
                pagerContextScreenModel.seed(
                    source = UserPagerSource.Recommendation,
                    users = snapshot.visibleUsers.map { it.user },
                    selectedUsername = user.user.username,
                )
                navigator.push(UserPagerRouteScreen(user.user.username, pagerContextId))
              },
          )
        }
      }
    }
  }
}

@Composable
internal fun RecommendationIdleContent(
    description: StringResource,
    action: StringResource,
    onStart: () -> Unit,
) {
  Box(
      modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
      contentAlignment = Alignment.Center,
  ) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
          text = stringResource(description),
          style = MaterialTheme.typography.bodyLarge,
      )
      ExpressiveFilledTonalButton(onClick = onStart) { Text(text = stringResource(action)) }
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RecommendationLoadingContent(
    logLines: List<String>,
) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      LoadingIndicator(
          modifier = Modifier.fillMaxWidth(0.4f),
          color = MaterialTheme.colorScheme.primary,
      )
      if (logLines.isNotEmpty()) {
        RecommendationProgressLog(logLines = logLines)
      }
    }
  }
}

@Composable
private fun RecommendationProgressLog(logLines: List<String>) {
  Surface(
      modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surface,
      border =
          BorderStroke(
              width = 1.dp,
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
          ),
  ) {
    Text(
        text = logLines.joinToString(separator = "\n"),
        modifier = Modifier.padding(12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchRecommendationSuccessContent(
    state: WatchRecommendationUiState.Success,
    listState: androidx.compose.foundation.lazy.LazyListState,
    emptyMessage: StringResource,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onBlockUser: (String) -> Unit,
    onOpenUser: (RecommendedWatchUser) -> Unit,
) {
  PullToRefreshBox(
      isRefreshing = state.refreshing,
      onRefresh = onRefresh,
      modifier = Modifier.fillMaxSize(),
  ) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      state.inlineErrorMessage
          ?.takeIf { message -> message.isNotBlank() }
          ?.let { message ->
            item("watch-recommendation-inline-error") {
              WatchlistStatusCard(
                  title = stringResource(Res.string.load_failed),
                  body = message,
                  onRetry = onRetry,
              )
            }
          }

      if (state.visibleUsers.isEmpty()) {
        item("watch-recommendation-empty") {
          WatchlistStatusCard(
              title = stringResource(Res.string.no_content),
              body = stringResource(emptyMessage),
              onRetry = onRetry,
          )
        }
      } else {
        items(
            items = state.visibleUsers,
            key = { item -> item.user.username.lowercase() },
        ) { item ->
          UserHorizontalCard(
              user = item.user,
              onClick = { onOpenUser(item) },
              trailingContent = {
                val normalizedUsername = item.user.username.lowercase()
                ExpressiveIconButton(
                    onClick = { onBlockUser(item.user.username) },
                    enabled = normalizedUsername !in state.blockingUsernames,
                ) {
                  Icon(
                      imageVector = FaMaterialSymbols.Outlined.VisibilityOff,
                      contentDescription =
                          stringResource(Res.string.following_recommendation_block),
                  )
                }
              },
          )
        }
      }
    }
  }
}
