package me.domino.fa2.ui.pages.submission

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import fa2.shared.generated.resources.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.i18n.appString
import me.domino.fa2.util.logging.FaLog

class SubmissionContextScreenModel : ScreenModel {
  private val log = FaLog.withTag("SubmissionContext")
  private val contexts: MutableMap<String, SubmissionContextRecord> = mutableMapOf()

  private companion object {
    const val pageScrollCandidateCount = 12
  }

  fun state(contextId: String): StateFlow<SubmissionContextSnapshot?> =
      recordOf(contextId).state.asStateFlow()

  fun snapshot(contextId: String): SubmissionContextSnapshot? = contexts[contextId]?.state?.value

  fun ensureSeedContext(
      contextId: String,
      sourceKind: SubmissionContextSourceKind,
      items: List<SubmissionThumbnail>,
      selectedSid: Int?,
  ) {
    val record = recordOf(contextId)
    if (record.state.value != null) return
    val page =
        SubmissionPageSnapshot(
            pageId = "seed:$contextId",
            requestKey = null,
            items = items,
        )
    record.adapter = SeedSubmissionSourceAdapter(sourceKind = sourceKind, items = items)
    record.state.value =
        SubmissionContextSnapshot(
            contextId = contextId,
            sourceKind = sourceKind,
            paginationModel = record.adapter?.paginationModel ?: SubmissionPaginationModel(),
            pages = listOf(page),
            flatItems = items,
            selectedSid = resolveSelectedSid(items, selectedSid),
            selectedFlatIndex = resolveSelectedIndex(items, selectedSid),
        )
  }

  internal fun ensureSeedContext(
      contextId: String,
      adapter: SubmissionSourceAdapter,
      initialPage: SubmissionLoadedPage,
      selectedSid: Int?,
      revisionKey: String? = null,
  ) {
    val record = recordOf(contextId)
    if (record.state.value != null) return
    record.adapter = adapter
    record.state.value =
        buildSnapshot(
            contextId = contextId,
            sourceKind = adapter.sourceKind,
            paginationModel = adapter.paginationModel,
            pages = listOf(initialPage.toSnapshot()),
            selectedSid = selectedSid,
            loading = ContextLoadingState(),
            viewport = WaterfallViewportState(anchorSid = selectedSid),
            revisionKey = revisionKey,
        )
  }

  internal fun syncRootPage(
      contextId: String,
      sourceKind: SubmissionContextSourceKind,
      adapter: SubmissionSourceAdapter,
      page: SubmissionLoadedPage,
      selectedSid: Int? = null,
      revisionKey: String? = null,
  ) {
    val record = recordOf(contextId)
    val incomingPage = page.toSnapshot()
    val current = record.state.value
    record.adapter = adapter
    if (current == null) {
      record.state.value =
          buildSnapshot(
              contextId = contextId,
              sourceKind = sourceKind,
              paginationModel = adapter.paginationModel,
              pages = listOf(incomingPage),
              selectedSid = selectedSid,
              loading = ContextLoadingState(),
              viewport = WaterfallViewportState(anchorSid = selectedSid),
              revisionKey = revisionKey,
          )
      return
    }

    val shouldKeepCachedTail =
        current.revisionKey == revisionKey &&
            current.pages.isNotEmpty() &&
            current.pages.first().requestKey == incomingPage.requestKey &&
            current.flatItems.size >= incomingPage.items.size &&
            current.flatItems.take(incomingPage.items.size).map(SubmissionThumbnail::id) ==
                incomingPage.items.map(SubmissionThumbnail::id)
    val mergedPages =
        if (shouldKeepCachedTail) {
          keepContinuousTailFromRoot(
              incomingRootPage = incomingPage,
              existingPages = current.pages,
          )
        } else {
          listOf(incomingPage)
        }
    record.state.value =
        buildSnapshot(
            contextId = contextId,
            sourceKind = sourceKind,
            paginationModel = adapter.paginationModel,
            pages = mergedPages,
            selectedSid = selectedSid ?: current.selectedSid,
            loading = current.loading.copy(initialLoading = false),
            viewport = current.waterfallViewport,
            revisionKey = revisionKey,
        )
  }

  fun selectSubmission(contextId: String, sid: Int) {
    val snapshot = snapshot(contextId) ?: return
    if (snapshot.flatItems.none { item -> item.id == sid }) return
    updateSnapshot(contextId) { current ->
      current.copy(
          selectedSid = sid,
          selectedFlatIndex = resolveSelectedIndex(current.flatItems, sid),
      )
    }
  }

  fun moveSelection(contextId: String, delta: Int): Boolean {
    val snapshot = snapshot(contextId) ?: return false
    if (snapshot.flatItems.isEmpty()) return false
    val targetIndex = (snapshot.selectedFlatIndex + delta).coerceIn(0, snapshot.flatItems.lastIndex)
    if (targetIndex == snapshot.selectedFlatIndex) return false
    val sid = snapshot.flatItems[targetIndex].id
    selectSubmission(contextId, sid)
    return true
  }

  fun updateWaterfallViewport(
      contextId: String,
      firstVisibleItemIndex: Int,
      firstVisibleItemScrollOffset: Int,
      anchorSid: Int?,
      currentPageNumber: Int?,
  ) {
    updateSnapshot(contextId) { current ->
      current.copy(
          waterfallViewport =
              current.waterfallViewport.copy(
                  firstVisibleItemIndex = firstVisibleItemIndex.coerceAtLeast(0),
                  firstVisibleItemScrollOffset = firstVisibleItemScrollOffset.coerceAtLeast(0),
                  anchorSid = anchorSid,
                  currentPageNumber = currentPageNumber,
              )
      )
    }
  }

  fun requestScrollToSubmission(contextId: String, sid: Int) {
    requestScrollToSubmission(contextId = contextId, sid = sid, animated = false)
  }

  private fun requestScrollToSubmission(
      contextId: String,
      sid: Int,
      animated: Boolean,
  ) {
    updateSnapshot(contextId) { current ->
      if (current.flatItems.none { item -> item.id == sid }) return@updateSnapshot current
      val nextVersion = (current.waterfallViewport.scrollRequest?.version ?: 0L) + 1L
      current.copy(
          waterfallViewport =
              current.waterfallViewport.copy(
                  anchorSid = sid,
                  scrollRequest =
                      WaterfallScrollRequest(
                          sid = sid,
                          version = nextVersion,
                          animated = animated,
                      ),
              )
      )
    }
  }

  fun requestScrollToSelectedSubmission(contextId: String) {
    val sid = snapshot(contextId)?.selectedSid ?: return
    requestScrollToSubmission(contextId, sid)
  }

  fun consumeWaterfallScrollRequest(contextId: String, version: Long) {
    updateSnapshot(contextId) { current ->
      val request = current.waterfallViewport.scrollRequest ?: return@updateSnapshot current
      if (request.version != version) return@updateSnapshot current
      current.copy(
          waterfallViewport =
              current.waterfallViewport.copy(
                  scrollRequest = null,
              )
      )
    }
  }

  fun loadNextPageIfNeeded(contextId: String, force: Boolean = false) {
    val snapshot = snapshot(contextId) ?: return
    val adapter = contexts[contextId]?.adapter ?: return
    val nextKey = snapshot.pages.lastOrNull()?.nextRequestKey ?: return
    if (!adapter.paginationModel.supportsAdjacentPaging) return
    val loading = snapshot.loading
    if (loading.appendLoading || loading.jumpLoading || loading.prependLoading) return
    if (!force && !loading.appendErrorMessage.isNullOrBlank()) return
    startLoad(contextId = contextId, kind = LoadKind.APPEND) { adapter.loadNextPage(nextKey) }
  }

  fun loadPreviousPageIfNeeded(contextId: String, force: Boolean = false) {
    val snapshot = snapshot(contextId) ?: return
    val adapter = contexts[contextId]?.adapter ?: return
    val previousKey = snapshot.pages.firstOrNull()?.previousRequestKey ?: return
    if (!adapter.paginationModel.supportsAdjacentPaging) return
    val loading = snapshot.loading
    if (loading.appendLoading || loading.jumpLoading || loading.prependLoading) return
    if (!force && !loading.prependErrorMessage.isNullOrBlank()) return
    startLoad(contextId = contextId, kind = LoadKind.PREPEND) {
      adapter.loadPreviousPage(previousKey)
    }
  }

  fun navigateToPreviousPage(contextId: String) {
    val snapshot = snapshot(contextId) ?: return
    val adapter = contexts[contextId]?.adapter ?: return
    if (!adapter.paginationModel.supportsAdjacentPaging) return
    val currentTarget = snapshot.currentWaterfallPageTarget() ?: return
    snapshot.cachedPreviousPageTarget(currentTarget)?.let { target ->
      scrollToPage(contextId, target.page, animated = true)
      return
    }
    val previousKey = currentTarget.page.previousRequestKey ?: return
    startLoad(
        contextId = contextId,
        kind = LoadKind.JUMP,
        scrollToLoadedPage = true,
        scrollAnimated = true,
        pageMerger = { existing, incoming ->
          mergeBoundaryPage(
              existing = existing,
              incoming = incoming,
              prepend = true,
              replaceOnDiscontinuity = false,
          )
        },
    ) {
      adapter.loadPreviousPage(previousKey)
    }
  }

  fun navigateToNextPage(contextId: String) {
    val snapshot = snapshot(contextId) ?: return
    val adapter = contexts[contextId]?.adapter ?: return
    if (!adapter.paginationModel.supportsAdjacentPaging) return
    val currentTarget = snapshot.currentWaterfallPageTarget() ?: return
    snapshot.cachedNextPageTarget(currentTarget)?.let { target ->
      scrollToPage(contextId, target.page, animated = true)
      return
    }
    val nextKey = currentTarget.page.nextRequestKey ?: return
    startLoad(
        contextId = contextId,
        kind = LoadKind.JUMP,
        scrollToLoadedPage = true,
        scrollAnimated = true,
        pageMerger = { existing, incoming ->
          mergeBoundaryPage(
              existing = existing,
              incoming = incoming,
              prepend = false,
              replaceOnDiscontinuity = false,
          )
        },
    ) {
      adapter.loadNextPage(nextKey)
    }
  }

  fun loadFirstPage(contextId: String) {
    val adapter = contexts[contextId]?.adapter ?: return
    if (!adapter.paginationModel.hasFirstPage) return
    startLoad(contextId = contextId, kind = LoadKind.JUMP, replace = true) {
      adapter.loadFirstPage()
    }
  }

  fun navigateToFirstPage(contextId: String) {
    val snapshot = snapshot(contextId) ?: return
    val adapter = contexts[contextId]?.adapter ?: return
    if (!adapter.paginationModel.hasFirstPage) return
    snapshot.pages
        .firstOrNull { page -> page.pageNumber == 1 || page.previousRequestKey == null }
        ?.let { page ->
          scrollToPage(contextId, page)
          return
        }
    startLoad(
        contextId = contextId,
        kind = LoadKind.JUMP,
        scrollToLoadedPage = true,
        pageMerger = { existing, incoming ->
          mergeBoundaryPage(
              existing = existing,
              incoming = incoming,
              prepend = true,
              replaceOnDiscontinuity = true,
          )
        },
    ) {
      adapter.loadFirstPage()
    }
  }

  fun loadLastPage(contextId: String) {
    val adapter = contexts[contextId]?.adapter ?: return
    if (!adapter.paginationModel.knowsLastPage) return
    startLoad(contextId = contextId, kind = LoadKind.JUMP, replace = true) {
      adapter.loadLastPage()
    }
  }

  fun navigateToLastPage(contextId: String) {
    val snapshot = snapshot(contextId) ?: return
    val adapter = contexts[contextId]?.adapter ?: return
    if (!adapter.paginationModel.knowsLastPage) return
    snapshot.pages
        .lastOrNull()
        ?.takeIf { page -> page.nextRequestKey == null }
        ?.let { page ->
          scrollToPage(contextId, page)
          return
        }
    startLoad(
        contextId = contextId,
        kind = LoadKind.JUMP,
        scrollToLoadedPage = true,
        pageMerger = { existing, incoming ->
          mergeBoundaryPage(
              existing = existing,
              incoming = incoming,
              prepend = false,
              replaceOnDiscontinuity = true,
          )
        },
    ) {
      adapter.loadLastPage()
    }
  }

  fun jumpToPage(contextId: String, pageNumber: Int) {
    val adapter = contexts[contextId]?.adapter ?: return
    if (!adapter.paginationModel.supportsJumpToPage) return
    startLoad(contextId = contextId, kind = LoadKind.JUMP, replace = true) {
      adapter.jumpToPage(pageNumber)
    }
  }

  fun navigateToPage(contextId: String, pageNumber: Int) {
    if (pageNumber <= 0) return
    val snapshot = snapshot(contextId) ?: return
    val adapter = contexts[contextId]?.adapter ?: return
    snapshot.pages
        .firstOrNull { page -> page.pageNumber == pageNumber }
        ?.let { page ->
          scrollToPage(contextId, page)
          return
        }
    val currentTarget = snapshot.currentWaterfallPageTarget()
    val currentPageNumber = currentTarget?.page?.pageNumber
    if (currentPageNumber != null && kotlin.math.abs(pageNumber - currentPageNumber) == 1) {
      when {
        pageNumber < currentPageNumber &&
            currentTarget.page.previousRequestKey != null &&
            adapter.paginationModel.supportsAdjacentPaging ->
            startLoad(
                contextId = contextId,
                kind = LoadKind.JUMP,
                scrollToLoadedPage = true,
                pageMerger = { existing, incoming ->
                  mergeBoundaryPage(
                      existing = existing,
                      incoming = incoming,
                      prepend = true,
                      replaceOnDiscontinuity = false,
                  )
                },
            ) {
              adapter.loadPreviousPage(currentTarget.page.previousRequestKey)
            }

        pageNumber > currentPageNumber &&
            currentTarget.page.nextRequestKey != null &&
            adapter.paginationModel.supportsAdjacentPaging ->
            startLoad(
                contextId = contextId,
                kind = LoadKind.JUMP,
                scrollToLoadedPage = true,
                pageMerger = { existing, incoming ->
                  mergeBoundaryPage(
                      existing = existing,
                      incoming = incoming,
                      prepend = false,
                      replaceOnDiscontinuity = false,
                  )
                },
            ) {
              adapter.loadNextPage(currentTarget.page.nextRequestKey)
            }

        adapter.paginationModel.supportsJumpToPage ->
            startLoad(
                contextId = contextId,
                kind = LoadKind.JUMP,
                replace = true,
                scrollToLoadedPage = true,
            ) {
              adapter.jumpToPage(pageNumber)
            }
      }
      return
    }
    if (!adapter.paginationModel.supportsJumpToPage) return
    startLoad(
        contextId = contextId,
        kind = LoadKind.JUMP,
        replace = true,
        scrollToLoadedPage = true,
    ) {
      adapter.jumpToPage(pageNumber)
    }
  }

  fun clear(contextId: String) {
    contexts.remove(contextId)?.loadingJob?.cancel()
  }

  override fun onDispose() {
    contexts.values.forEach { record -> record.loadingJob?.cancel() }
    contexts.clear()
  }

  private fun startLoad(
      contextId: String,
      kind: LoadKind,
      replace: Boolean = false,
      scrollToLoadedPage: Boolean = false,
      scrollAnimated: Boolean = false,
      pageMerger:
          ((List<SubmissionPageSnapshot>, SubmissionPageSnapshot) -> List<
                  SubmissionPageSnapshot
              >)? =
          null,
      block: suspend () -> PageState<SubmissionLoadedPage>,
  ) {
    val record = contexts[contextId] ?: return
    record.loadingJob?.cancel()
    updateSnapshot(contextId) { current ->
      current.copy(
          loading =
              when (kind) {
                LoadKind.PREPEND ->
                    current.loading.copy(prependLoading = true, prependErrorMessage = null)
                LoadKind.APPEND ->
                    current.loading.copy(appendLoading = true, appendErrorMessage = null)
                LoadKind.JUMP -> current.loading.copy(jumpLoading = true, jumpErrorMessage = null)
              }
      )
    }
    record.loadingJob =
        screenModelScope.launch {
          when (val result = block()) {
            is PageState.Success -> {
              val current = snapshot(contextId) ?: return@launch
              val incomingPage = result.data.toSnapshot()
              val duplicateBoundaryPages =
                  resolveDuplicateBoundaryPages(
                      existing = current.pages,
                      incoming = incomingPage,
                      kind = kind,
                  )
              val mergedPages =
                  duplicateBoundaryPages
                      ?: pageMerger?.invoke(current.pages, incomingPage)
                      ?: when (kind) {
                        LoadKind.PREPEND ->
                            mergePages(
                                existing = current.pages,
                                incoming = incomingPage,
                                prepend = true,
                            )
                        LoadKind.APPEND ->
                            mergePages(
                                existing = current.pages,
                                incoming = incomingPage,
                                prepend = false,
                            )
                        LoadKind.JUMP ->
                            if (replace) listOf(incomingPage)
                            else insertPage(current.pages, incomingPage)
                      }
              logPageMerge(
                  contextId = contextId,
                  kind = kind,
                  existing = current.pages,
                  incoming = incomingPage,
                  merged = mergedPages,
              )
              val scrollTarget = mergedPages.firstOrNull { page -> page.matches(incomingPage) }
              updateSnapshot(contextId) {
                buildSnapshot(
                    contextId = it.contextId,
                    sourceKind = it.sourceKind,
                    paginationModel = it.paginationModel,
                    pages = mergedPages,
                    selectedSid = it.selectedSid,
                    loading = ContextLoadingState(),
                    viewport = it.waterfallViewport,
                    revisionKey = it.revisionKey,
                )
              }
              if (scrollToLoadedPage && scrollTarget != null) {
                scrollToPage(
                    contextId = contextId,
                    page = scrollTarget,
                    animated = scrollAnimated,
                )
              }
            }

            PageState.CfChallenge ->
                updateLoadingError(
                    contextId,
                    kind,
                    appString(Res.string.cloudflare_challenge_title),
                )

            is PageState.AuthRequired -> updateLoadingError(contextId, kind, result.message)
            is PageState.MatureBlocked -> updateLoadingError(contextId, kind, result.reason)
            is PageState.Error ->
                updateLoadingError(
                    contextId,
                    kind,
                    result.exception.message ?: result.exception.toString(),
                )
            PageState.Loading -> updateLoadingError(contextId, kind, null)
          }
        }
  }

  private fun updateLoadingError(contextId: String, kind: LoadKind, message: String?) {
    updateSnapshot(contextId) { current ->
      current.copy(
          loading =
              when (kind) {
                LoadKind.PREPEND ->
                    current.loading.copy(prependLoading = false, prependErrorMessage = message)
                LoadKind.APPEND ->
                    current.loading.copy(appendLoading = false, appendErrorMessage = message)
                LoadKind.JUMP ->
                    current.loading.copy(jumpLoading = false, jumpErrorMessage = message)
              }
      )
    }
  }

  private fun updateSnapshot(
      contextId: String,
      transform: (SubmissionContextSnapshot) -> SubmissionContextSnapshot,
  ) {
    val record = contexts[contextId] ?: return
    val current = record.state.value ?: return
    record.state.value = transform(current)
  }

  private fun recordOf(contextId: String): SubmissionContextRecord =
      contexts.getOrPut(contextId) { SubmissionContextRecord(state = MutableStateFlow(null)) }

  private fun buildSnapshot(
      contextId: String,
      sourceKind: SubmissionContextSourceKind,
      paginationModel: SubmissionPaginationModel,
      pages: List<SubmissionPageSnapshot>,
      selectedSid: Int?,
      loading: ContextLoadingState,
      viewport: WaterfallViewportState,
      revisionKey: String?,
  ): SubmissionContextSnapshot {
    val flatItems = flattenItems(pages)
    val resolvedSelectedSid = resolveSelectedSid(flatItems, selectedSid)
    val sanitizedViewport =
        sanitizeViewport(
            viewport = viewport,
            pages = pages,
            flatItems = flatItems,
            fallbackSid = resolvedSelectedSid,
        )
    return SubmissionContextSnapshot(
        contextId = contextId,
        sourceKind = sourceKind,
        paginationModel = paginationModel,
        pages = pages,
        flatItems = flatItems,
        selectedSid = resolvedSelectedSid,
        selectedFlatIndex = resolveSelectedIndex(flatItems, resolvedSelectedSid),
        loading = loading,
        waterfallViewport = sanitizedViewport,
        revisionKey = revisionKey,
    )
  }

  private fun mergePages(
      existing: List<SubmissionPageSnapshot>,
      incoming: SubmissionPageSnapshot,
      prepend: Boolean,
  ): List<SubmissionPageSnapshot> {
    if (
        existing.any { page ->
          page.pageId == incoming.pageId ||
              (incoming.requestKey != null && page.requestKey == incoming.requestKey)
        }
    ) {
      return existing
    }
    return if (prepend) listOf(incoming) + existing else existing + incoming
  }

  private fun insertPage(
      existing: List<SubmissionPageSnapshot>,
      incoming: SubmissionPageSnapshot,
  ): List<SubmissionPageSnapshot> {
    if (
        existing.any { page ->
          page.pageId == incoming.pageId ||
              (incoming.requestKey != null && page.requestKey == incoming.requestKey)
        }
    ) {
      return existing
    }
    existing.forEachIndexed { index, page ->
      val shouldInsertBefore =
          page.requestKey != null &&
              (incoming.nextRequestKey == page.requestKey ||
                  page.previousRequestKey == incoming.requestKey)
      if (shouldInsertBefore) {
        return existing.toMutableList().apply { add(index, incoming) }
      }
      val shouldInsertAfter =
          page.requestKey != null &&
              (incoming.previousRequestKey == page.requestKey ||
                  page.nextRequestKey == incoming.requestKey)
      if (shouldInsertAfter) {
        return existing.toMutableList().apply { add(index + 1, incoming) }
      }
    }
    val incomingNumber = incoming.pageNumber
    if (incomingNumber == null) return existing + incoming
    val sorted =
        (existing + incoming).sortedWith(
            compareBy<SubmissionPageSnapshot> { page -> page.pageNumber ?: Int.MAX_VALUE }
                .thenBy { page -> page.pageId }
        )
    return sorted
  }

  private fun flattenItems(pages: List<SubmissionPageSnapshot>): List<SubmissionThumbnail> {
    val map = LinkedHashMap<Int, SubmissionThumbnail>()
    pages.forEach { page -> page.items.forEach { item -> map[item.id] = item } }
    return map.values.toList()
  }

  private fun resolveSelectedSid(
      items: List<SubmissionThumbnail>,
      preferredSid: Int?,
  ): Int? =
      when {
        items.isEmpty() -> null
        preferredSid != null && items.any { item -> item.id == preferredSid } -> preferredSid
        else -> items.first().id
      }

  private fun resolveSelectedIndex(items: List<SubmissionThumbnail>, sid: Int?): Int {
    if (items.isEmpty()) return 0
    val index = sid?.let { targetSid -> items.indexOfFirst { item -> item.id == targetSid } } ?: 0
    return index.coerceAtLeast(0)
  }

  private fun keepContinuousTailFromRoot(
      incomingRootPage: SubmissionPageSnapshot,
      existingPages: List<SubmissionPageSnapshot>,
  ): List<SubmissionPageSnapshot> {
    if (existingPages.isEmpty()) return listOf(incomingRootPage)
    val merged = mutableListOf(incomingRootPage)
    var previous = incomingRootPage
    existingPages.drop(1).forEach { page ->
      if (!page.isDirectNextOf(previous)) {
        return merged
      }
      merged += page
      previous = page
    }
    return merged
  }

  private fun mergeBoundaryPage(
      existing: List<SubmissionPageSnapshot>,
      incoming: SubmissionPageSnapshot,
      prepend: Boolean,
      replaceOnDiscontinuity: Boolean,
  ): List<SubmissionPageSnapshot> {
    if (existing.any { page -> page.matches(incoming) }) return existing
    if (existing.isEmpty()) return listOf(incoming)
    val canMerge =
        if (prepend) {
          incoming.isDirectPreviousOf(existing.first())
        } else {
          incoming.isDirectNextOf(existing.last())
        }
    if (canMerge) {
      return if (prepend) listOf(incoming) + existing else existing + incoming
    }
    return if (replaceOnDiscontinuity) listOf(incoming) else existing
  }

  private fun resolveDuplicateBoundaryPages(
      existing: List<SubmissionPageSnapshot>,
      incoming: SubmissionPageSnapshot,
      kind: LoadKind,
  ): List<SubmissionPageSnapshot>? {
    val incomingPageNumber = incoming.pageNumber ?: return null
    val duplicateIndex = existing.indexOfFirst { page -> page.pageNumber == incomingPageNumber }
    if (duplicateIndex < 0) return null
    return when (kind) {
      LoadKind.APPEND -> {
        val duplicatePage = existing[duplicateIndex]
        val resolvedLastRequestKey = duplicatePage.requestKey ?: incoming.requestKey
        existing.mapIndexed { index, page ->
          when (index) {
            duplicateIndex ->
                page.copy(
                    nextRequestKey = null,
                    lastRequestKey = resolvedLastRequestKey ?: page.lastRequestKey,
                    lastPageNumber = incomingPageNumber,
                )
            else -> page.copy(lastPageNumber = incomingPageNumber)
          }
        }
      }

      LoadKind.PREPEND -> {
        val duplicatePage = existing[duplicateIndex]
        val resolvedFirstRequestKey =
            duplicatePage.firstRequestKey ?: duplicatePage.requestKey ?: incoming.requestKey
        existing.mapIndexed { index, page ->
          when (index) {
            duplicateIndex ->
                page.copy(
                    previousRequestKey = null,
                    firstRequestKey = resolvedFirstRequestKey,
                )
            else -> page
          }
        }
      }

      LoadKind.JUMP -> null
    }
  }

  private fun sanitizeViewport(
      viewport: WaterfallViewportState,
      pages: List<SubmissionPageSnapshot>,
      flatItems: List<SubmissionThumbnail>,
      fallbackSid: Int?,
  ): WaterfallViewportState {
    val validIds = flatItems.mapTo(linkedSetOf()) { item -> item.id }
    val pageNumbers = pages.mapNotNull { page -> page.pageNumber }
    val anchorSid = viewport.anchorSid?.takeIf { sid -> sid in validIds } ?: fallbackSid
    val anchorPage =
        anchorSid?.let { sid ->
          pages.firstOrNull { page -> page.items.any { item -> item.id == sid } }
        }
    val scrollRequest = viewport.scrollRequest?.takeIf { request -> request.sid in validIds }
    val currentPageNumber =
        anchorPage?.pageNumber
            ?: when {
              pageNumbers.isEmpty() -> viewport.currentPageNumber
              else ->
                  viewport.currentPageNumber?.takeIf { pageNumber ->
                    pageNumbers.contains(pageNumber)
                  }
            }
            ?: if (pages.size == 1) pages.firstOrNull()?.pageNumber else null
    val keepScrollPosition = scrollRequest != null || viewport.anchorSid == anchorSid
    return viewport.copy(
        firstVisibleItemIndex = if (keepScrollPosition) viewport.firstVisibleItemIndex else 0,
        firstVisibleItemScrollOffset =
            if (keepScrollPosition) viewport.firstVisibleItemScrollOffset else 0,
        anchorSid = anchorSid,
        currentPageNumber = currentPageNumber,
        scrollRequest = scrollRequest,
    )
  }

  private fun scrollToPage(
      contextId: String,
      page: SubmissionPageSnapshot,
      animated: Boolean = false,
  ) {
    val targetSid = page.items.firstOrNull()?.id ?: return
    val targetPageLeadingSids = page.items.take(pageScrollCandidateCount).map { item -> item.id }
    updateSnapshot(contextId) { current ->
      val nextVersion = (current.waterfallViewport.scrollRequest?.version ?: 0L) + 1L
      current.copy(
          waterfallViewport =
              current.waterfallViewport.copy(
                  anchorSid = targetSid,
                  currentPageNumber = page.pageNumber,
                  scrollRequest =
                      WaterfallScrollRequest(
                          sid = targetSid,
                          version = nextVersion,
                          animated = animated,
                          targetPageLeadingSids = targetPageLeadingSids,
                      ),
              )
      )
    }
  }

  private fun logPageMerge(
      contextId: String,
      kind: LoadKind,
      existing: List<SubmissionPageSnapshot>,
      incoming: SubmissionPageSnapshot,
      merged: List<SubmissionPageSnapshot>,
  ) {
    val existingSummary =
        existing.joinToString(prefix = "[", postfix = "]") { page ->
          page.pageNumber?.toString() ?: page.requestKey.orEmpty()
        }
    val mergedSummary =
        merged.joinToString(prefix = "[", postfix = "]") { page ->
          page.pageNumber?.toString() ?: page.requestKey.orEmpty()
        }
    val incomingSummary = incoming.pageNumber?.toString() ?: incoming.requestKey.orEmpty()
    val mergedNumbers = merged.mapNotNull { page -> page.pageNumber }
    val hasDuplicateNumbers = mergedNumbers.size != mergedNumbers.distinct().size
    val hasDiscontinuity = mergedNumbers.zipWithNext().any { (left, right) -> right - left != 1 }
    log.i {
      "page merge -> context=$contextId,kind=$kind,incoming=$incomingSummary,existing=$existingSummary,merged=$mergedSummary,duplicateNumbers=$hasDuplicateNumbers,discontinuous=$hasDiscontinuity"
    }
  }
}

private data class SubmissionContextRecord(
    val state: MutableStateFlow<SubmissionContextSnapshot?>,
    var adapter: SubmissionSourceAdapter? = null,
    var loadingJob: Job? = null,
)

private enum class LoadKind {
  PREPEND,
  APPEND,
  JUMP,
}
