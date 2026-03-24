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
import me.domino.fa2.data.repository.ActivityHistoryRepository
import me.domino.fa2.ui.screen.auth.AuthRouteScreen
import me.domino.fa2.ui.screen.browse.BrowseScreen
import me.domino.fa2.ui.screen.browse.BrowseScreenModel
import me.domino.fa2.ui.screen.feed.FeedScreen
import me.domino.fa2.ui.screen.feed.FeedScreenModel
import me.domino.fa2.ui.screen.feed.SubmissionRouteScreen
import me.domino.fa2.ui.screen.search.SearchScreen
import me.domino.fa2.ui.screen.search.SearchScreenActions
import me.domino.fa2.ui.screen.search.SearchScreenModel
import me.domino.fa2.ui.screen.user.UserChildRoute
import me.domino.fa2.ui.screen.user.UserRouteScreen
import org.koin.compose.koinInject
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
    val historyRepository = koinInject<ActivityHistoryRepository>()
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
          FeedScreen(
            state = feedState,
            onRetry = { feedScreenModel.load(forceRefresh = true) },
            onRefresh = feedScreenModel::refresh,
            onOpenSubmission = { item ->
              feedScreenModel.setCurrentSubmission(item.id)
              navigator.push(
                SubmissionRouteScreen(
                  initialSid = item.id,
                  holderTag = "submission-list-holder:feed",
                )
              )
            },
            onLastVisibleIndexChanged = feedScreenModel::onLastVisibleIndexChanged,
            onRetryLoadMore = feedScreenModel::retryLoadMore,
            waterfallState = feedWaterfallState,
          )
        }

        TopLevelDestination.BROWSE -> {
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
              browseScreenModel.setCurrentSubmission(item.id)
              navigator.push(
                SubmissionRouteScreen(
                  initialSid = item.id,
                  holderTag = "submission-list-holder:browse",
                )
              )
            },
            onLastVisibleIndexChanged = browseScreenModel::onLastVisibleIndexChanged,
            onRetryLoadMore = browseScreenModel::retryLoadMore,
            waterfallState = browseWaterfallState,
          )
        }

        TopLevelDestination.SEARCH -> {
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
                  val query = searchState.draft.query.trim()
                  searchScreenModel.applySearch()
                  if (query.isNotBlank()) {
                    coroutineScope.launch { historyRepository.recordSearchQuery(query) }
                  }
                },
                onRefresh = searchScreenModel::refresh,
                onRetry = searchScreenModel::refresh,
                onOpenSubmission = { item ->
                  searchScreenModel.setCurrentSubmission(item.id)
                  navigator.push(
                    SubmissionRouteScreen(
                      initialSid = item.id,
                      holderTag = "submission-list-holder:search",
                    )
                  )
                },
                onLastVisibleIndexChanged = searchScreenModel::onLastVisibleIndexChanged,
                onRetryLoadMore = searchScreenModel::retryLoadMore,
              ),
            waterfallState = searchWaterfallState,
          )
        }

        TopLevelDestination.MORE -> {
          MoreScreen(
            state = moreState,
            onOpenUser = {
              navigator.push(
                UserRouteScreen(username = username, initialChildRoute = UserChildRoute.Gallery)
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
