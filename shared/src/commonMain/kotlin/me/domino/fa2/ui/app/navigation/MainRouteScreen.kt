package me.domino.fa2.ui.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import kotlinx.serialization.Serializable
import me.domino.fa2.ui.pages.browse.BrowseFilterState
import me.domino.fa2.ui.pages.browse.BrowseRouteScreen
import me.domino.fa2.ui.pages.feed.FeedRouteScreen
import me.domino.fa2.ui.pages.more.MoreRootScreen
import me.domino.fa2.ui.pages.search.SearchRouteScreen

/** 登录后的轻量导航壳；每个顶层目标拥有独立 Voyager Navigator。 */
@Serializable
class MainRouteScreen(
    @Suppress("unused") private val deferInitialFeedLoad: Boolean = false,
) : AppScreen {
  @Composable
  override fun Content() {
    val controller = LocalFaTabNavigationController.current
    val stateHolder = rememberSaveableStateHolder()
    val selected = controller.selectedTab

    AppScaffold(
        currentTopLevelDestination = selected,
        showNavigation = controller.currentDepth == 1,
        onTopLevelDestinationClick = { destination, reselected ->
          if (!reselected) controller.select(destination)
        },
    ) {
      stateHolder.SaveableStateProvider(selected) {
        val root =
            remember(selected) {
              when (selected) {
                TopLevelDestination.FEED -> FeedRouteScreen()
                TopLevelDestination.BROWSE ->
                    BrowseRouteScreen(BrowseFilterState(), showTopBar = false)
                TopLevelDestination.SEARCH ->
                    SearchRouteScreen(initialQuery = "", showTopBar = false)
                TopLevelDestination.MORE -> MoreRootScreen()
              }
            }
        Navigator(root) { navigator ->
          controller.register(selected, navigator)
          controller.updateDepth(selected, navigator.size)
          CurrentScreen()
        }
      }
    }
  }
}
