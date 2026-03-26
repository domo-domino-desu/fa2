package me.domino.fa2.ui.navigation

import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import me.domino.fa2.ui.components.PageStateWrapper
import me.domino.fa2.ui.pages.about.AboutRouteScreen
import me.domino.fa2.ui.pages.auth.AuthRouteScreen
import me.domino.fa2.ui.pages.browse.BrowseScreen
import me.domino.fa2.ui.pages.browse.BrowseScreenModel
import me.domino.fa2.ui.pages.feed.FeedScreen
import me.domino.fa2.ui.pages.feed.FeedScreenModel
import me.domino.fa2.ui.pages.history.SearchHistoryRouteScreen
import me.domino.fa2.ui.pages.history.SubmissionHistoryRouteScreen
import me.domino.fa2.ui.pages.more.MoreScreen
import me.domino.fa2.ui.pages.more.MoreScreenModel
import me.domino.fa2.ui.pages.more.MoreUiState
import me.domino.fa2.ui.pages.search.SearchScreen
import me.domino.fa2.ui.pages.search.SearchScreenActions
import me.domino.fa2.ui.pages.search.SearchScreenModel
import me.domino.fa2.ui.pages.settings.SettingsRouteScreen
import me.domino.fa2.ui.pages.user.route.UserChildRoute
import me.domino.fa2.ui.pages.user.route.UserRouteScreen
import org.koin.core.parameter.parametersOf

/** 登录后的主路由页面。 */
class MainRouteScreen(
    /** 当前用户名。 */
    private val username: String
) : Screen {
  /** 页面内容。 */
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val feedSubmissionListHolder =
        navigator.rememberNavigatorScreenModel<SubmissionListHolder>(
            tag = "submission-list-holder:feed"
        ) {
          SubmissionListHolder()
        }
    val browseSubmissionListHolder =
        navigator.rememberNavigatorScreenModel<SubmissionListHolder>(
            tag = "submission-list-holder:browse"
        ) {
          SubmissionListHolder()
        }
    val searchSubmissionListHolder =
        navigator.rememberNavigatorScreenModel<SubmissionListHolder>(
            tag = "submission-list-holder:search"
        ) {
          SubmissionListHolder()
        }
    val feedScreenModel =
        koinScreenModel<FeedScreenModel> { parametersOf(feedSubmissionListHolder) }
    val browseScreenModel =
        koinScreenModel<BrowseScreenModel> { parametersOf(browseSubmissionListHolder) }
    val searchScreenModel =
        koinScreenModel<SearchScreenModel> { parametersOf(searchSubmissionListHolder) }
    val moreScreenModel = koinScreenModel<MoreScreenModel> { parametersOf(username) }
    val feedState by feedScreenModel.state.collectAsState()
    val browseState by browseScreenModel.state.collectAsState()
    val searchState by searchScreenModel.state.collectAsState()
    val feedPageState by feedScreenModel.pageState.collectAsState()
    val browsePageState by browseScreenModel.pageState.collectAsState()
    val searchPageState by searchScreenModel.pageState.collectAsState()
    val moreState by moreScreenModel.state.collectAsState()
    val feedWaterfallState = rememberLazyStaggeredGridState()
    val browseWaterfallState = rememberLazyStaggeredGridState()
    val searchWaterfallState = rememberLazyStaggeredGridState()
    val coroutineScope = rememberCoroutineScope()
    val topLevelDestinationHolder =
        navigator.rememberNavigatorScreenModel<MainTopLevelDestinationHolder>(
            tag = "main-top-level-destination:${username.lowercase()}"
        ) {
          MainTopLevelDestinationHolder()
        }
    val currentTopLevelDestination = topLevelDestinationHolder.destination

    LaunchedEffect(Unit) { feedScreenModel.load() }

    LaunchedEffect(moreState) {
      val snapshot = moreState
      if (snapshot is MoreUiState.Ready && snapshot.loggedOut) {
        navigator.replaceAll(AuthRouteScreen())
      }
    }

    LaunchedEffect(currentTopLevelDestination) {
      if (currentTopLevelDestination == TopLevelDestination.MORE) {
        moreScreenModel.load()
      }
    }

    AppScaffold(
        currentTopLevelDestination = currentTopLevelDestination,
        onTopLevelDestinationClick = { destination, reselected ->
          if (!reselected) {
            topLevelDestinationHolder.destination = destination
            return@AppScaffold
          }
          when (destination) {
            TopLevelDestination.FEED -> {
              val atTop =
                  feedWaterfallState.firstVisibleItemIndex == 0 &&
                      feedWaterfallState.firstVisibleItemScrollOffset == 0
              if (atTop) {
                feedScreenModel.refresh()
              } else {
                coroutineScope.launch { feedWaterfallState.animateScrollToItem(0) }
              }
            }

            TopLevelDestination.BROWSE -> {
              val atTop =
                  browseWaterfallState.firstVisibleItemIndex == 0 &&
                      browseWaterfallState.firstVisibleItemScrollOffset == 0
              if (atTop) {
                browseScreenModel.refresh()
              } else {
                coroutineScope.launch { browseWaterfallState.animateScrollToItem(0) }
              }
            }

            TopLevelDestination.SEARCH -> {
              coroutineScope.launch { searchWaterfallState.animateScrollToItem(0) }
            }

            TopLevelDestination.MORE -> Unit
          }
        },
    ) {
      when (currentTopLevelDestination) {
        TopLevelDestination.FEED -> {
          PageStateWrapper(
              state = feedPageState,
              onRetry = { feedScreenModel.load(forceRefresh = true) },
          ) {
            FeedScreen(
                state = feedState,
                onRetry = { feedScreenModel.load(forceRefresh = true) },
                onRefresh = feedScreenModel::refresh,
                onOpenSubmission = { item ->
                  navigator.openSubmissionFromList(
                      sid = item.id,
                      holderTag = "submission-list-holder:feed",
                      onSelect = feedScreenModel::setCurrentSubmission,
                  )
                },
                onLastVisibleIndexChanged = feedScreenModel::onLastVisibleIndexChanged,
                onRetryLoadMore = feedScreenModel::retryLoadMore,
                waterfallState = feedWaterfallState,
            )
          }
        }

        TopLevelDestination.BROWSE -> {
          PageStateWrapper(
              state = browsePageState,
              onRetry = browseScreenModel::refresh,
          ) {
            BrowseScreen(
                state = browseState,
                onUpdateCategory = browseScreenModel::updateCategory,
                onUpdateType = browseScreenModel::updateType,
                onUpdateSpecies = browseScreenModel::updateSpecies,
                onUpdateGender = browseScreenModel::updateGender,
                onSetRatingGeneral = browseScreenModel::setRatingGeneral,
                onSetRatingMature = browseScreenModel::setRatingMature,
                onSetRatingAdult = browseScreenModel::setRatingAdult,
                onApplyFilter = browseScreenModel::applyFilter,
                onRefresh = browseScreenModel::refresh,
                onRetry = browseScreenModel::refresh,
                onOpenSubmission = { item ->
                  navigator.openSubmissionFromList(
                      sid = item.id,
                      holderTag = "submission-list-holder:browse",
                      onSelect = browseScreenModel::setCurrentSubmission,
                  )
                },
                onLastVisibleIndexChanged = browseScreenModel::onLastVisibleIndexChanged,
                onRetryLoadMore = browseScreenModel::retryLoadMore,
                waterfallState = browseWaterfallState,
            )
          }
        }

        TopLevelDestination.SEARCH -> {
          PageStateWrapper(
              state = searchPageState,
              onRetry = searchScreenModel::refresh,
          ) {
            SearchScreen(
                state = searchState,
                actions =
                    SearchScreenActions(
                        onOpenOverlay = searchScreenModel::openOverlay,
                        onCloseOverlay = searchScreenModel::closeOverlay,
                        onUpdateQuery = searchScreenModel::updateQuery,
                        onToggleGender = searchScreenModel::toggleGender,
                        onUpdateCategory = searchScreenModel::updateCategory,
                        onUpdateType = searchScreenModel::updateType,
                        onUpdateSpecies = searchScreenModel::updateSpecies,
                        onUpdateOrderBy = searchScreenModel::updateOrderBy,
                        onUpdateOrderDirection = searchScreenModel::updateOrderDirection,
                        onUpdateRange = searchScreenModel::updateRange,
                        onUpdateRangeFrom = searchScreenModel::updateRangeFrom,
                        onUpdateRangeTo = searchScreenModel::updateRangeTo,
                        onSetRatingGeneral = searchScreenModel::setRatingGeneral,
                        onSetRatingMature = searchScreenModel::setRatingMature,
                        onSetRatingAdult = searchScreenModel::setRatingAdult,
                        onSetTypeArt = searchScreenModel::setTypeArt,
                        onSetTypeMusic = searchScreenModel::setTypeMusic,
                        onSetTypeFlash = searchScreenModel::setTypeFlash,
                        onSetTypeStory = searchScreenModel::setTypeStory,
                        onSetTypePhoto = searchScreenModel::setTypePhoto,
                        onSetTypePoetry = searchScreenModel::setTypePoetry,
                        onApplySearch = {
                          coroutineScope.launch { searchWaterfallState.scrollToItem(0) }
                          searchScreenModel.applySearch()
                        },
                        onRefresh = searchScreenModel::refresh,
                        onRetry = searchScreenModel::refresh,
                        onOpenSubmission = { item ->
                          navigator.openSubmissionFromList(
                              sid = item.id,
                              holderTag = "submission-list-holder:search",
                              onSelect = searchScreenModel::setCurrentSubmission,
                          )
                        },
                        onLastVisibleIndexChanged = searchScreenModel::onLastVisibleIndexChanged,
                        onRetryLoadMore = searchScreenModel::retryLoadMore,
                    ),
                waterfallState = searchWaterfallState,
            )
          }
        }

        TopLevelDestination.MORE -> {
          MoreScreen(
              state = moreState,
              onOpenUser = {
                navigator.push(
                    UserRouteScreen(
                        username = username,
                        initialChildRoute = UserChildRoute.Gallery,
                    )
                )
              },
              onOpenSettings = { navigator.push(SettingsRouteScreen()) },
              onOpenSubmissionHistory = { navigator.push(SubmissionHistoryRouteScreen()) },
              onOpenSearchHistory = { navigator.push(SearchHistoryRouteScreen()) },
              onOpenAbout = { navigator.push(AboutRouteScreen()) },
              onLogout = moreScreenModel::logout,
          )
        }
      }
    }
  }
}

private class MainTopLevelDestinationHolder : ScreenModel {
  var destination by mutableStateOf(TopLevelDestination.FEED)
}
