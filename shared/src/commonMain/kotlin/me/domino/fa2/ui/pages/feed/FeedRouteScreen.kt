package me.domino.fa2.ui.pages.feed

import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.serialization.Serializable
import me.domino.fa2.data.remote.fa.feed.FeedRepository
import me.domino.fa2.domain.submissionviewer.adapters.FeedSubmissionSourceAdapter
import me.domino.fa2.domain.submissionviewer.api.SubmissionContextSourceKind
import me.domino.fa2.domain.submissionviewer.api.SubmissionPageSnapshot
import me.domino.fa2.domain.submissionviewer.api.WaterfallViewportState
import me.domino.fa2.ui.app.navigation.AppScreen
import me.domino.fa2.ui.components.state.PageStateWrapper
import me.domino.fa2.ui.pages.submission.SubmissionRouteScreen
import me.domino.fa2.ui.pages.submission.pager.SubmissionContextScreenModel
import me.domino.fa2.ui.pages.submission.pager.pageNumberForSid
import me.domino.fa2.ui.pages.submission.pager.toWaterfallPageControls
import me.domino.fa2.utils.FaUrls
import org.koin.compose.koinInject

/** Feed 路由页面。 */
@Serializable
class FeedRouteScreen : AppScreen {
  /** 路由页面内容。 */
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val contextId = "submission-list-holder:feed-route"
    val contextScreenModel =
        navigator.rememberNavigatorScreenModel<SubmissionContextScreenModel>(
            tag = "submission-context"
        ) {
          SubmissionContextScreenModel()
        }
    val screenModel = koinScreenModel<FeedScreenModel>()
    val feedRepository = koinInject<FeedRepository>()
    val query by screenModel.state.collectAsState()
    val state = query.data ?: FeedUiState()
    val contextState by contextScreenModel.state(contextId).collectAsState()
    val initialViewport =
        remember(contextScreenModel, contextId) {
          contextScreenModel.snapshot(contextId)?.waterfallViewport ?: WaterfallViewportState()
        }
    val waterfallState =
        rememberLazyStaggeredGridState(
            initialFirstVisibleItemIndex = initialViewport.firstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset = initialViewport.firstVisibleItemScrollOffset,
        )

    LaunchedEffect(Unit) { screenModel.load() }
    LaunchedEffect(screenModel, contextId, feedRepository) {
      screenModel.state.collect { s ->
        val data = s.data ?: return@collect
        if (data.submissions.isEmpty()) return@collect
        contextScreenModel.syncRootPage(
            contextId = contextId,
            sourceKind = SubmissionContextSourceKind.FEED,
            adapter = FeedSubmissionSourceAdapter(feedRepository),
            page =
                SubmissionPageSnapshot(
                    pageId = FaUrls.submissions(),
                    requestKey = FaUrls.submissions(),
                    items = data.submissions,
                    nextRequestKey = data.nextPageUrl,
                    firstRequestKey = FaUrls.submissions(),
                ),
            revisionKey = FaUrls.submissions(),
        )
      }
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

    PageStateWrapper(
        state = query,
        onRetry = { screenModel.load(forceRefresh = true) },
    ) {
      FeedScreen(
          state = displayState,
          onRetry = { screenModel.load(forceRefresh = true) },
          onRefresh = screenModel::refresh,
          onOpenSubmission = { item ->
            contextScreenModel.selectSubmission(contextId, item.id)
            navigator.push(SubmissionRouteScreen(initialSid = item.id, contextId = contextId))
          },
          onLastVisibleIndexChanged = { lastVisibleIndex ->
            val items = contextState?.flatItems ?: displayState.submissions
            if (items.isNotEmpty() && lastVisibleIndex > items.lastIndex - 10) {
              contextScreenModel.loadNextPageIfNeeded(contextId = contextId)
            }
          },
          onRetryLoadMore = {
            contextScreenModel.loadNextPageIfNeeded(contextId = contextId, force = true)
          },
          waterfallState = waterfallState,
          pageControls = pageControls,
          canLoadPreviousPageAtTop = contextState?.hasPreviousPage == true,
          loadingPreviousPage = contextState?.loading?.prependLoading == true,
          prependErrorMessage = contextState?.loading?.prependErrorMessage,
          onLoadPreviousPageAtTop = {
            contextScreenModel.loadPreviousPageIfNeeded(contextId = contextId, force = true)
          },
          onLoadFirstPage = { contextScreenModel.navigateToFirstPage(contextId) },
          onLoadPreviousPage = { contextScreenModel.navigateToPreviousPage(contextId) },
          onJumpToPage = { pageNumber -> contextScreenModel.navigateToPage(contextId, pageNumber) },
          onLoadNextPage = { contextScreenModel.navigateToNextPage(contextId) },
          onLoadLastPage = { contextScreenModel.navigateToLastPage(contextId) },
          pendingScrollRequest = contextState?.waterfallViewport?.scrollRequest,
          onConsumeScrollRequest = { version ->
            contextScreenModel.consumeWaterfallScrollRequest(contextId, version)
          },
          onViewportChanged = { viewport ->
            contextScreenModel.updateWaterfallViewport(
                contextId = contextId,
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
