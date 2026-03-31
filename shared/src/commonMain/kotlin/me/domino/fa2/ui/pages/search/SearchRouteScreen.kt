package me.domino.fa2.ui.pages.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import me.domino.fa2.data.repository.SearchRepository
import me.domino.fa2.ui.components.PageStateWrapper
import me.domino.fa2.ui.layouts.SearchRouteTopBar
import me.domino.fa2.ui.navigation.goBackHome
import me.domino.fa2.ui.navigation.openSubmissionFromList
import me.domino.fa2.ui.pages.submission.SearchSubmissionSourceAdapter
import me.domino.fa2.ui.pages.submission.SubmissionContextScreenModel
import me.domino.fa2.ui.pages.submission.SubmissionContextSourceKind
import me.domino.fa2.ui.pages.submission.SubmissionLoadedPage
import me.domino.fa2.ui.pages.submission.WaterfallViewportState
import me.domino.fa2.ui.pages.submission.pageNumberForSid
import me.domino.fa2.ui.pages.submission.toWaterfallPageControls
import org.koin.compose.koinInject

/** 独立搜索路由页面（用于从投稿关键词跳转）。 */
class SearchRouteScreen(
    private val initialQuery: String,
    private val initialSearchUrl: String? = null,
) : Screen {
  private val holderTag: String =
      "submission-list-holder:search-route:${initialQuery.hashCode()}:${initialSearchUrl.orEmpty().hashCode()}"

  override val key: String =
      "search-route:${initialQuery.trim().lowercase()}:${initialSearchUrl.orEmpty().trim().lowercase()}"

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val contextScreenModel =
        navigator.rememberNavigatorScreenModel<SubmissionContextScreenModel>(
            tag = "submission-context"
        ) {
          SubmissionContextScreenModel()
        }
    val screenModel = koinScreenModel<SearchScreenModel>()
    val searchRepository = koinInject<SearchRepository>()
    val state by screenModel.state.collectAsState()
    val pageState by screenModel.pageState.collectAsState()
    val contextState by contextScreenModel.state(holderTag).collectAsState()
    val initialViewport =
        remember(contextScreenModel, holderTag) {
          contextScreenModel.snapshot(holderTag)?.waterfallViewport ?: WaterfallViewportState()
        }
    val waterfallState =
        rememberLazyStaggeredGridState(
            initialFirstVisibleItemIndex = initialViewport.firstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset = initialViewport.firstVisibleItemScrollOffset,
        )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(initialQuery, initialSearchUrl, state.hasSearched) {
      if (state.hasSearched) return@LaunchedEffect
      val restoredUrl = initialSearchUrl?.trim().orEmpty()
      if (restoredUrl.isNotBlank()) {
        screenModel.applySearchFromUrl(url = restoredUrl, fallbackQuery = initialQuery)
        return@LaunchedEffect
      }
      val normalized = initialQuery.trim()
      if (normalized.isNotBlank()) {
        screenModel.updateQuery(normalized)
        screenModel.applySearch()
      }
    }
    LaunchedEffect(state.applied, state.submissions, state.nextPageUrl, state.totalCount) {
      val applied = state.applied ?: return@LaunchedEffect
      if (state.submissions.isEmpty()) return@LaunchedEffect
      val firstUrl = buildSearchUrl(applied, page = 1)
      val lastPageNumber =
          state.totalCount?.let { totalCount -> ((minOf(totalCount, 5000) - 1) / 72) + 1 }
      contextScreenModel.syncRootPage(
          contextId = holderTag,
          sourceKind = SubmissionContextSourceKind.SEARCH,
          adapter = SearchSubmissionSourceAdapter(searchRepository, firstPageUrl = firstUrl),
          page =
              SubmissionLoadedPage(
                  pageId = firstUrl,
                  requestKey = firstUrl,
                  items = state.submissions,
                  pageNumber = 1,
                  nextRequestKey = state.nextPageUrl,
                  firstRequestKey = firstUrl,
                  lastRequestKey =
                      lastPageNumber?.let { pageNumber ->
                        buildSearchUrl(applied, page = pageNumber)
                      },
                  lastPageNumber = lastPageNumber,
                  totalCount = state.totalCount,
              ),
          revisionKey = firstUrl,
      )
    }
    val displayState =
        contextState?.let { snapshot ->
          state.copy(
              submissions = snapshot.flatItems.ifEmpty { state.submissions },
              nextPageUrl = if (snapshot.hasNextPage) "context:next" else null,
              isLoadingMore = snapshot.loading.appendLoading,
              appendErrorMessage = snapshot.loading.appendErrorMessage,
          )
        } ?: state
    val pageControls = contextState?.toWaterfallPageControls()

    Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
      SearchRouteTopBar(
          onBack = { navigator.pop() },
          onGoHome = { navigator.goBackHome() },
          onTitleClick = { coroutineScope.launch { waterfallState.animateScrollToItem(0) } },
      )

      PageStateWrapper(
          state = pageState,
          hasContent = displayState.submissions.isNotEmpty(),
          onRetry = screenModel::refresh,
      ) {
        SearchScreen(
            state = displayState,
            actions =
                SearchScreenActions(
                    onOpenOverlay = screenModel::openOverlay,
                    onCloseOverlay = screenModel::closeOverlay,
                    onUpdateQuery = screenModel::updateQuery,
                    onToggleGender = screenModel::toggleGender,
                    onUpdateCategory = screenModel::updateCategory,
                    onUpdateType = screenModel::updateType,
                    onUpdateSpecies = screenModel::updateSpecies,
                    onUpdateOrderBy = screenModel::updateOrderBy,
                    onUpdateOrderDirection = screenModel::updateOrderDirection,
                    onUpdateRange = screenModel::updateRange,
                    onUpdateRangeFrom = screenModel::updateRangeFrom,
                    onUpdateRangeTo = screenModel::updateRangeTo,
                    onSetRatingGeneral = screenModel::setRatingGeneral,
                    onSetRatingMature = screenModel::setRatingMature,
                    onSetRatingAdult = screenModel::setRatingAdult,
                    onSetTypeArt = screenModel::setTypeArt,
                    onSetTypeMusic = screenModel::setTypeMusic,
                    onSetTypeFlash = screenModel::setTypeFlash,
                    onSetTypeStory = screenModel::setTypeStory,
                    onSetTypePhoto = screenModel::setTypePhoto,
                    onSetTypePoetry = screenModel::setTypePoetry,
                    onApplySearch = {
                      coroutineScope.launch { waterfallState.scrollToItem(0) }
                      screenModel.applySearch()
                    },
                    onRefresh = screenModel::refresh,
                    onRetry = screenModel::refresh,
                    onOpenSubmission = { item ->
                      contextScreenModel.selectSubmission(holderTag, item.id)
                      navigator.openSubmissionFromList(sid = item.id, contextId = holderTag)
                    },
                    onLastVisibleIndexChanged = { lastVisibleIndex ->
                      val items = contextState?.flatItems ?: displayState.submissions
                      if (items.isNotEmpty() && lastVisibleIndex > items.lastIndex - 10) {
                        contextScreenModel.loadNextPageIfNeeded(holderTag)
                      }
                    },
                    onRetryLoadMore = {
                      contextScreenModel.loadNextPageIfNeeded(holderTag, force = true)
                    },
                ),
            waterfallState = waterfallState,
            pageControls = pageControls,
            canLoadPreviousPageAtTop = contextState?.hasPreviousPage == true,
            loadingPreviousPage = contextState?.loading?.prependLoading == true,
            prependErrorMessage = contextState?.loading?.prependErrorMessage,
            onLoadPreviousPageAtTop = {
              contextScreenModel.loadPreviousPageIfNeeded(holderTag, force = true)
            },
            onLoadFirstPage = { contextScreenModel.navigateToFirstPage(holderTag) },
            onLoadPreviousPage = { contextScreenModel.navigateToPreviousPage(holderTag) },
            onJumpToPage = { pageNumber ->
              contextScreenModel.navigateToPage(holderTag, pageNumber)
            },
            onLoadNextPage = { contextScreenModel.navigateToNextPage(holderTag) },
            onLoadLastPage = { contextScreenModel.navigateToLastPage(holderTag) },
            pendingScrollRequest = contextState?.waterfallViewport?.scrollRequest,
            onConsumeScrollRequest = { version ->
              contextScreenModel.consumeWaterfallScrollRequest(holderTag, version)
            },
            onViewportChanged = { viewport ->
              contextScreenModel.updateWaterfallViewport(
                  contextId = holderTag,
                  firstVisibleItemIndex = viewport.firstVisibleItemIndex,
                  firstVisibleItemScrollOffset = viewport.firstVisibleItemScrollOffset,
                  anchorSid = viewport.anchorSid,
                  currentPageNumber = contextState?.pageNumberForSid(viewport.anchorSid),
              )
            },
        )
      }
    }
  }
}
