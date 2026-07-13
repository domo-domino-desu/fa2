package me.domino.fa2.ui.pages.submission.pager

import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.domain.submissionviewer.api.ContextLoadingState
import me.domino.fa2.domain.submissionviewer.api.SubmissionContextSnapshot
import me.domino.fa2.domain.submissionviewer.api.SubmissionContextSourceKind
import me.domino.fa2.domain.submissionviewer.api.SubmissionPageSnapshot
import me.domino.fa2.domain.submissionviewer.api.SubmissionPaginationModel
import me.domino.fa2.domain.submissionviewer.api.WaterfallViewportState
import me.domino.fa2.domain.submissionviewer.api.isDirectNextOf
import me.domino.fa2.domain.submissionviewer.api.isDirectPreviousOf
import me.domino.fa2.domain.submissionviewer.api.matches
import me.domino.fa2.ui.components.paging.PageWindowLoadKind

/** Pure state reducer for page ordering, selection and viewport invariants. */
internal object SubmissionContextSnapshotReducer {
  fun build(
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
    return SubmissionContextSnapshot(
        contextId = contextId,
        sourceKind = sourceKind,
        paginationModel = paginationModel,
        pages = pages,
        flatItems = flatItems,
        selectedSid = resolvedSelectedSid,
        selectedFlatIndex = resolveSelectedIndex(flatItems, resolvedSelectedSid),
        loading = loading,
        waterfallViewport =
            sanitizeViewport(viewport, pages, flatItems, fallbackSid = resolvedSelectedSid),
        revisionKey = revisionKey,
    )
  }

  fun mergePages(
      existing: List<SubmissionPageSnapshot>,
      incoming: SubmissionPageSnapshot,
      prepend: Boolean,
  ): List<SubmissionPageSnapshot> {
    if (
        existing.any {
          it.pageId == incoming.pageId ||
              incoming.requestKey != null && it.requestKey == incoming.requestKey
        }
    ) {
      return existing
    }
    return if (prepend) listOf(incoming) + existing else existing + incoming
  }

  fun insertPage(
      existing: List<SubmissionPageSnapshot>,
      incoming: SubmissionPageSnapshot,
  ): List<SubmissionPageSnapshot> {
    if (
        existing.any {
          it.pageId == incoming.pageId ||
              incoming.requestKey != null && it.requestKey == incoming.requestKey
        }
    ) {
      return existing
    }
    existing.forEachIndexed { index, page ->
      if (
          page.requestKey != null &&
              (incoming.nextRequestKey == page.requestKey ||
                  page.previousRequestKey == incoming.requestKey)
      ) {
        return existing.toMutableList().apply { add(index, incoming) }
      }
      if (
          page.requestKey != null &&
              (incoming.previousRequestKey == page.requestKey ||
                  page.nextRequestKey == incoming.requestKey)
      ) {
        return existing.toMutableList().apply { add(index + 1, incoming) }
      }
    }
    if (incoming.pageNumber == null) return existing + incoming
    return (existing + incoming).sortedWith(
        compareBy<SubmissionPageSnapshot> { it.pageNumber ?: Int.MAX_VALUE }.thenBy { it.pageId }
    )
  }

  fun resolveSelectedSid(items: List<SubmissionThumbnail>, preferredSid: Int?): Int? =
      when {
        items.isEmpty() -> null
        preferredSid != null && items.any { it.id == preferredSid } -> preferredSid
        else -> items.first().id
      }

  fun resolveSelectedIndex(items: List<SubmissionThumbnail>, sid: Int?): Int {
    if (items.isEmpty()) return 0
    return (sid?.let { target -> items.indexOfFirst { it.id == target } } ?: 0).coerceAtLeast(0)
  }

  fun keepContinuousTailFromRoot(
      incomingRootPage: SubmissionPageSnapshot,
      existingPages: List<SubmissionPageSnapshot>,
  ): List<SubmissionPageSnapshot> {
    if (existingPages.isEmpty()) return listOf(incomingRootPage)
    val merged = mutableListOf(incomingRootPage)
    var previous = incomingRootPage
    existingPages.drop(1).forEach { page ->
      if (!page.isDirectNextOf(previous)) return merged
      merged += page
      previous = page
    }
    return merged
  }

  fun mergeBoundaryPage(
      existing: List<SubmissionPageSnapshot>,
      incoming: SubmissionPageSnapshot,
      prepend: Boolean,
      replaceOnDiscontinuity: Boolean,
  ): List<SubmissionPageSnapshot> {
    if (existing.any { it.matches(incoming) }) return existing
    if (existing.isEmpty()) return listOf(incoming)
    val canMerge =
        if (prepend) incoming.isDirectPreviousOf(existing.first())
        else incoming.isDirectNextOf(existing.last())
    if (canMerge) return if (prepend) listOf(incoming) + existing else existing + incoming
    return if (replaceOnDiscontinuity) listOf(incoming) else existing
  }

  fun resolveDuplicateBoundaryPages(
      existing: List<SubmissionPageSnapshot>,
      incoming: SubmissionPageSnapshot,
      kind: PageWindowLoadKind,
  ): List<SubmissionPageSnapshot>? {
    val pageNumber = incoming.pageNumber ?: return null
    val duplicateIndex = existing.indexOfFirst { it.pageNumber == pageNumber }
    if (duplicateIndex < 0) return null
    return when (kind) {
      PageWindowLoadKind.APPEND -> {
        val duplicate = existing[duplicateIndex]
        val lastRequestKey = duplicate.requestKey ?: incoming.requestKey
        existing.mapIndexed { index, page ->
          if (index == duplicateIndex) {
            page.copy(
                nextRequestKey = null,
                lastRequestKey = lastRequestKey ?: page.lastRequestKey,
                lastPageNumber = pageNumber,
            )
          } else {
            page.copy(lastPageNumber = pageNumber)
          }
        }
      }
      PageWindowLoadKind.PREPEND -> {
        val duplicate = existing[duplicateIndex]
        val firstRequestKey =
            duplicate.firstRequestKey ?: duplicate.requestKey ?: incoming.requestKey
        existing.mapIndexed { index, page ->
          if (index == duplicateIndex) {
            page.copy(previousRequestKey = null, firstRequestKey = firstRequestKey)
          } else {
            page
          }
        }
      }
      PageWindowLoadKind.JUMP -> null
    }
  }

  private fun flattenItems(pages: List<SubmissionPageSnapshot>): List<SubmissionThumbnail> {
    val items = LinkedHashMap<Int, SubmissionThumbnail>()
    pages.forEach { page -> page.items.forEach { item -> items[item.id] = item } }
    return items.values.toList()
  }

  private fun sanitizeViewport(
      viewport: WaterfallViewportState,
      pages: List<SubmissionPageSnapshot>,
      flatItems: List<SubmissionThumbnail>,
      fallbackSid: Int?,
  ): WaterfallViewportState {
    val validIds = flatItems.mapTo(linkedSetOf()) { it.id }
    val pageNumbers = pages.mapNotNull { it.pageNumber }
    val anchorSid = viewport.anchorSid?.takeIf { it in validIds } ?: fallbackSid
    val anchorPage =
        anchorSid?.let { sid -> pages.firstOrNull { page -> page.items.any { it.id == sid } } }
    val scrollRequest = viewport.scrollRequest?.takeIf { it.sid in validIds }
    val pageNumber =
        anchorPage?.pageNumber
            ?: when {
              pageNumbers.isEmpty() -> viewport.currentPageNumber
              else -> viewport.currentPageNumber?.takeIf { it in pageNumbers }
            }
            ?: if (pages.size == 1) pages.first().pageNumber else null
    val keepScrollPosition = scrollRequest != null || viewport.anchorSid == anchorSid
    return viewport.copy(
        firstVisibleItemIndex = if (keepScrollPosition) viewport.firstVisibleItemIndex else 0,
        firstVisibleItemScrollOffset =
            if (keepScrollPosition) viewport.firstVisibleItemScrollOffset else 0,
        anchorSid = anchorSid,
        currentPageNumber = pageNumber,
        scrollRequest = scrollRequest,
    )
  }
}
