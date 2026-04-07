package me.domino.fa2.ui.navigation

import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import me.domino.fa2.application.auth.PendingFaRouteStore
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.repository.BrowseRepository
import me.domino.fa2.data.repository.FeedRepository
import me.domino.fa2.data.repository.SearchRepository
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
import me.domino.fa2.ui.pages.search.buildSearchUrl
import me.domino.fa2.ui.pages.settings.SettingsRouteScreen
import me.domino.fa2.ui.pages.submission.BrowseSubmissionSourceAdapter
import me.domino.fa2.ui.pages.submission.FeedSubmissionSourceAdapter
import me.domino.fa2.ui.pages.submission.SearchSubmissionSourceAdapter
import me.domino.fa2.ui.pages.submission.SubmissionContextScreenModel
import me.domino.fa2.ui.pages.submission.SubmissionContextSourceKind
import me.domino.fa2.ui.pages.submission.SubmissionLoadedPage
import me.domino.fa2.ui.pages.submission.WaterfallViewportState
import me.domino.fa2.ui.pages.submission.pageNumberForSid
import me.domino.fa2.ui.pages.submission.toWaterfallPageControls
import me.domino.fa2.ui.pages.user.route.UserChildRoute
import me.domino.fa2.ui.pages.user.route.UserRouteScreen
import me.domino.fa2.ui.pages.user.watchlist.UserWatchlistRouteScreen
import me.domino.fa2.ui.pages.watchrecommendation.WatchRecommendationRouteScreen
import me.domino.fa2.util.FaUrls
import org.koin.compose.koinInject

/** 登录后的主路由页面。 */
class MainRouteScreen(
    /** 是否延后 Feed 首次加载。 */
    private val deferInitialFeedLoad: Boolean = false,
) : Screen {
  /** 页面内容。 */
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val contextScreenModel =
        navigator.rememberNavigatorScreenModel<SubmissionContextScreenModel>(
            tag = "submission-context"
        ) {
          SubmissionContextScreenModel()
        }
    val feedRepository = koinInject<FeedRepository>()
    val browseRepository = koinInject<BrowseRepository>()
    val searchRepository = koinInject<SearchRepository>()
    val feedContextId = "submission-list-holder:feed"
    val browseContextId = "submission-list-holder:browse"
    val searchContextId = "submission-list-holder:search"
    val feedScreenModel = koinScreenModel<FeedScreenModel>()
    val browseScreenModel = koinScreenModel<BrowseScreenModel>()
    val searchScreenModel = koinScreenModel<SearchScreenModel>()
    val moreScreenModel = koinScreenModel<MoreScreenModel>()
    val pendingFaRouteStore = koinInject<PendingFaRouteStore>()
    val feedState by feedScreenModel.state.collectAsState()
    val browseState by browseScreenModel.state.collectAsState()
    val searchState by searchScreenModel.state.collectAsState()
    val feedPageState by feedScreenModel.pageState.collectAsState()
    val browsePageState by browseScreenModel.pageState.collectAsState()
    val searchPageState by searchScreenModel.pageState.collectAsState()
    val feedContextState by contextScreenModel.state(feedContextId).collectAsState()
    val browseContextState by contextScreenModel.state(browseContextId).collectAsState()
    val searchContextState by contextScreenModel.state(searchContextId).collectAsState()
    val moreState by moreScreenModel.state.collectAsState()
    val pendingRestoreUri by pendingFaRouteStore.pendingUri.collectAsState()
    var feedAutoLoadUnlocked by
        remember(deferInitialFeedLoad) { mutableStateOf(!deferInitialFeedLoad) }
    val initialFeedViewport =
        remember(contextScreenModel, feedContextId) {
          contextScreenModel.snapshot(feedContextId)?.waterfallViewport ?: WaterfallViewportState()
        }
    val initialBrowseViewport =
        remember(contextScreenModel, browseContextId) {
          contextScreenModel.snapshot(browseContextId)?.waterfallViewport
              ?: WaterfallViewportState()
        }
    val initialSearchViewport =
        remember(contextScreenModel, searchContextId) {
          contextScreenModel.snapshot(searchContextId)?.waterfallViewport
              ?: WaterfallViewportState()
        }
    val feedWaterfallState =
        rememberLazyStaggeredGridState(
            initialFirstVisibleItemIndex = initialFeedViewport.firstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset = initialFeedViewport.firstVisibleItemScrollOffset,
        )
    val browseWaterfallState =
        rememberLazyStaggeredGridState(
            initialFirstVisibleItemIndex = initialBrowseViewport.firstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset =
                initialBrowseViewport.firstVisibleItemScrollOffset,
        )
    val searchWaterfallState =
        rememberLazyStaggeredGridState(
            initialFirstVisibleItemIndex = initialSearchViewport.firstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset =
                initialSearchViewport.firstVisibleItemScrollOffset,
        )
    val coroutineScope = rememberCoroutineScope()
    val topLevelDestinationHolder =
        navigator.rememberNavigatorScreenModel<MainTopLevelDestinationHolder>(
            tag = "main-top-level-destination"
        ) {
          MainTopLevelDestinationHolder()
        }
    val currentTopLevelDestination = topLevelDestinationHolder.destination

    LaunchedEffect(pendingRestoreUri) {
      if (pendingRestoreUri == null) {
        feedAutoLoadUnlocked = true
      }
    }

    LaunchedEffect(
        currentTopLevelDestination,
        pendingRestoreUri,
        feedState.submissions,
        feedAutoLoadUnlocked,
    ) {
      if (currentTopLevelDestination != TopLevelDestination.FEED) return@LaunchedEffect
      if (feedState.submissions.isNotEmpty()) return@LaunchedEffect
      if (pendingRestoreUri != null) return@LaunchedEffect
      if (!feedAutoLoadUnlocked) return@LaunchedEffect
      feedScreenModel.load()
    }

    LaunchedEffect(feedState.submissions, feedState.nextPageUrl) {
      if (feedState.submissions.isEmpty()) return@LaunchedEffect
      contextScreenModel.syncRootPage(
          contextId = feedContextId,
          sourceKind = SubmissionContextSourceKind.FEED,
          adapter = FeedSubmissionSourceAdapter(feedRepository),
          page =
              SubmissionLoadedPage(
                  pageId = FaUrls.submissions(),
                  requestKey = FaUrls.submissions(),
                  items = feedState.submissions,
                  nextRequestKey = feedState.nextPageUrl,
                  firstRequestKey = FaUrls.submissions(),
              ),
          revisionKey = FaUrls.submissions(),
      )
    }
    LaunchedEffect(browseState.appliedFilter, browseState.submissions, browseState.nextPageUrl) {
      if (browseState.submissions.isEmpty()) return@LaunchedEffect
      val firstUrl =
          FaUrls.browse(
              cat = browseState.appliedFilter.category,
              atype = browseState.appliedFilter.type,
              species = browseState.appliedFilter.species,
              gender = browseState.appliedFilter.gender,
              ratingGeneral = browseState.appliedFilter.ratingGeneral,
              ratingMature = browseState.appliedFilter.ratingMature,
              ratingAdult = browseState.appliedFilter.ratingAdult,
          )
      contextScreenModel.syncRootPage(
          contextId = browseContextId,
          sourceKind = SubmissionContextSourceKind.BROWSE,
          adapter = BrowseSubmissionSourceAdapter(browseRepository, firstPageUrl = firstUrl),
          page =
              SubmissionLoadedPage(
                  pageId = firstUrl,
                  requestKey = firstUrl,
                  items = browseState.submissions,
                  pageNumber = 1,
                  nextRequestKey = browseState.nextPageUrl,
                  firstRequestKey = firstUrl,
              ),
          revisionKey = firstUrl,
      )
    }
    LaunchedEffect(
        searchState.applied,
        searchState.submissions,
        searchState.nextPageUrl,
        searchState.totalCount,
    ) {
      val applied = searchState.applied ?: return@LaunchedEffect
      if (searchState.submissions.isEmpty()) return@LaunchedEffect
      val firstUrl = buildSearchUrl(applied, page = 1)
      val lastPageNumber =
          searchState.totalCount?.let { totalCount -> ((minOf(totalCount, 5000) - 1) / 72) + 1 }
      contextScreenModel.syncRootPage(
          contextId = searchContextId,
          sourceKind = SubmissionContextSourceKind.SEARCH,
          adapter = SearchSubmissionSourceAdapter(searchRepository, firstPageUrl = firstUrl),
          page =
              SubmissionLoadedPage(
                  pageId = firstUrl,
                  requestKey = firstUrl,
                  items = searchState.submissions,
                  pageNumber = 1,
                  nextRequestKey = searchState.nextPageUrl,
                  firstRequestKey = firstUrl,
                  lastRequestKey =
                      lastPageNumber?.let { pageNumber ->
                        buildSearchUrl(applied, page = pageNumber)
                      },
                  lastPageNumber = lastPageNumber,
                  totalCount = searchState.totalCount,
              ),
          revisionKey = firstUrl,
      )
    }

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

    val feedDisplayState =
        feedContextState?.let { snapshot ->
          feedState.copy(
              submissions = snapshot.flatItems.ifEmpty { feedState.submissions },
              nextPageUrl = if (snapshot.hasNextPage) "context:next" else null,
              isLoadingMore = snapshot.loading.appendLoading,
              appendErrorMessage = snapshot.loading.appendErrorMessage,
          )
        } ?: feedState
    val browseDisplayState =
        browseContextState?.let { snapshot ->
          browseState.copy(
              submissions = snapshot.flatItems.ifEmpty { browseState.submissions },
              nextPageUrl = if (snapshot.hasNextPage) "context:next" else null,
              isLoadingMore = snapshot.loading.appendLoading,
              appendErrorMessage = snapshot.loading.appendErrorMessage,
          )
        } ?: browseState
    val searchDisplayState =
        searchContextState?.let { snapshot ->
          searchState.copy(
              submissions = snapshot.flatItems.ifEmpty { searchState.submissions },
              nextPageUrl = if (snapshot.hasNextPage) "context:next" else null,
              isLoadingMore = snapshot.loading.appendLoading,
              appendErrorMessage = snapshot.loading.appendErrorMessage,
          )
        } ?: searchState
    val feedPageControls = feedContextState?.toWaterfallPageControls()
    val browsePageControls = browseContextState?.toWaterfallPageControls()
    val searchPageControls = searchContextState?.toWaterfallPageControls()

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
              hasContent = feedDisplayState.submissions.isNotEmpty(),
              onRetry = { feedScreenModel.load(forceRefresh = true) },
          ) {
            FeedScreen(
                state = feedDisplayState,
                onRetry = { feedScreenModel.load(forceRefresh = true) },
                onRefresh = feedScreenModel::refresh,
                onOpenSubmission = { item ->
                  contextScreenModel.selectSubmission(feedContextId, item.id)
                  navigator.openSubmissionFromList(sid = item.id, contextId = feedContextId)
                },
                onLastVisibleIndexChanged = { lastVisibleIndex ->
                  val items = feedContextState?.flatItems ?: feedDisplayState.submissions
                  if (items.isNotEmpty() && lastVisibleIndex > items.lastIndex - 10) {
                    contextScreenModel.loadNextPageIfNeeded(feedContextId)
                  }
                },
                onRetryLoadMore = {
                  contextScreenModel.loadNextPageIfNeeded(feedContextId, force = true)
                },
                waterfallState = feedWaterfallState,
                pageControls = feedPageControls,
                canLoadPreviousPageAtTop = feedContextState?.hasPreviousPage == true,
                loadingPreviousPage = feedContextState?.loading?.prependLoading == true,
                prependErrorMessage = feedContextState?.loading?.prependErrorMessage,
                onLoadPreviousPageAtTop = {
                  contextScreenModel.loadPreviousPageIfNeeded(feedContextId, force = true)
                },
                onLoadFirstPage = { contextScreenModel.navigateToFirstPage(feedContextId) },
                onLoadPreviousPage = { contextScreenModel.navigateToPreviousPage(feedContextId) },
                onJumpToPage = { pageNumber ->
                  contextScreenModel.navigateToPage(feedContextId, pageNumber)
                },
                onLoadNextPage = { contextScreenModel.navigateToNextPage(feedContextId) },
                onLoadLastPage = { contextScreenModel.navigateToLastPage(feedContextId) },
                pendingScrollRequest = feedContextState?.waterfallViewport?.scrollRequest,
                onConsumeScrollRequest = { version ->
                  contextScreenModel.consumeWaterfallScrollRequest(feedContextId, version)
                },
                onViewportChanged = { viewport ->
                  contextScreenModel.updateWaterfallViewport(
                      contextId = feedContextId,
                      firstVisibleItemIndex = viewport.firstVisibleItemIndex,
                      firstVisibleItemScrollOffset = viewport.firstVisibleItemScrollOffset,
                      anchorSid = viewport.anchorSid,
                      currentPageNumber = feedContextState?.pageNumberForSid(viewport.anchorSid),
                  )
                },
            )
          }
        }

        TopLevelDestination.BROWSE -> {
          PageStateWrapper(
              state = browsePageState,
              hasContent = browseDisplayState.submissions.isNotEmpty(),
              onRetry = browseScreenModel::refresh,
          ) {
            BrowseScreen(
                state = browseDisplayState,
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
                  contextScreenModel.selectSubmission(browseContextId, item.id)
                  navigator.openSubmissionFromList(sid = item.id, contextId = browseContextId)
                },
                onLastVisibleIndexChanged = { lastVisibleIndex ->
                  val items = browseContextState?.flatItems ?: browseDisplayState.submissions
                  if (items.isNotEmpty() && lastVisibleIndex > items.lastIndex - 10) {
                    contextScreenModel.loadNextPageIfNeeded(browseContextId)
                  }
                },
                onRetryLoadMore = {
                  contextScreenModel.loadNextPageIfNeeded(browseContextId, force = true)
                },
                waterfallState = browseWaterfallState,
                pageControls = browsePageControls,
                canLoadPreviousPageAtTop = browseContextState?.hasPreviousPage == true,
                loadingPreviousPage = browseContextState?.loading?.prependLoading == true,
                prependErrorMessage = browseContextState?.loading?.prependErrorMessage,
                onLoadPreviousPageAtTop = {
                  contextScreenModel.loadPreviousPageIfNeeded(browseContextId, force = true)
                },
                onLoadFirstPage = { contextScreenModel.navigateToFirstPage(browseContextId) },
                onLoadPreviousPage = { contextScreenModel.navigateToPreviousPage(browseContextId) },
                onJumpToPage = { pageNumber ->
                  contextScreenModel.navigateToPage(browseContextId, pageNumber)
                },
                onLoadNextPage = { contextScreenModel.navigateToNextPage(browseContextId) },
                onLoadLastPage = { contextScreenModel.navigateToLastPage(browseContextId) },
                pendingScrollRequest = browseContextState?.waterfallViewport?.scrollRequest,
                onConsumeScrollRequest = { version ->
                  contextScreenModel.consumeWaterfallScrollRequest(browseContextId, version)
                },
                onViewportChanged = { viewport ->
                  contextScreenModel.updateWaterfallViewport(
                      contextId = browseContextId,
                      firstVisibleItemIndex = viewport.firstVisibleItemIndex,
                      firstVisibleItemScrollOffset = viewport.firstVisibleItemScrollOffset,
                      anchorSid = viewport.anchorSid,
                      currentPageNumber = browseContextState?.pageNumberForSid(viewport.anchorSid),
                  )
                },
            )
          }
        }

        TopLevelDestination.SEARCH -> {
          PageStateWrapper(
              state = searchPageState,
              hasContent = searchDisplayState.submissions.isNotEmpty(),
              onRetry = searchScreenModel::refresh,
          ) {
            SearchScreen(
                state = searchDisplayState,
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
                        onShiftDateRange = searchScreenModel::shiftDateRange,
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
                          contextScreenModel.selectSubmission(searchContextId, item.id)
                          navigator.openSubmissionFromList(
                              sid = item.id,
                              contextId = searchContextId,
                          )
                        },
                        onLastVisibleIndexChanged = { lastVisibleIndex ->
                          val items =
                              searchContextState?.flatItems ?: searchDisplayState.submissions
                          if (items.isNotEmpty() && lastVisibleIndex > items.lastIndex - 10) {
                            contextScreenModel.loadNextPageIfNeeded(searchContextId)
                          }
                        },
                        onRetryLoadMore = {
                          contextScreenModel.loadNextPageIfNeeded(searchContextId, force = true)
                        },
                    ),
                waterfallState = searchWaterfallState,
                pageControls = searchPageControls,
                canLoadPreviousPageAtTop = searchContextState?.hasPreviousPage == true,
                loadingPreviousPage = searchContextState?.loading?.prependLoading == true,
                prependErrorMessage = searchContextState?.loading?.prependErrorMessage,
                onLoadPreviousPageAtTop = {
                  contextScreenModel.loadPreviousPageIfNeeded(searchContextId, force = true)
                },
                onLoadFirstPage = { contextScreenModel.navigateToFirstPage(searchContextId) },
                onLoadPreviousPage = { contextScreenModel.navigateToPreviousPage(searchContextId) },
                onJumpToPage = { pageNumber ->
                  contextScreenModel.navigateToPage(searchContextId, pageNumber)
                },
                onLoadNextPage = { contextScreenModel.navigateToNextPage(searchContextId) },
                onLoadLastPage = { contextScreenModel.navigateToLastPage(searchContextId) },
                pendingScrollRequest = searchContextState?.waterfallViewport?.scrollRequest,
                onConsumeScrollRequest = { version ->
                  contextScreenModel.consumeWaterfallScrollRequest(searchContextId, version)
                },
                onViewportChanged = { viewport ->
                  contextScreenModel.updateWaterfallViewport(
                      contextId = searchContextId,
                      firstVisibleItemIndex = viewport.firstVisibleItemIndex,
                      firstVisibleItemScrollOffset = viewport.firstVisibleItemScrollOffset,
                      anchorSid = viewport.anchorSid,
                      currentPageNumber = searchContextState?.pageNumberForSid(viewport.anchorSid),
                  )
                },
            )
          }
        }

        TopLevelDestination.MORE -> {
          MoreScreen(
              state = moreState,
              onOpenUser = moreState.openUserAction(navigator),
              onOpenFollowing = moreState.openFollowingAction(navigator),
              onOpenWatchRecommendations = moreState.openWatchRecommendationsAction(navigator),
              onOpenFavorites = moreState.openFavoritesAction(navigator),
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

private fun MoreUiState.openUserAction(navigator: Navigator): (() -> Unit)? =
    (this as? MoreUiState.Ready)
        ?.username
        ?.takeIf { it.isNotBlank() }
        ?.let { username ->
          {
            navigator.push(
                UserRouteScreen(
                    username = username,
                    initialChildRoute = UserChildRoute.Gallery,
                )
            )
          }
        }

private fun MoreUiState.openFollowingAction(navigator: Navigator): (() -> Unit)? =
    (this as? MoreUiState.Ready)
        ?.username
        ?.takeIf { it.isNotBlank() }
        ?.let { username ->
          {
            navigator.push(
                UserWatchlistRouteScreen(
                    username = username,
                    category = WatchlistCategory.Watching,
                )
            )
          }
        }

private fun MoreUiState.openWatchRecommendationsAction(navigator: Navigator): (() -> Unit)? =
    (this as? MoreUiState.Ready)
        ?.username
        ?.takeIf { it.isNotBlank() }
        ?.let { username ->
          { navigator.push(WatchRecommendationRouteScreen(username = username)) }
        }

private fun MoreUiState.openFavoritesAction(navigator: Navigator): (() -> Unit)? =
    (this as? MoreUiState.Ready)
        ?.username
        ?.takeIf { it.isNotBlank() }
        ?.let { username ->
          {
            navigator.push(
                UserRouteScreen(
                    username = username,
                    initialChildRoute = UserChildRoute.Favorites,
                )
            )
          }
        }

private class MainTopLevelDestinationHolder : ScreenModel {
  var destination by mutableStateOf(TopLevelDestination.FEED)
}
