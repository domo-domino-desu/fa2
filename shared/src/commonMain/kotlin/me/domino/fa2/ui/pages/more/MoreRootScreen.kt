package me.domino.fa2.ui.pages.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.serialization.Serializable
import me.domino.fa2.ui.app.navigation.AppScreen
import me.domino.fa2.ui.pages.about.AboutRouteScreen
import me.domino.fa2.ui.pages.history.SearchHistoryRouteScreen
import me.domino.fa2.ui.pages.history.SubmissionHistoryRouteScreen
import me.domino.fa2.ui.pages.settings.SettingsRouteScreen
import me.domino.fa2.ui.pages.user.UserChildRoute
import me.domino.fa2.ui.pages.user.UserRouteScreen
import me.domino.fa2.ui.pages.user.userHeaderNavigationActions
import me.domino.fa2.ui.pages.watchrecommendation.WatchRecommendationRouteScreen

@Serializable
class MoreRootScreen : AppScreen {
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = koinScreenModel<MoreScreenModel>()
    val state by screenModel.state.collectAsState()
    val ready = state as? MoreUiState.Ready

    LaunchedEffect(Unit) { screenModel.load() }
    MoreScreen(
        state = state,
        onOpenUser =
            ready?.username?.takeIf(String::isNotBlank)?.let { username ->
              { navigator.push(UserRouteScreen(username, UserChildRoute.Gallery)) }
            },
        headerNavigationActions =
            ready?.username?.takeIf(String::isNotBlank)?.let { username ->
              userHeaderNavigationActions(username, ready.userHeader, navigator)
            },
        onOpenWatchRecommendations =
            ready?.username?.takeIf(String::isNotBlank)?.let { username ->
              { navigator.push(WatchRecommendationRouteScreen(username)) }
            },
        onOpenSettings = { navigator.push(SettingsRouteScreen()) },
        onOpenSubmissionHistory = { navigator.push(SubmissionHistoryRouteScreen()) },
        onOpenSearchHistory = { navigator.push(SearchHistoryRouteScreen()) },
        onOpenAbout = { navigator.push(AboutRouteScreen()) },
        onLogout = screenModel::logout,
    )
  }
}
