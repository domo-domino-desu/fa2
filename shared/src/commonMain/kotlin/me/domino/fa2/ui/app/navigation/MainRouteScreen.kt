package me.domino.fa2.ui.app.navigation

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.domino.fa2.data.fa.browse.BrowseRepository
import me.domino.fa2.data.fa.feed.FeedRepository
import me.domino.fa2.data.fa.search.SearchRepository
import me.domino.fa2.data.fa.session.PendingFaRouteStore
import me.domino.fa2.data.model.PageState
import me.domino.fa2.ui.components.state.PageStateWrapper
import me.domino.fa2.ui.pages.auth.AuthRouteScreen
import me.domino.fa2.ui.pages.browse.BrowseScreen
import me.domino.fa2.ui.pages.browse.BrowseScreenModel
import me.domino.fa2.ui.pages.browse.BrowseUiState
import me.domino.fa2.ui.pages.feed.FeedScreen
import me.domino.fa2.ui.pages.feed.FeedScreenModel
import me.domino.fa2.ui.pages.feed.FeedUiState
import me.domino.fa2.ui.pages.history.SearchHistoryRouteScreen
import me.domino.fa2.ui.pages.history.SubmissionHistoryRouteScreen
import me.domino.fa2.ui.pages.more.MoreScreen
import me.domino.fa2.ui.pages.more.MoreScreenModel
import me.domino.fa2.ui.pages.more.MoreUiState
import me.domino.fa2.ui.pages.overlays.about.AboutRouteScreen
import me.domino.fa2.ui.pages.overlays.watchrecommendation.WatchRecommendationRouteScreen
import me.domino.fa2.ui.pages.search.SearchScreen
import me.domino.fa2.ui.pages.search.SearchScreenActions
import me.domino.fa2.ui.pages.search.SearchScreenModel
import me.domino.fa2.ui.pages.search.SearchUiState
import me.domino.fa2.ui.pages.search.buildSearchUrl
import me.domino.fa2.ui.pages.settings.SettingsRouteScreen
import me.domino.fa2.ui.pages.submission.pager.BrowseSubmissionSourceAdapter
import me.domino.fa2.ui.pages.submission.pager.FeedSubmissionSourceAdapter
import me.domino.fa2.ui.pages.submission.pager.SearchSubmissionSourceAdapter
import me.domino.fa2.ui.pages.submission.pager.SubmissionContextScreenModel
import me.domino.fa2.ui.pages.submission.pager.SubmissionContextSnapshot
import me.domino.fa2.ui.pages.submission.pager.SubmissionContextSourceKind
import me.domino.fa2.ui.pages.submission.pager.SubmissionLoadedPage
import me.domino.fa2.ui.pages.submission.pager.WaterfallViewportState
import me.domino.fa2.ui.pages.submission.pager.pageNumberForSid
import me.domino.fa2.ui.pages.submission.pager.toWaterfallPageControls
import me.domino.fa2.ui.pages.user.UserChildRoute
import me.domino.fa2.ui.pages.user.UserRouteScreen
import me.domino.fa2.ui.pages.user.userHeaderNavigationActions
import me.domino.fa2.utils.FaUrls
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
              if (atTop) feedScreenModel.refresh()
              else coroutineScope.launch { feedWaterfallState.animateScrollToItem(0) }
            }
            TopLevelDestination.BROWSE -> {
              val atTop =
                  browseWaterfallState.firstVisibleItemIndex == 0 &&
                      browseWaterfallState.firstVisibleItemScrollOffset == 0
              if (atTop) browseScreenModel.refresh()
              else coroutineScope.launch { browseWaterfallState.animateScrollToItem(0) }
            }
            TopLevelDestination.SEARCH ->
                coroutineScope.launch { searchWaterfallState.animateScrollToItem(0) }
            TopLevelDestination.MORE -> Unit
          }
        },
    ) {
      when (currentTopLevelDestination) {
        TopLevelDestination.FEED ->
            FeedDestinationContent(
                feedPageState = feedPageState,
                feedDisplayState = feedDisplayState,
                feedContextState = feedContextState,
                feedWaterfallState = feedWaterfallState,
                feedScreenModel = feedScreenModel,
                contextScreenModel = contextScreenModel,
                navigator = navigator,
                contextId = feedContextId,
            )
        TopLevelDestination.BROWSE ->
            BrowseDestinationContent(
                browsePageState = browsePageState,
                browseDisplayState = browseDisplayState,
                browseContextState = browseContextState,
                browseWaterfallState = browseWaterfallState,
                browseScreenModel = browseScreenModel,
                contextScreenModel = contextScreenModel,
                navigator = navigator,
                contextId = browseContextId,
            )
        TopLevelDestination.SEARCH ->
            SearchDestinationContent(
                searchPageState = searchPageState,
                searchDisplayState = searchDisplayState,
                searchContextState = searchContextState,
                searchWaterfallState = searchWaterfallState,
                searchScreenModel = searchScreenModel,
                contextScreenModel = contextScreenModel,
                navigator = navigator,
                coroutineScope = coroutineScope,
                contextId = searchContextId,
            )
        TopLevelDestination.MORE ->
            MoreDestinationContent(
                moreState = moreState,
                moreScreenModel = moreScreenModel,
                navigator = navigator,
            )
      }
    }
  }
}

/** Feed 导航目标内容。 */
@Composable
private fun FeedDestinationContent(
    feedPageState: PageState<FeedUiState>,
    feedDisplayState: FeedUiState,
    feedContextState: SubmissionContextSnapshot?,
    feedWaterfallState: LazyStaggeredGridState,
    feedScreenModel: FeedScreenModel,
    contextScreenModel: SubmissionContextScreenModel,
    navigator: Navigator,
    contextId: String,
) {
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
          contextScreenModel.selectSubmission(contextId, item.id)
          navigator.openSubmissionFromList(sid = item.id, contextId = contextId)
        },
        onLastVisibleIndexChanged = { lastVisibleIndex ->
          val items = feedContextState?.flatItems ?: feedDisplayState.submissions
          if (items.isNotEmpty() && lastVisibleIndex > items.lastIndex - 10) {
            contextScreenModel.loadNextPageIfNeeded(contextId)
          }
        },
        onRetryLoadMore = { contextScreenModel.loadNextPageIfNeeded(contextId, force = true) },
        waterfallState = feedWaterfallState,
        pageControls = feedContextState?.toWaterfallPageControls(),
        canLoadPreviousPageAtTop = feedContextState?.hasPreviousPage == true,
        loadingPreviousPage = feedContextState?.loading?.prependLoading == true,
        prependErrorMessage = feedContextState?.loading?.prependErrorMessage,
        onLoadPreviousPageAtTop = {
          contextScreenModel.loadPreviousPageIfNeeded(contextId, force = true)
        },
        onLoadFirstPage = { contextScreenModel.navigateToFirstPage(contextId) },
        onLoadPreviousPage = { contextScreenModel.navigateToPreviousPage(contextId) },
        onJumpToPage = { pageNumber -> contextScreenModel.navigateToPage(contextId, pageNumber) },
        onLoadNextPage = { contextScreenModel.navigateToNextPage(contextId) },
        onLoadLastPage = { contextScreenModel.navigateToLastPage(contextId) },
        pendingScrollRequest = feedContextState?.waterfallViewport?.scrollRequest,
        onConsumeScrollRequest = { version ->
          contextScreenModel.consumeWaterfallScrollRequest(contextId, version)
        },
        onViewportChanged = { viewport ->
          contextScreenModel.updateWaterfallViewport(
              contextId = contextId,
              firstVisibleItemIndex = viewport.firstVisibleItemIndex,
              firstVisibleItemScrollOffset = viewport.firstVisibleItemScrollOffset,
              anchorSid = viewport.anchorSid,
              currentPageNumber = feedContextState?.pageNumberForSid(viewport.anchorSid),
          )
        },
    )
  }
}

/** Browse 导航目标内容。 */
@Composable
private fun BrowseDestinationContent(
    browsePageState: PageState<BrowseUiState>,
    browseDisplayState: BrowseUiState,
    browseContextState: SubmissionContextSnapshot?,
    browseWaterfallState: LazyStaggeredGridState,
    browseScreenModel: BrowseScreenModel,
    contextScreenModel: SubmissionContextScreenModel,
    navigator: Navigator,
    contextId: String,
) {
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
          contextScreenModel.selectSubmission(contextId, item.id)
          navigator.openSubmissionFromList(sid = item.id, contextId = contextId)
        },
        onLastVisibleIndexChanged = { lastVisibleIndex ->
          val items = browseContextState?.flatItems ?: browseDisplayState.submissions
          if (items.isNotEmpty() && lastVisibleIndex > items.lastIndex - 10) {
            contextScreenModel.loadNextPageIfNeeded(contextId)
          }
        },
        onRetryLoadMore = { contextScreenModel.loadNextPageIfNeeded(contextId, force = true) },
        waterfallState = browseWaterfallState,
        pageControls = browseContextState?.toWaterfallPageControls(),
        canLoadPreviousPageAtTop = browseContextState?.hasPreviousPage == true,
        loadingPreviousPage = browseContextState?.loading?.prependLoading == true,
        prependErrorMessage = browseContextState?.loading?.prependErrorMessage,
        onLoadPreviousPageAtTop = {
          contextScreenModel.loadPreviousPageIfNeeded(contextId, force = true)
        },
        onLoadFirstPage = { contextScreenModel.navigateToFirstPage(contextId) },
        onLoadPreviousPage = { contextScreenModel.navigateToPreviousPage(contextId) },
        onJumpToPage = { pageNumber -> contextScreenModel.navigateToPage(contextId, pageNumber) },
        onLoadNextPage = { contextScreenModel.navigateToNextPage(contextId) },
        onLoadLastPage = { contextScreenModel.navigateToLastPage(contextId) },
        pendingScrollRequest = browseContextState?.waterfallViewport?.scrollRequest,
        onConsumeScrollRequest = { version ->
          contextScreenModel.consumeWaterfallScrollRequest(contextId, version)
        },
        onViewportChanged = { viewport ->
          contextScreenModel.updateWaterfallViewport(
              contextId = contextId,
              firstVisibleItemIndex = viewport.firstVisibleItemIndex,
              firstVisibleItemScrollOffset = viewport.firstVisibleItemScrollOffset,
              anchorSid = viewport.anchorSid,
              currentPageNumber = browseContextState?.pageNumberForSid(viewport.anchorSid),
          )
        },
    )
  }
}

/** Search 导航目标内容。 */
@Composable
private fun SearchDestinationContent(
    searchPageState: PageState<SearchUiState>,
    searchDisplayState: SearchUiState,
    searchContextState: SubmissionContextSnapshot?,
    searchWaterfallState: LazyStaggeredGridState,
    searchScreenModel: SearchScreenModel,
    contextScreenModel: SubmissionContextScreenModel,
    navigator: Navigator,
    coroutineScope: CoroutineScope,
    contextId: String,
) {
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
                  contextScreenModel.selectSubmission(contextId, item.id)
                  navigator.openSubmissionFromList(sid = item.id, contextId = contextId)
                },
                onLastVisibleIndexChanged = { lastVisibleIndex ->
                  val items = searchContextState?.flatItems ?: searchDisplayState.submissions
                  if (items.isNotEmpty() && lastVisibleIndex > items.lastIndex - 10) {
                    contextScreenModel.loadNextPageIfNeeded(contextId)
                  }
                },
                onRetryLoadMore = {
                  contextScreenModel.loadNextPageIfNeeded(contextId, force = true)
                },
            ),
        waterfallState = searchWaterfallState,
        pageControls = searchContextState?.toWaterfallPageControls(),
        canLoadPreviousPageAtTop = searchContextState?.hasPreviousPage == true,
        loadingPreviousPage = searchContextState?.loading?.prependLoading == true,
        prependErrorMessage = searchContextState?.loading?.prependErrorMessage,
        onLoadPreviousPageAtTop = {
          contextScreenModel.loadPreviousPageIfNeeded(contextId, force = true)
        },
        onLoadFirstPage = { contextScreenModel.navigateToFirstPage(contextId) },
        onLoadPreviousPage = { contextScreenModel.navigateToPreviousPage(contextId) },
        onJumpToPage = { pageNumber -> contextScreenModel.navigateToPage(contextId, pageNumber) },
        onLoadNextPage = { contextScreenModel.navigateToNextPage(contextId) },
        onLoadLastPage = { contextScreenModel.navigateToLastPage(contextId) },
        pendingScrollRequest = searchContextState?.waterfallViewport?.scrollRequest,
        onConsumeScrollRequest = { version ->
          contextScreenModel.consumeWaterfallScrollRequest(contextId, version)
        },
        onViewportChanged = { viewport ->
          contextScreenModel.updateWaterfallViewport(
              contextId = contextId,
              firstVisibleItemIndex = viewport.firstVisibleItemIndex,
              firstVisibleItemScrollOffset = viewport.firstVisibleItemScrollOffset,
              anchorSid = viewport.anchorSid,
              currentPageNumber = searchContextState?.pageNumberForSid(viewport.anchorSid),
          )
        },
    )
  }
}

/** More 导航目标内容。 */
@Composable
private fun MoreDestinationContent(
    moreState: MoreUiState,
    moreScreenModel: MoreScreenModel,
    navigator: Navigator,
) {
  MoreScreen(
      state = moreState,
      onOpenUser = moreState.openUserAction(navigator),
      headerNavigationActions = moreState.headerNavigationActions(navigator),
      onOpenWatchRecommendations = moreState.openWatchRecommendationsAction(navigator),
      onOpenSettings = { navigator.push(SettingsRouteScreen()) },
      onOpenSubmissionHistory = { navigator.push(SubmissionHistoryRouteScreen()) },
      onOpenSearchHistory = { navigator.push(SearchHistoryRouteScreen()) },
      onOpenAbout = { navigator.push(AboutRouteScreen()) },
      onLogout = moreScreenModel::logout,
  )
}

/** 构建打开当前用户主页的点击操作，用户名为空时返回 null。 */
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

/** 构建用户头部统计入口导航，用户名为空时返回 null。 */
private fun MoreUiState.headerNavigationActions(navigator: Navigator) =
    (this as? MoreUiState.Ready)
        ?.username
        ?.takeIf { it.isNotBlank() }
        ?.let { username ->
          userHeaderNavigationActions(
              username = username,
              header = userHeader,
              navigator = navigator,
          )
        }

/** 构建打开关注推荐页的点击操作，用户名为空时返回 null。 */
private fun MoreUiState.openWatchRecommendationsAction(navigator: Navigator): (() -> Unit)? =
    (this as? MoreUiState.Ready)
        ?.username
        ?.takeIf { it.isNotBlank() }
        ?.let { username ->
          { navigator.push(WatchRecommendationRouteScreen(username = username)) }
        }

/** 顶层导航目标状态持有器。 */
private class MainTopLevelDestinationHolder : ScreenModel {
  /** 当前选中的顶层导航目标。 */
  var destination by mutableStateOf(TopLevelDestination.FEED)
}
