package me.domino.fa2.ui.pages.overlays.watchrecommendation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
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
import me.domino.fa2.ui.app.navigation.goBackHome
import me.domino.fa2.ui.app.scaffold.SimilarUsersRouteTopBar
import me.domino.fa2.ui.pages.overlays.userpager.UserPagerContextScreenModel
import me.domino.fa2.ui.pages.overlays.userpager.UserPagerRouteScreen
import me.domino.fa2.ui.pages.overlays.userpager.UserPagerSource
import me.domino.fa2.ui.pages.overlays.userwatchlist.WatchlistStatusCard
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
    val pagerContextId = remember(username) { "similar-users:${username.lowercase()}" }
    val pagerContextScreenModel =
        navigator.rememberNavigatorScreenModel<UserPagerContextScreenModel>(
            tag = "user-pager-context:$pagerContextId"
        ) {
          UserPagerContextScreenModel()
        }
    val screenModel = koinScreenModel<SimilarUsersScreenModel> { parametersOf(username) }
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
      SimilarUsersRouteTopBar(
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
              description = Res.string.similar_users_description,
              action = Res.string.similar_users_action,
              onStart = screenModel::loadSimilarUsers,
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
              listState = activeListState,
              emptyMessage = Res.string.similar_users_empty,
              onRefresh = screenModel::refreshSimilarUsers,
              onRetry = screenModel::loadSimilarUsers,
              onBlockUser = screenModel::blockSimilarUser,
              onOpenUser = { user ->
                pagerContextScreenModel.seed(
                    source = UserPagerSource.SimilarUsers,
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
