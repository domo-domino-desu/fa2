package me.domino.fa2.ui.pages.browse

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
import me.domino.fa2.data.repository.BrowseRepository
import me.domino.fa2.ui.components.PageStateWrapper
import me.domino.fa2.ui.layouts.BrowseRouteTopBar
import me.domino.fa2.ui.navigation.goBackHome
import me.domino.fa2.ui.navigation.openSubmissionFromList
import me.domino.fa2.ui.pages.submission.BrowseSubmissionSourceAdapter
import me.domino.fa2.ui.pages.submission.SubmissionContextScreenModel
import me.domino.fa2.ui.pages.submission.SubmissionContextSourceKind
import me.domino.fa2.ui.pages.submission.SubmissionLoadedPage
import me.domino.fa2.ui.pages.submission.WaterfallViewportState
import me.domino.fa2.ui.pages.submission.pageNumberForSid
import me.domino.fa2.ui.pages.submission.toWaterfallPageControls
import me.domino.fa2.util.FaUrls
import org.koin.compose.koinInject

/** 独立 Browse 路由页面（用于从投稿详情跳转）。 */
class BrowseRouteScreen(private val initialFilter: BrowseFilterState) : Screen {
  private val holderTag: String = "submission-list-holder:browse-route:${initialFilter.hashCode()}"

  override val key: String = "browse-route:${initialFilter.hashCode()}"

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
    val screenModel = koinScreenModel<BrowseScreenModel>()
    val browseRepository = koinInject<BrowseRepository>()
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

    LaunchedEffect(initialFilter) { screenModel.applyFilter(initialFilter) }
    LaunchedEffect(state.appliedFilter, state.submissions, state.nextPageUrl) {
      if (state.submissions.isEmpty()) return@LaunchedEffect
      val firstUrl =
          FaUrls.browse(
              cat = state.appliedFilter.category,
              atype = state.appliedFilter.type,
              species = state.appliedFilter.species,
              gender = state.appliedFilter.gender,
              ratingGeneral = state.appliedFilter.ratingGeneral,
              ratingMature = state.appliedFilter.ratingMature,
              ratingAdult = state.appliedFilter.ratingAdult,
          )
      contextScreenModel.syncRootPage(
          contextId = holderTag,
          sourceKind = SubmissionContextSourceKind.BROWSE,
          adapter = BrowseSubmissionSourceAdapter(browseRepository, firstPageUrl = firstUrl),
          page =
              SubmissionLoadedPage(
                  pageId = firstUrl,
                  requestKey = firstUrl,
                  items = state.submissions,
                  pageNumber = 1,
                  nextRequestKey = state.nextPageUrl,
                  firstRequestKey = firstUrl,
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
      BrowseRouteTopBar(
          onBack = { navigator.pop() },
          onGoHome = { navigator.goBackHome() },
          onTitleClick = { coroutineScope.launch { waterfallState.animateScrollToItem(0) } },
      )

      PageStateWrapper(state = pageState, onRetry = screenModel::refresh) {
        BrowseScreen(
            state = displayState,
            onUpdateCategory = screenModel::updateCategory,
            onUpdateType = screenModel::updateType,
            onUpdateSpecies = screenModel::updateSpecies,
            onUpdateGender = screenModel::updateGender,
            onSetRatingGeneral = screenModel::setRatingGeneral,
            onSetRatingMature = screenModel::setRatingMature,
            onSetRatingAdult = screenModel::setRatingAdult,
            onApplyFilter = screenModel::applyFilter,
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
            onRetryLoadMore = { contextScreenModel.loadNextPageIfNeeded(holderTag, force = true) },
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
