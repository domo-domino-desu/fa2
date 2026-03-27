package me.domino.fa2.ui.pages.submission

import me.domino.fa2.ui.components.submission.SubmissionWaterfallPageControls

internal fun SubmissionContextSnapshot.pageNumberForSid(sid: Int?): Int? {
  if (sid == null) return null
  return pages.firstNotNullOfOrNull { page ->
    page.pageNumber?.takeIf { number -> page.items.any { item -> item.id == sid } }
  }
}

internal fun SubmissionContextSnapshot.currentWaterfallPageNumber(): Int? =
    waterfallViewport.anchorSid?.let { sid ->
      pages.firstOrNull { page -> page.items.any { item -> item.id == sid } }?.pageNumber
    }
        ?: waterfallViewport.currentPageNumber
        ?: if (pages.size == 1) pages.firstOrNull()?.pageNumber else null

internal fun SubmissionContextSnapshot.knownLastPageNumber(): Int? =
    pages.firstNotNullOfOrNull { page -> page.lastPageNumber }

internal fun SubmissionContextSnapshot.currentWaterfallPageTarget(): SubmissionPageTarget? {
  val anchorSid = waterfallViewport.anchorSid
  if (anchorSid != null) {
    pages.forEachIndexed { index, page ->
      if (page.items.any { item -> item.id == anchorSid }) {
        return SubmissionPageTarget(page = page, index = index)
      }
    }
  }

  val currentPageNumber = currentWaterfallPageNumber()
  if (currentPageNumber != null) {
    pages.forEachIndexed { index, page ->
      if (page.pageNumber == currentPageNumber) {
        return SubmissionPageTarget(page = page, index = index)
      }
    }
  }

  return when {
    pages.isEmpty() -> null
    else -> SubmissionPageTarget(page = pages.first(), index = 0)
  }
}

internal fun SubmissionContextSnapshot.cachedPreviousPageTarget(
    currentTarget: SubmissionPageTarget,
): SubmissionPageTarget? {
  val candidate = pages.getOrNull(currentTarget.index - 1) ?: return null
  return SubmissionPageTarget(page = candidate, index = currentTarget.index - 1).takeIf { target ->
    target.page.isDirectPreviousOf(currentTarget.page)
  }
}

internal fun SubmissionContextSnapshot.cachedNextPageTarget(
    currentTarget: SubmissionPageTarget,
): SubmissionPageTarget? {
  val candidate = pages.getOrNull(currentTarget.index + 1) ?: return null
  return SubmissionPageTarget(page = candidate, index = currentTarget.index + 1).takeIf { target ->
    target.page.isDirectNextOf(currentTarget.page)
  }
}

internal fun SubmissionContextSnapshot.toWaterfallPageControls(): SubmissionWaterfallPageControls? {
  val currentPageNumber = currentWaterfallPageNumber()
  val currentTarget = currentWaterfallPageTarget()
  val cachedPreviousTarget = currentTarget?.let { target -> cachedPreviousPageTarget(target) }
  val cachedNextTarget = currentTarget?.let { target -> cachedNextPageTarget(target) }
  val knownLastPageNumber = knownLastPageNumber()
  val currentIsFirstPage =
      currentTarget != null &&
          currentTarget.index == 0 &&
          (currentTarget.page.pageNumber == 1 || currentTarget.page.previousRequestKey == null)
  val currentIsLastPage =
      currentTarget != null &&
          when {
            knownLastPageNumber != null && currentTarget.page.pageNumber != null ->
                currentTarget.page.pageNumber == knownLastPageNumber
            currentTarget.page.requestKey != null && currentTarget.page.lastRequestKey != null ->
                currentTarget.page.requestKey == currentTarget.page.lastRequestKey
            else -> false
          }
  val controls =
      SubmissionWaterfallPageControls(
          paginationKind = paginationModel.kind,
          currentPageNumber = currentPageNumber,
          lastPageNumber = knownLastPageNumber,
          showFirstPage = paginationModel.hasFirstPage,
          canLoadFirstPage =
              paginationModel.hasFirstPage && currentTarget != null && !currentIsFirstPage,
          showPreviousPage = paginationModel.supportsAdjacentPaging,
          canLoadPreviousPage =
              paginationModel.supportsAdjacentPaging &&
                  currentTarget != null &&
                  (cachedPreviousTarget != null || currentTarget.page.previousRequestKey != null),
          showJumpToPage = paginationModel.supportsJumpToPage,
          canJumpToPage = paginationModel.supportsJumpToPage,
          showNextPage = paginationModel.supportsAdjacentPaging,
          canLoadNextPage =
              paginationModel.supportsAdjacentPaging &&
                  currentTarget != null &&
                  (cachedNextTarget != null || currentTarget.page.nextRequestKey != null),
          showLastPage = paginationModel.knowsLastPage,
          canLoadLastPage =
              paginationModel.knowsLastPage && currentTarget != null && !currentIsLastPage,
          loading = loading.prependLoading || loading.appendLoading || loading.jumpLoading,
      )
  return controls.takeIf { it.hasAnyAction }
}
