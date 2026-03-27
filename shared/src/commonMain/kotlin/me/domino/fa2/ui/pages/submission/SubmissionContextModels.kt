package me.domino.fa2.ui.pages.submission

import me.domino.fa2.data.model.SubmissionThumbnail

enum class SubmissionContextSourceKind {
  FEED,
  BROWSE,
  SEARCH,
  GALLERY,
  FAVORITES,
  SEQUENCE,
  DETACHED,
  HISTORY,
}

enum class SubmissionPaginationKind {
  CURSOR,
  NUMBERED,
}

data class SubmissionPaginationModel(
    val kind: SubmissionPaginationKind? = null,
    val hasFirstPage: Boolean = false,
    val knowsLastPage: Boolean = false,
    val canJumpToCachedSubmission: Boolean = true,
) {
  val supportsAdjacentPaging: Boolean
    get() = kind != null

  val supportsJumpToPage: Boolean
    get() = kind == SubmissionPaginationKind.NUMBERED
}

data class SubmissionPageSnapshot(
    val pageId: String,
    val requestKey: String? = null,
    val items: List<SubmissionThumbnail>,
    val pageNumber: Int? = null,
    val previousRequestKey: String? = null,
    val nextRequestKey: String? = null,
    val firstRequestKey: String? = null,
    val lastRequestKey: String? = null,
    val lastPageNumber: Int? = null,
    val totalCount: Int? = null,
)

data class ContextLoadingState(
    val initialLoading: Boolean = false,
    val prependLoading: Boolean = false,
    val appendLoading: Boolean = false,
    val jumpLoading: Boolean = false,
    val prependErrorMessage: String? = null,
    val appendErrorMessage: String? = null,
    val jumpErrorMessage: String? = null,
)

data class WaterfallViewportState(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
    val anchorSid: Int? = null,
    val currentPageNumber: Int? = null,
    val scrollRequest: WaterfallScrollRequest? = null,
)

data class WaterfallScrollRequest(
    val sid: Int,
    val version: Long,
    val animated: Boolean = false,
    val targetPageLeadingSids: List<Int> = emptyList(),
)

internal data class SubmissionPageTarget(
    val page: SubmissionPageSnapshot,
    val index: Int,
)

internal fun SubmissionPageSnapshot.matches(other: SubmissionPageSnapshot): Boolean =
    pageId == other.pageId || (requestKey != null && requestKey == other.requestKey)

internal fun SubmissionPageSnapshot.isDirectPreviousOf(current: SubmissionPageSnapshot): Boolean {
  if (pageNumber != null && current.pageNumber != null) {
    return pageNumber + 1 == current.pageNumber
  }
  return (requestKey != null && requestKey == current.previousRequestKey) ||
      (nextRequestKey != null && nextRequestKey == current.requestKey)
}

internal fun SubmissionPageSnapshot.isDirectNextOf(current: SubmissionPageSnapshot): Boolean {
  if (pageNumber != null && current.pageNumber != null) {
    return current.pageNumber + 1 == pageNumber
  }
  return (requestKey != null && requestKey == current.nextRequestKey) ||
      (previousRequestKey != null && previousRequestKey == current.requestKey)
}

data class SubmissionContextSnapshot(
    val contextId: String,
    val sourceKind: SubmissionContextSourceKind,
    val paginationModel: SubmissionPaginationModel,
    val pages: List<SubmissionPageSnapshot> = emptyList(),
    val flatItems: List<SubmissionThumbnail> = emptyList(),
    val selectedSid: Int? = null,
    val selectedFlatIndex: Int = 0,
    val loading: ContextLoadingState = ContextLoadingState(),
    val waterfallViewport: WaterfallViewportState = WaterfallViewportState(),
    val revisionKey: String? = null,
) {
  val hasNextPage: Boolean
    get() = pages.lastOrNull()?.nextRequestKey != null

  val hasPreviousPage: Boolean
    get() = pages.firstOrNull()?.previousRequestKey != null
}

internal data class SubmissionLoadedPage(
    val pageId: String,
    val requestKey: String? = null,
    val items: List<SubmissionThumbnail>,
    val pageNumber: Int? = null,
    val previousRequestKey: String? = null,
    val nextRequestKey: String? = null,
    val firstRequestKey: String? = null,
    val lastRequestKey: String? = null,
    val lastPageNumber: Int? = null,
    val totalCount: Int? = null,
) {
  fun toSnapshot(): SubmissionPageSnapshot =
      SubmissionPageSnapshot(
          pageId = pageId,
          requestKey = requestKey,
          items = items,
          pageNumber = pageNumber,
          previousRequestKey = previousRequestKey?.takeUnless { key -> key == requestKey },
          nextRequestKey = nextRequestKey?.takeUnless { key -> key == requestKey },
          firstRequestKey = firstRequestKey,
          lastRequestKey = lastRequestKey?.takeUnless { key -> key == requestKey },
          lastPageNumber = lastPageNumber,
          totalCount = totalCount,
      )
}
