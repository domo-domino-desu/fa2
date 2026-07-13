package me.domino.fa2.ui.pages.submission.pager

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import fa2.shared.generated.resources.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.domain.submissionviewer.adapters.SeedSubmissionSourceAdapter
import me.domino.fa2.domain.submissionviewer.api.ContextLoadingState
import me.domino.fa2.domain.submissionviewer.api.SubmissionContextSnapshot
import me.domino.fa2.domain.submissionviewer.api.SubmissionContextSourceKind
import me.domino.fa2.domain.submissionviewer.api.SubmissionPageSnapshot
import me.domino.fa2.domain.submissionviewer.api.SubmissionPaginationModel
import me.domino.fa2.domain.submissionviewer.api.SubmissionSourceAdapter
import me.domino.fa2.domain.submissionviewer.api.WaterfallScrollRequest
import me.domino.fa2.domain.submissionviewer.api.WaterfallViewportState
import me.domino.fa2.domain.submissionviewer.api.matches
import me.domino.fa2.domain.submissionviewer.api.toSnapshot
import me.domino.fa2.ui.components.paging.PageWindowLoadKind
import me.domino.fa2.ui.i18n.appString
import me.domino.fa2.utils.logging.FaLog

class SubmissionContextScreenModel : ScreenModel {
  private val log = FaLog.withTag("SubmissionContext")
  private val contexts: MutableMap<String, SubmissionContextRecord> = mutableMapOf()
  private val reducer = SubmissionContextSnapshotReducer

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
    val page = SubmissionPageSnapshot(pageId = "seed:$contextId", requestKey = null, items = items)
    record.adapter = SeedSubmissionSourceAdapter(sourceKind = sourceKind, items = items)
    record.state.value =
        SubmissionContextSnapshot(
            contextId = contextId,
            sourceKind = sourceKind,
            paginationModel = record.adapter?.paginationModel ?: SubmissionPaginationModel(),
            pages = listOf(page),
            flatItems = items,
            selectedSid = reducer.resolveSelectedSid(items, selectedSid),
            selectedFlatIndex = reducer.resolveSelectedIndex(items, selectedSid),
        )
  }

  internal fun ensureSeedContext(
      contextId: String,
      adapter: SubmissionSourceAdapter,
      initialPage: SubmissionPageSnapshot,
      selectedSid: Int?,
      revisionKey: String? = null,
  ) {
    val record = recordOf(contextId)
    if (record.state.value != null) return
    record.adapter = adapter
    record.state.value =
        reducer.build(
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
      page: SubmissionPageSnapshot,
      selectedSid: Int? = null,
      revisionKey: String? = null,
  ) {
    val record = recordOf(contextId)
    val incomingPage = page.toSnapshot()
    val current = record.state.value
    record.adapter = adapter
    if (current == null) {
      record.state.value =
          reducer.build(
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

    // Returning from details recomposes the list and emits its cached first page again. Once the
    // session has navigated away from that root page, that emission must not replace the active
    // page window.
    if (
        current.revisionKey == revisionKey &&
            current.pages.isNotEmpty() &&
            current.pages.none { page -> page.matches(incomingPage) }
    ) {
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
          reducer.keepContinuousTailFromRoot(
              incomingRootPage = incomingPage,
              existingPages = current.pages,
          )
        } else {
          listOf(incomingPage)
        }
    record.state.value =
        reducer.build(
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
          selectedFlatIndex = reducer.resolveSelectedIndex(current.flatItems, sid),
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
                  currentPageNumber =
                      if (current.loading.jumpLoading) {
                        current.waterfallViewport.currentPageNumber
                      } else {
                        currentPageNumber
                      },
              )
      )
    }
  }

  fun requestScrollToSubmission(contextId: String, sid: Int) {
    requestScrollToSubmission(contextId = contextId, sid = sid, animated = false)
  }

  private fun requestScrollToSubmission(contextId: String, sid: Int, animated: Boolean) {
    updateSnapshot(contextId) { current ->
      if (current.flatItems.none { item -> item.id == sid }) return@updateSnapshot current
      val nextVersion = (current.waterfallViewport.scrollRequest?.version ?: 0L) + 1L
      current.copy(
          waterfallViewport =
              current.waterfallViewport.copy(
                  anchorSid = sid,
                  scrollRequest =
                      WaterfallScrollRequest(sid = sid, version = nextVersion, animated = animated),
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
      current.copy(waterfallViewport = current.waterfallViewport.copy(scrollRequest = null))
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
    startLoad(contextId = contextId, kind = PageWindowLoadKind.APPEND) {
      adapter.loadNextPage(nextKey)
    }
  }

  fun loadPreviousPageIfNeeded(contextId: String, force: Boolean = false) {
    val snapshot = snapshot(contextId) ?: return
    val adapter = contexts[contextId]?.adapter ?: return
    val previousKey = snapshot.pages.firstOrNull()?.previousRequestKey ?: return
    if (!adapter.paginationModel.supportsAdjacentPaging) return
    val loading = snapshot.loading
    if (loading.appendLoading || loading.jumpLoading || loading.prependLoading) return
    if (!force && !loading.prependErrorMessage.isNullOrBlank()) return
    startLoad(contextId = contextId, kind = PageWindowLoadKind.PREPEND) {
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
        kind = PageWindowLoadKind.JUMP,
        scrollToLoadedPage = true,
        scrollAnimated = true,
        pageMerger = { existing, incoming ->
          reducer.mergeBoundaryPage(
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
        kind = PageWindowLoadKind.JUMP,
        scrollToLoadedPage = true,
        scrollAnimated = true,
        pageMerger = { existing, incoming ->
          reducer.mergeBoundaryPage(
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
    startLoad(contextId = contextId, kind = PageWindowLoadKind.JUMP, replace = true) {
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
        kind = PageWindowLoadKind.JUMP,
        scrollToLoadedPage = true,
        pageMerger = { existing, incoming ->
          reducer.mergeBoundaryPage(
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
    startLoad(contextId = contextId, kind = PageWindowLoadKind.JUMP, replace = true) {
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
        kind = PageWindowLoadKind.JUMP,
        scrollToLoadedPage = true,
        pageMerger = { existing, incoming ->
          reducer.mergeBoundaryPage(
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
    startLoad(
        contextId = contextId,
        kind = PageWindowLoadKind.JUMP,
        replace = true,
        targetPageNumber = pageNumber,
        scrollToLoadedPage = true,
    ) {
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
                kind = PageWindowLoadKind.JUMP,
                targetPageNumber = pageNumber,
                scrollToLoadedPage = true,
                pageMerger = { existing, incoming ->
                  reducer.mergeBoundaryPage(
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
                kind = PageWindowLoadKind.JUMP,
                targetPageNumber = pageNumber,
                scrollToLoadedPage = true,
                pageMerger = { existing, incoming ->
                  reducer.mergeBoundaryPage(
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
                kind = PageWindowLoadKind.JUMP,
                replace = true,
                targetPageNumber = pageNumber,
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
        kind = PageWindowLoadKind.JUMP,
        replace = true,
        targetPageNumber = pageNumber,
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
      kind: PageWindowLoadKind,
      replace: Boolean = false,
      scrollToLoadedPage: Boolean = false,
      scrollAnimated: Boolean = false,
      targetPageNumber: Int? = null,
      pageMerger:
          ((List<SubmissionPageSnapshot>, SubmissionPageSnapshot) -> List<
                  SubmissionPageSnapshot
              >)? =
          null,
      block: suspend () -> ResourceResult<SubmissionPageSnapshot>,
  ) {
    val record = contexts[contextId] ?: return
    record.loadingJob?.cancel()
    val rollback = record.state.value ?: return
    updateSnapshot(contextId) { current ->
      val scrollRequest =
          if (kind == PageWindowLoadKind.JUMP && targetPageNumber != null) {
            current.pages.firstOrNull()?.items?.firstOrNull()?.id?.let { sid ->
              WaterfallScrollRequest(
                  sid = sid,
                  version = (current.waterfallViewport.scrollRequest?.version ?: 0L) + 1L,
              )
            }
          } else {
            current.waterfallViewport.scrollRequest
          }
      current.copy(
          loading =
              when (kind) {
                PageWindowLoadKind.PREPEND ->
                    current.loading.copy(prependLoading = true, prependErrorMessage = null)
                PageWindowLoadKind.APPEND ->
                    current.loading.copy(appendLoading = true, appendErrorMessage = null)
                PageWindowLoadKind.JUMP ->
                    current.loading.copy(jumpLoading = true, jumpErrorMessage = null)
              },
          waterfallViewport =
              current.waterfallViewport.copy(
                  firstVisibleItemIndex =
                      if (targetPageNumber != null) 0
                      else current.waterfallViewport.firstVisibleItemIndex,
                  firstVisibleItemScrollOffset =
                      if (targetPageNumber != null) 0
                      else current.waterfallViewport.firstVisibleItemScrollOffset,
                  currentPageNumber =
                      targetPageNumber ?: current.waterfallViewport.currentPageNumber,
                  scrollRequest = scrollRequest,
              ),
      )
    }
    record.loadingJob =
        screenModelScope.launch {
          when (val result = block()) {
            is ResourceResult.Success -> {
              val current = snapshot(contextId) ?: return@launch
              val incomingPage = result.data.toSnapshot()
              val duplicateBoundaryPages =
                  reducer.resolveDuplicateBoundaryPages(
                      existing = current.pages,
                      incoming = incomingPage,
                      kind = kind,
                  )
              val mergedPages =
                  duplicateBoundaryPages
                      ?: pageMerger?.invoke(current.pages, incomingPage)
                      ?: when (kind) {
                        PageWindowLoadKind.PREPEND ->
                            reducer.mergePages(
                                existing = current.pages,
                                incoming = incomingPage,
                                prepend = true,
                            )
                        PageWindowLoadKind.APPEND ->
                            reducer.mergePages(
                                existing = current.pages,
                                incoming = incomingPage,
                                prepend = false,
                            )
                        PageWindowLoadKind.JUMP ->
                            if (replace) listOf(incomingPage)
                            else reducer.insertPage(current.pages, incomingPage)
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
                reducer.build(
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
                scrollToPage(contextId = contextId, page = scrollTarget, animated = scrollAnimated)
              }
            }

            ResourceResult.CfChallenge ->
                updateLoadingError(
                    contextId,
                    kind,
                    appString(Res.string.cloudflare_challenge_title),
                    rollback,
                )

            is ResourceResult.AuthRequired ->
                updateLoadingError(contextId, kind, result.message, rollback)
            is ResourceResult.MatureBlocked ->
                updateLoadingError(contextId, kind, result.reason, rollback)
            is ResourceResult.Error ->
                updateLoadingError(
                    contextId,
                    kind,
                    result.exception.message ?: result.exception.toString(),
                    rollback,
                )
          }
        }
  }

  private fun updateLoadingError(
      contextId: String,
      kind: PageWindowLoadKind,
      message: String?,
      rollback: SubmissionContextSnapshot,
  ) {
    updateSnapshot(contextId) { current ->
      val base = if (kind == PageWindowLoadKind.JUMP) rollback else current
      val restoreRequest =
          if (kind == PageWindowLoadKind.JUMP) {
            rollback.waterfallViewport.anchorSid?.let { sid ->
              WaterfallScrollRequest(
                  sid = sid,
                  version = (current.waterfallViewport.scrollRequest?.version ?: 0L) + 1L,
              )
            }
          } else {
            base.waterfallViewport.scrollRequest
          }
      base.copy(
          loading =
              when (kind) {
                PageWindowLoadKind.PREPEND ->
                    base.loading.copy(prependLoading = false, prependErrorMessage = message)
                PageWindowLoadKind.APPEND ->
                    base.loading.copy(appendLoading = false, appendErrorMessage = message)
                PageWindowLoadKind.JUMP ->
                    base.loading.copy(jumpLoading = false, jumpErrorMessage = message)
              },
          waterfallViewport = base.waterfallViewport.copy(scrollRequest = restoreRequest),
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
      kind: PageWindowLoadKind,
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
