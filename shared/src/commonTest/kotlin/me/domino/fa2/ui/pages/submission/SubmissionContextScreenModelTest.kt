package me.domino.fa2.ui.pages.submission

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.util.FaUrls

@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionContextScreenModelTest {
  private val dispatcher = StandardTestDispatcher()

  @BeforeTest
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun appendsPageWithoutLosingSelectionOrViewport() =
      runTest(dispatcher.scheduler) {
        val contextId = "feed-context"
        val model = SubmissionContextScreenModel()
        val adapter = FakeSubmissionSourceAdapter()
        model.syncRootPage(
            contextId = contextId,
            sourceKind = SubmissionContextSourceKind.FEED,
            adapter = adapter,
            page =
                SubmissionLoadedPage(
                    pageId = "page-1",
                    requestKey = "page-1",
                    items = listOf(testThumbnail(1), testThumbnail(2)),
                    nextRequestKey = "page-2",
                    firstRequestKey = "page-1",
                ),
            selectedSid = 2,
            revisionKey = "rev-1",
        )
        model.updateWaterfallViewport(
            contextId = contextId,
            firstVisibleItemIndex = 7,
            firstVisibleItemScrollOffset = 24,
            anchorSid = 2,
            currentPageNumber = 1,
        )
        adapter.nextResults["page-2"] =
            SubmissionLoadedPage(
                pageId = "page-2",
                requestKey = "page-2",
                items = listOf(testThumbnail(3), testThumbnail(4)),
                previousRequestKey = "page-1",
            )

        model.loadNextPageIfNeeded(contextId)
        runCurrent()

        val snapshot = assertNotNull(model.snapshot(contextId))
        assertEquals(listOf(1, 2, 3, 4), snapshot.flatItems.map(SubmissionThumbnail::id))
        assertEquals(2, snapshot.selectedSid)
        assertEquals(1, snapshot.selectedFlatIndex)
        assertEquals(7, snapshot.waterfallViewport.firstVisibleItemIndex)
        assertEquals(24, snapshot.waterfallViewport.firstVisibleItemScrollOffset)
        assertEquals(2, snapshot.waterfallViewport.anchorSid)
        assertEquals(1, snapshot.waterfallViewport.currentPageNumber)
        assertEquals(2, snapshot.pages.size)
        assertFalse(snapshot.loading.appendLoading)
      }

  @Test
  fun jumpToDistantPageReplacesWindow() =
      runTest(dispatcher.scheduler) {
        val contextId = "search-context"
        val model = SubmissionContextScreenModel()
        val adapter =
            FakeSubmissionSourceAdapter(
                paginationModel =
                    SubmissionPaginationModel(
                        kind = SubmissionPaginationKind.NUMBERED,
                        hasFirstPage = true,
                        knowsLastPage = true,
                    )
            )
        model.syncRootPage(
            contextId = contextId,
            sourceKind = SubmissionContextSourceKind.SEARCH,
            adapter = adapter,
            page =
                SubmissionLoadedPage(
                    pageId = "page-1",
                    requestKey = "page-1",
                    items = listOf(testThumbnail(10), testThumbnail(11)),
                    pageNumber = 1,
                    nextRequestKey = "page-2",
                    firstRequestKey = "page-1",
                ),
            selectedSid = 10,
            revisionKey = "search-1",
        )
        adapter.jumpResults[5] =
            SubmissionLoadedPage(
                pageId = "page-5",
                requestKey = "page-5",
                items = listOf(testThumbnail(50), testThumbnail(51)),
                pageNumber = 5,
                previousRequestKey = "page-4",
                nextRequestKey = "page-6",
                firstRequestKey = "page-1",
            )

        model.navigateToPage(contextId, 5)
        runCurrent()

        val snapshot = assertNotNull(model.snapshot(contextId))
        assertEquals(listOf("page-5"), snapshot.pages.map(SubmissionPageSnapshot::pageId))
        assertEquals(listOf(50, 51), snapshot.flatItems.map(SubmissionThumbnail::id))
        assertEquals(50, snapshot.waterfallViewport.scrollRequest?.sid)
        assertEquals(5, snapshot.waterfallViewport.currentPageNumber)
        assertEquals(1, adapter.jumpCalls)
      }

  @Test
  fun navigateToAdjacentPageLoadsAdjacentInsteadOfJumping() =
      runTest(dispatcher.scheduler) {
        val contextId = "search-context"
        val model = SubmissionContextScreenModel()
        val adapter =
            FakeSubmissionSourceAdapter(
                paginationModel =
                    SubmissionPaginationModel(
                        kind = SubmissionPaginationKind.NUMBERED,
                        hasFirstPage = true,
                        knowsLastPage = true,
                    )
            )
        model.syncRootPage(
            contextId = contextId,
            sourceKind = SubmissionContextSourceKind.SEARCH,
            adapter = adapter,
            page =
                SubmissionLoadedPage(
                    pageId = "page-2",
                    requestKey = "page-2",
                    items = listOf(testThumbnail(20), testThumbnail(21)),
                    pageNumber = 2,
                    previousRequestKey = "page-1",
                    nextRequestKey = "page-3",
                    firstRequestKey = "page-1",
                ),
            selectedSid = 20,
            revisionKey = "search-2",
        )
        model.updateWaterfallViewport(
            contextId = contextId,
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
            anchorSid = 20,
            currentPageNumber = 2,
        )
        adapter.nextResults["page-3"] =
            SubmissionLoadedPage(
                pageId = "page-3",
                requestKey = "page-3",
                items = listOf(testThumbnail(30), testThumbnail(31)),
                pageNumber = 3,
                previousRequestKey = "page-2",
                nextRequestKey = "page-4",
            )

        model.navigateToPage(contextId, 3)
        runCurrent()

        val snapshot = assertNotNull(model.snapshot(contextId))
        assertEquals(listOf("page-2", "page-3"), snapshot.pages.map(SubmissionPageSnapshot::pageId))
        assertEquals(1, adapter.nextCalls)
        assertEquals(0, adapter.jumpCalls)
        assertEquals(30, snapshot.waterfallViewport.scrollRequest?.sid)
        assertEquals(
            listOf(30, 31),
            snapshot.waterfallViewport.scrollRequest?.targetPageLeadingSids,
        )
        assertEquals(3, snapshot.waterfallViewport.currentPageNumber)
      }

  @Test
  fun navigateToCachedPageOnlyEmitsScrollRequest() =
      runTest(dispatcher.scheduler) {
        val contextId = "browse-context"
        val model = SubmissionContextScreenModel()
        val adapter =
            FakeSubmissionSourceAdapter(
                paginationModel =
                    SubmissionPaginationModel(
                        kind = SubmissionPaginationKind.NUMBERED,
                        hasFirstPage = true,
                        knowsLastPage = true,
                    )
            )
        model.syncRootPage(
            contextId = contextId,
            sourceKind = SubmissionContextSourceKind.BROWSE,
            adapter = adapter,
            page =
                SubmissionLoadedPage(
                    pageId = "page-1",
                    requestKey = "page-1",
                    items = listOf(testThumbnail(10), testThumbnail(11)),
                    pageNumber = 1,
                    nextRequestKey = "page-2",
                    firstRequestKey = "page-1",
                ),
            selectedSid = 10,
            revisionKey = "browse-1",
        )
        adapter.nextResults["page-2"] =
            SubmissionLoadedPage(
                pageId = "page-2",
                requestKey = "page-2",
                items = listOf(testThumbnail(20), testThumbnail(21)),
                pageNumber = 2,
                previousRequestKey = "page-1",
                nextRequestKey = "page-3",
            )
        adapter.nextResults["page-3"] =
            SubmissionLoadedPage(
                pageId = "page-3",
                requestKey = "page-3",
                items = listOf(testThumbnail(30), testThumbnail(31)),
                pageNumber = 3,
                previousRequestKey = "page-2",
            )

        model.loadNextPageIfNeeded(contextId)
        runCurrent()
        model.loadNextPageIfNeeded(contextId)
        runCurrent()

        model.updateWaterfallViewport(
            contextId = contextId,
            firstVisibleItemIndex = 8,
            firstVisibleItemScrollOffset = 16,
            anchorSid = 30,
            currentPageNumber = 3,
        )
        model.navigateToPage(contextId, 1)

        val snapshot = assertNotNull(model.snapshot(contextId))
        assertEquals(0, adapter.previousCalls)
        assertEquals(2, adapter.nextCalls)
        assertEquals(0, adapter.jumpCalls)
        assertEquals(10, snapshot.waterfallViewport.scrollRequest?.sid)
        assertEquals(1, snapshot.waterfallViewport.currentPageNumber)
      }

  @Test
  fun navigateToFirstPagePrependsWhenWindowStartsAtSecondPage() =
      runTest(dispatcher.scheduler) {
        val contextId = "search-first-context"
        val model = SubmissionContextScreenModel()
        val adapter =
            FakeSubmissionSourceAdapter(
                paginationModel =
                    SubmissionPaginationModel(
                        kind = SubmissionPaginationKind.NUMBERED,
                        hasFirstPage = true,
                        knowsLastPage = true,
                    )
            )
        model.syncRootPage(
            contextId = contextId,
            sourceKind = SubmissionContextSourceKind.SEARCH,
            adapter = adapter,
            page =
                SubmissionLoadedPage(
                    pageId = "page-2",
                    requestKey = "page-2",
                    items = listOf(testThumbnail(20), testThumbnail(21)),
                    pageNumber = 2,
                    previousRequestKey = "page-1",
                    nextRequestKey = "page-3",
                    firstRequestKey = "page-1",
                ),
            selectedSid = 20,
            revisionKey = "search-2",
        )
        adapter.firstResult =
            SubmissionLoadedPage(
                pageId = "page-1",
                requestKey = "page-1",
                items = listOf(testThumbnail(10), testThumbnail(11)),
                pageNumber = 1,
                nextRequestKey = "page-2",
                firstRequestKey = "page-1",
            )

        model.updateWaterfallViewport(
            contextId = contextId,
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
            anchorSid = 20,
            currentPageNumber = 2,
        )

        model.navigateToFirstPage(contextId)
        runCurrent()

        val snapshot = assertNotNull(model.snapshot(contextId))
        assertEquals(listOf("page-1", "page-2"), snapshot.pages.map(SubmissionPageSnapshot::pageId))
        assertEquals(1, adapter.firstCalls)
        assertEquals(10, snapshot.waterfallViewport.scrollRequest?.sid)
        assertEquals(1, snapshot.waterfallViewport.currentPageNumber)
      }

  @Test
  fun navigateToFirstPageRestartsWindowWhenCurrentWindowIsFarAway() =
      runTest(dispatcher.scheduler) {
        val contextId = "search-first-restart-context"
        val model = SubmissionContextScreenModel()
        val adapter =
            FakeSubmissionSourceAdapter(
                paginationModel =
                    SubmissionPaginationModel(
                        kind = SubmissionPaginationKind.NUMBERED,
                        hasFirstPage = true,
                        knowsLastPage = true,
                    )
            )
        model.syncRootPage(
            contextId = contextId,
            sourceKind = SubmissionContextSourceKind.SEARCH,
            adapter = adapter,
            page =
                SubmissionLoadedPage(
                    pageId = "page-5",
                    requestKey = "page-5",
                    items = listOf(testThumbnail(50), testThumbnail(51)),
                    pageNumber = 5,
                    previousRequestKey = "page-4",
                    nextRequestKey = "page-6",
                    firstRequestKey = "page-1",
                ),
            selectedSid = 50,
            revisionKey = "search-5",
        )
        adapter.firstResult =
            SubmissionLoadedPage(
                pageId = "page-1",
                requestKey = "page-1",
                items = listOf(testThumbnail(10), testThumbnail(11)),
                pageNumber = 1,
                nextRequestKey = "page-2",
                firstRequestKey = "page-1",
            )

        model.navigateToFirstPage(contextId)
        runCurrent()

        val snapshot = assertNotNull(model.snapshot(contextId))
        assertEquals(listOf("page-1"), snapshot.pages.map(SubmissionPageSnapshot::pageId))
        assertEquals(1, adapter.firstCalls)
        assertEquals(10, snapshot.waterfallViewport.scrollRequest?.sid)
        assertEquals(1, snapshot.waterfallViewport.currentPageNumber)
      }

  @Test
  fun jumpUsesLoadedPageNumberInsteadOfRequestedPageNumber() =
      runTest(dispatcher.scheduler) {
        val contextId = "browse-jump-context"
        val model = SubmissionContextScreenModel()
        val adapter =
            FakeSubmissionSourceAdapter(
                paginationModel =
                    SubmissionPaginationModel(
                        kind = SubmissionPaginationKind.NUMBERED,
                        hasFirstPage = true,
                        knowsLastPage = false,
                    )
            )
        model.syncRootPage(
            contextId = contextId,
            sourceKind = SubmissionContextSourceKind.BROWSE,
            adapter = adapter,
            page =
                SubmissionLoadedPage(
                    pageId = "page-1",
                    requestKey = "page-1",
                    items = listOf(testThumbnail(10), testThumbnail(11)),
                    pageNumber = 1,
                    nextRequestKey = "page-2",
                    firstRequestKey = "page-1",
                ),
            selectedSid = 10,
            revisionKey = "browse-root",
        )
        adapter.jumpResults[10000] =
            SubmissionLoadedPage(
                pageId = "page-999",
                requestKey = "page-999",
                items = listOf(testThumbnail(9990), testThumbnail(9991)),
                pageNumber = 999,
                previousRequestKey = "page-998",
                nextRequestKey = null,
                firstRequestKey = "page-1",
            )

        model.navigateToPage(contextId, 10000)
        runCurrent()

        val snapshot = assertNotNull(model.snapshot(contextId))
        assertEquals(listOf("page-999"), snapshot.pages.map(SubmissionPageSnapshot::pageId))
        assertEquals(999, snapshot.waterfallViewport.currentPageNumber)
        assertEquals(9990, snapshot.waterfallViewport.scrollRequest?.sid)
      }

  @Test
  fun numberedPaginationWithoutKnownLastDoesNotExposeLastAction() =
      runTest(dispatcher.scheduler) {
        val contextId = "browse-controls-context"
        val model = SubmissionContextScreenModel()
        val adapter =
            FakeSubmissionSourceAdapter(
                paginationModel =
                    SubmissionPaginationModel(
                        kind = SubmissionPaginationKind.NUMBERED,
                        hasFirstPage = true,
                        knowsLastPage = false,
                    )
            )
        model.syncRootPage(
            contextId = contextId,
            sourceKind = SubmissionContextSourceKind.BROWSE,
            adapter = adapter,
            page =
                SubmissionLoadedPage(
                    pageId = "page-3",
                    requestKey = "page-3",
                    items = listOf(testThumbnail(30), testThumbnail(31)),
                    pageNumber = 3,
                    previousRequestKey = "page-2",
                    nextRequestKey = "page-4",
                    firstRequestKey = "page-1",
                ),
            selectedSid = 30,
            revisionKey = "browse-controls",
        )

        val controls = assertNotNull(model.snapshot(contextId)?.toWaterfallPageControls())
        assertEquals(SubmissionPaginationKind.NUMBERED, controls.paginationKind)
        assertEquals(true, controls.showPreviousPage)
        assertEquals(true, controls.showNextPage)
        assertEquals(true, controls.showFirstPage)
        assertEquals(false, controls.showLastPage)
        assertFalse(controls.canLoadLastPage)
        assertEquals(3, controls.currentPageNumber)
        assertEquals(null, controls.lastPageNumber)
      }

  @Test
  fun cursorPaginationKeepsPreviousActionVisibleButDisabledOnFirstPage() =
      runTest(dispatcher.scheduler) {
        val contextId = "feed-controls-context"
        val model = SubmissionContextScreenModel()
        val adapter =
            FakeSubmissionSourceAdapter(
                paginationModel =
                    SubmissionPaginationModel(
                        kind = SubmissionPaginationKind.CURSOR,
                        hasFirstPage = true,
                        knowsLastPage = false,
                    )
            )
        model.syncRootPage(
            contextId = contextId,
            sourceKind = SubmissionContextSourceKind.FEED,
            adapter = adapter,
            page =
                SubmissionLoadedPage(
                    pageId = "page-1",
                    requestKey = "page-1",
                    items = listOf(testThumbnail(10), testThumbnail(11)),
                    nextRequestKey = "page-2",
                    firstRequestKey = "page-1",
                ),
            selectedSid = 10,
            revisionKey = "feed-controls",
        )

        val controls = assertNotNull(model.snapshot(contextId)?.toWaterfallPageControls())
        assertEquals(SubmissionPaginationKind.CURSOR, controls.paginationKind)
        assertEquals(true, controls.showPreviousPage)
        assertFalse(controls.canLoadPreviousPage)
        assertEquals(true, controls.showNextPage)
        assertEquals(true, controls.canLoadNextPage)
        assertEquals(false, controls.showLastPage)
      }

  @Test
  fun cursorPaginationCanAdvanceWithUnknownPageNumbers() =
      runTest(dispatcher.scheduler) {
        val contextId = "favorites-context"
        val model = SubmissionContextScreenModel()
        val adapter =
            FakeSubmissionSourceAdapter(
                paginationModel =
                    SubmissionPaginationModel(
                        kind = SubmissionPaginationKind.CURSOR,
                        hasFirstPage = true,
                        knowsLastPage = false,
                    )
            )
        model.syncRootPage(
            contextId = contextId,
            sourceKind = SubmissionContextSourceKind.FAVORITES,
            adapter = adapter,
            page =
                SubmissionLoadedPage(
                    pageId = "page-1",
                    requestKey = "page-1",
                    items = listOf(testThumbnail(10), testThumbnail(11)),
                    pageNumber = 1,
                    nextRequestKey = "page-2-cursor",
                    firstRequestKey = "page-1",
                ),
            selectedSid = 10,
            revisionKey = "favorites-root",
        )
        adapter.nextResults["page-2-cursor"] =
            SubmissionLoadedPage(
                pageId = "page-2",
                requestKey = "page-2-cursor",
                items = listOf(testThumbnail(20), testThumbnail(21)),
                pageNumber = null,
                previousRequestKey = "page-1",
                nextRequestKey = "page-3-cursor",
                firstRequestKey = "page-1",
            )

        model.navigateToNextPage(contextId)
        runCurrent()

        val snapshot = assertNotNull(model.snapshot(contextId))
        assertEquals(listOf("page-1", "page-2"), snapshot.pages.map(SubmissionPageSnapshot::pageId))
        assertEquals(20, snapshot.waterfallViewport.scrollRequest?.sid)
        assertEquals("page-2-cursor", snapshot.currentWaterfallPageTarget()?.page?.requestKey)
      }

  @Test
  fun appendDuplicatePageNumberMarksLastPageWithoutAppendingCache() =
      runTest(dispatcher.scheduler) {
        val contextId = "browse-last-page-context"
        val model = SubmissionContextScreenModel()
        val adapter =
            FakeSubmissionSourceAdapter(
                paginationModel =
                    SubmissionPaginationModel(
                        kind = SubmissionPaginationKind.NUMBERED,
                        hasFirstPage = true,
                        knowsLastPage = false,
                    )
            )
        model.syncRootPage(
            contextId = contextId,
            sourceKind = SubmissionContextSourceKind.BROWSE,
            adapter = adapter,
            page =
                SubmissionLoadedPage(
                    pageId = "page-998",
                    requestKey = "page-998",
                    items = listOf(testThumbnail(9980), testThumbnail(9981)),
                    pageNumber = 998,
                    nextRequestKey = "page-999",
                    firstRequestKey = "page-1",
                ),
            selectedSid = 9980,
            revisionKey = "browse-last-page",
        )
        adapter.nextResults["page-999"] =
            SubmissionLoadedPage(
                pageId = "page-999",
                requestKey = "page-999",
                items = listOf(testThumbnail(9990), testThumbnail(9991)),
                pageNumber = 999,
                previousRequestKey = "page-998",
                nextRequestKey = "page-1000",
                firstRequestKey = "page-1",
            )
        model.loadNextPageIfNeeded(contextId)
        runCurrent()
        adapter.nextResults["page-1000"] =
            SubmissionLoadedPage(
                pageId = "page-999-again",
                requestKey = "page-1000",
                items = listOf(testThumbnail(9990), testThumbnail(9991)),
                pageNumber = 999,
                previousRequestKey = "page-998",
                nextRequestKey = null,
                firstRequestKey = "page-1",
            )

        model.loadNextPageIfNeeded(contextId)
        runCurrent()

        val snapshot = assertNotNull(model.snapshot(contextId))
        assertEquals(
            listOf("page-998", "page-999"),
            snapshot.pages.map(SubmissionPageSnapshot::pageId),
        )
        assertEquals(999, snapshot.knownLastPageNumber())
        assertFalse(snapshot.hasNextPage)
      }

  @Test
  fun syncRootPageDropsNonRootWindowAndStartsFromRootAgain() =
      runTest(dispatcher.scheduler) {
        val contextId = "search-sync-root-context"
        val model = SubmissionContextScreenModel()
        val adapter =
            FakeSubmissionSourceAdapter(
                paginationModel =
                    SubmissionPaginationModel(
                        kind = SubmissionPaginationKind.NUMBERED,
                        hasFirstPage = true,
                        knowsLastPage = true,
                    )
            )
        model.syncRootPage(
            contextId = contextId,
            sourceKind = SubmissionContextSourceKind.SEARCH,
            adapter = adapter,
            page =
                SubmissionLoadedPage(
                    pageId = "page-1",
                    requestKey = "page-1",
                    items = listOf(testThumbnail(10), testThumbnail(11)),
                    pageNumber = 1,
                    nextRequestKey = "page-2",
                    firstRequestKey = "page-1",
                ),
            selectedSid = 10,
            revisionKey = "search-root",
        )
        adapter.jumpResults[5] =
            SubmissionLoadedPage(
                pageId = "page-5",
                requestKey = "page-5",
                items = listOf(testThumbnail(50), testThumbnail(51)),
                pageNumber = 5,
                previousRequestKey = "page-4",
                nextRequestKey = "page-6",
                firstRequestKey = "page-1",
            )

        model.navigateToPage(contextId, 5)
        runCurrent()
        model.syncRootPage(
            contextId = contextId,
            sourceKind = SubmissionContextSourceKind.SEARCH,
            adapter = adapter,
            page =
                SubmissionLoadedPage(
                    pageId = "page-1",
                    requestKey = "page-1",
                    items = listOf(testThumbnail(10), testThumbnail(11)),
                    pageNumber = 1,
                    nextRequestKey = "page-2",
                    firstRequestKey = "page-1",
                ),
            selectedSid = 10,
            revisionKey = "search-root",
        )

        val snapshot = assertNotNull(model.snapshot(contextId))
        assertEquals(listOf("page-1"), snapshot.pages.map(SubmissionPageSnapshot::pageId))
        assertEquals(listOf(10, 11), snapshot.flatItems.map(SubmissionThumbnail::id))
        assertEquals(10, snapshot.selectedSid)
      }
}

private class FakeSubmissionSourceAdapter(
    override val paginationModel: SubmissionPaginationModel =
        SubmissionPaginationModel(kind = SubmissionPaginationKind.CURSOR),
) : SubmissionSourceAdapter {
  override val sourceKind: SubmissionContextSourceKind = SubmissionContextSourceKind.SEARCH
  val nextResults: MutableMap<String, SubmissionLoadedPage> = mutableMapOf()
  val previousResults: MutableMap<String, SubmissionLoadedPage> = mutableMapOf()
  val jumpResults: MutableMap<Int, SubmissionLoadedPage> = mutableMapOf()
  var firstResult: SubmissionLoadedPage? = null
  var lastResult: SubmissionLoadedPage? = null
  var nextCalls: Int = 0
  var previousCalls: Int = 0
  var jumpCalls: Int = 0
  var firstCalls: Int = 0
  var lastCalls: Int = 0

  override suspend fun loadInitialPage(): PageState<SubmissionLoadedPage> =
      PageState.Error(IllegalStateException("Unused in test"))

  override suspend fun loadNextPage(requestKey: String): PageState<SubmissionLoadedPage> =
      nextResults[requestKey]?.also { nextCalls += 1 }?.let { page -> PageState.Success(page) }
          ?: PageState.Error(IllegalStateException("Missing next page for $requestKey"))

  override suspend fun loadPreviousPage(requestKey: String): PageState<SubmissionLoadedPage> =
      previousResults[requestKey]
          ?.also { previousCalls += 1 }
          ?.let { page -> PageState.Success(page) }
          ?: PageState.Error(IllegalStateException("Missing previous page for $requestKey"))

  override suspend fun jumpToPage(pageNumber: Int): PageState<SubmissionLoadedPage> =
      jumpResults[pageNumber]?.also { jumpCalls += 1 }?.let { page -> PageState.Success(page) }
          ?: PageState.Error(IllegalStateException("Missing jump page for $pageNumber"))

  override suspend fun loadFirstPage(): PageState<SubmissionLoadedPage> =
      firstResult?.also { firstCalls += 1 }?.let { page -> PageState.Success(page) }
          ?: PageState.Error(IllegalStateException("Missing first page"))

  override suspend fun loadLastPage(): PageState<SubmissionLoadedPage> =
      lastResult?.also { lastCalls += 1 }?.let { page -> PageState.Success(page) }
          ?: PageState.Error(IllegalStateException("Missing last page"))
}

private fun testThumbnail(sid: Int): SubmissionThumbnail =
    SubmissionThumbnail(
        id = sid,
        submissionUrl = FaUrls.submission(sid),
        title = "title-$sid",
        author = "author",
        authorAvatarUrl = "",
        thumbnailUrl = "https://t.furaffinity.net/$sid@400-0.jpg",
        thumbnailAspectRatio = 1f,
        categoryTag = "c_all",
    )
