package me.domino.fa2.ui.pages.watchrecommendation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import fa2.shared.generated.resources.*
import kotlinx.coroutines.launch
import me.domino.fa2.application.watchrecommendation.RecommendedWatchUser
import me.domino.fa2.ui.components.ExpressiveFilledTonalButton
import me.domino.fa2.ui.components.ExpressiveIconButton
import me.domino.fa2.ui.components.UserHorizontalCard
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.ui.layouts.WatchRecommendationRouteTopBar
import me.domino.fa2.ui.navigation.goBackHome
import me.domino.fa2.ui.pages.user.route.UserChildRoute
import me.domino.fa2.ui.pages.user.route.UserRouteScreen
import me.domino.fa2.ui.pages.user.watchlist.WatchlistStatusCard
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
    val screenModel = koinScreenModel<WatchRecommendationScreenModel> { parametersOf(username) }
    val state by screenModel.state.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
      WatchRecommendationRouteTopBar(
          onBack = { navigator.pop() },
          onGoHome = { navigator.goBackHome() },
          onTitleClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
      )

      when (val snapshot = state) {
        WatchRecommendationUiState.Idle -> {
          WatchRecommendationIdleContent(onStart = screenModel::loadRecommendations)
        }

        WatchRecommendationUiState.Loading -> {
          WatchRecommendationLoadingContent()
        }

        is WatchRecommendationUiState.Error -> {
          LazyColumn(
              state = listState,
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
              listState = listState,
              onRefresh = screenModel::refreshRecommendations,
              onRetry = screenModel::loadRecommendations,
              onBlockUser = screenModel::blockRecommendation,
              onOpenUser = { user ->
                navigator.push(
                    UserRouteScreen(
                        username = user.user.username,
                        initialChildRoute = UserChildRoute.Gallery,
                    )
                )
              },
          )
        }
      }
    }
  }
}

@Composable
private fun WatchRecommendationIdleContent(onStart: () -> Unit) {
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
          text = stringResource(Res.string.following_recommendation_description),
          style = MaterialTheme.typography.bodyLarge,
      )
      ExpressiveFilledTonalButton(onClick = onStart) {
        Text(text = stringResource(Res.string.following_recommendation_action))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WatchRecommendationLoadingContent() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    LoadingIndicator(
        modifier = Modifier.fillMaxWidth(0.4f),
        color = MaterialTheme.colorScheme.primary,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchRecommendationSuccessContent(
    state: WatchRecommendationUiState.Success,
    listState: androidx.compose.foundation.lazy.LazyListState,
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

      if (state.users.isEmpty()) {
        item("watch-recommendation-empty") {
          WatchlistStatusCard(
              title = stringResource(Res.string.no_content),
              body = stringResource(Res.string.following_recommendation_empty),
              onRetry = onRetry,
          )
        }
      } else {
        items(
            items = state.users,
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
