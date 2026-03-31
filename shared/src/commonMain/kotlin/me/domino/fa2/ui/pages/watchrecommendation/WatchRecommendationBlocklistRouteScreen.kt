package me.domino.fa2.ui.pages.watchrecommendation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.following_recommendation_blocklist_empty
import fa2.shared.generated.resources.following_recommendation_unblock
import fa2.shared.generated.resources.load_failed
import fa2.shared.generated.resources.no_content
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.WatchlistUser
import me.domino.fa2.ui.components.ExpressiveIconButton
import me.domino.fa2.ui.components.UserHorizontalCard
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.ui.layouts.WatchRecommendationBlocklistRouteTopBar
import me.domino.fa2.ui.navigation.goBackHome
import me.domino.fa2.ui.pages.user.route.UserChildRoute
import me.domino.fa2.ui.pages.user.route.UserRouteScreen
import me.domino.fa2.ui.pages.user.watchlist.WatchlistSkeleton
import me.domino.fa2.ui.pages.user.watchlist.WatchlistStatusCard
import me.domino.fa2.util.FaUrls
import org.jetbrains.compose.resources.stringResource

class WatchRecommendationBlocklistRouteScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = koinScreenModel<WatchRecommendationBlocklistScreenModel>()
    val state by screenModel.state.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
      WatchRecommendationBlocklistRouteTopBar(
          onBack = { navigator.pop() },
          onGoHome = { navigator.goBackHome() },
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
            state.loading && state.usernames.isEmpty() -> {
              item("watch-recommendation-blocklist-skeleton") { WatchlistSkeleton() }
            }

            !state.errorMessage.isNullOrBlank() && state.usernames.isEmpty() -> {
              item("watch-recommendation-blocklist-error") {
                WatchlistStatusCard(
                    title = stringResource(Res.string.load_failed),
                    body = state.errorMessage.orEmpty(),
                    onRetry = { screenModel.load(forceRefresh = true) },
                )
              }
            }

            else -> {
              state.inlineErrorMessage
                  ?.takeIf { message -> message.isNotBlank() }
                  ?.let { message ->
                    item("watch-recommendation-blocklist-inline-error") {
                      WatchlistStatusCard(
                          title = stringResource(Res.string.load_failed),
                          body = message,
                          onRetry = { screenModel.load(forceRefresh = true) },
                      )
                    }
                  }

              if (state.usernames.isEmpty()) {
                item("watch-recommendation-blocklist-empty") {
                  WatchlistStatusCard(
                      title = stringResource(Res.string.no_content),
                      body = stringResource(Res.string.following_recommendation_blocklist_empty),
                      onRetry = { screenModel.load(forceRefresh = true) },
                  )
                }
              } else {
                items(
                    items = state.usernames,
                    key = { username -> username },
                ) { username ->
                  val user =
                      WatchlistUser(
                          username = username,
                          displayName = username,
                          profileUrl = FaUrls.user(username),
                      )
                  UserHorizontalCard(
                      user = user,
                      onClick = {
                        navigator.push(
                            UserRouteScreen(
                                username = username,
                                initialChildRoute = UserChildRoute.Gallery,
                            )
                        )
                      },
                      trailingContent = {
                        ExpressiveIconButton(
                            onClick = { screenModel.removeBlockedUsername(username) },
                            enabled = username !in state.removingUsernames,
                        ) {
                          Icon(
                              imageVector = FaMaterialSymbols.Filled.Close,
                              contentDescription =
                                  stringResource(Res.string.following_recommendation_unblock),
                          )
                        }
                      },
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
