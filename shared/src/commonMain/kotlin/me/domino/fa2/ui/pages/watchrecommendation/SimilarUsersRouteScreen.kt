package me.domino.fa2.ui.pages.watchrecommendation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
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
import fa2.shared.generated.resources.load_failed
import fa2.shared.generated.resources.similar_users_action
import fa2.shared.generated.resources.similar_users_description
import fa2.shared.generated.resources.similar_users_empty
import fa2.shared.generated.resources.similar_users_load_failed
import kotlinx.coroutines.launch
import me.domino.fa2.ui.layouts.SimilarUsersRouteTopBar
import me.domino.fa2.ui.navigation.goBackHome
import me.domino.fa2.ui.pages.user.route.UserChildRoute
import me.domino.fa2.ui.pages.user.route.UserRouteScreen
import me.domino.fa2.ui.pages.user.watchlist.WatchlistStatusCard
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf

class SimilarUsersRouteScreen(
    private val username: String,
) : Screen {
  override val key: String = "similar-users:${username.lowercase()}"

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = koinScreenModel<SimilarUsersScreenModel> { parametersOf(username) }
    val state by screenModel.state.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
      SimilarUsersRouteTopBar(
          onBack = { navigator.pop() },
          onGoHome = { navigator.goBackHome() },
          onTitleClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
      )

      when (val snapshot = state) {
        WatchRecommendationUiState.Idle -> {
          RecommendationIdleContent(
              description = Res.string.similar_users_description,
              action = Res.string.similar_users_action,
              onStart = screenModel::loadSimilarUsers,
          )
        }

        WatchRecommendationUiState.Loading -> {
          RecommendationLoadingContent()
        }

        is WatchRecommendationUiState.Error -> {
          LazyColumn(
              state = listState,
              modifier = Modifier.fillMaxSize().padding(top = 6.dp),
          ) {
            item("similar-users-error") {
              WatchlistStatusCard(
                  title = stringResource(Res.string.load_failed),
                  body =
                      snapshot.message.ifBlank {
                        stringResource(Res.string.similar_users_load_failed)
                      },
                  onRetry = screenModel::loadSimilarUsers,
              )
            }
          }
        }

        is WatchRecommendationUiState.Success -> {
          WatchRecommendationSuccessContent(
              state = snapshot,
              listState = listState,
              emptyMessage = Res.string.similar_users_empty,
              onRefresh = screenModel::refreshSimilarUsers,
              onRetry = screenModel::loadSimilarUsers,
              onBlockUser = screenModel::blockSimilarUser,
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
