package me.domino.fa2.domain.submissionviewer.api

import me.domino.fa2.data.model.ResourceResult

internal abstract class SubmissionSourceAdapter {
  abstract val sourceKind: SubmissionContextSourceKind
  abstract val paginationModel: SubmissionPaginationModel

  abstract suspend fun loadInitialPage(): ResourceResult<SubmissionPageSnapshot>

  open suspend fun loadNextPage(requestKey: String): ResourceResult<SubmissionPageSnapshot> =
      unsupported("Next")

  open suspend fun loadPreviousPage(requestKey: String): ResourceResult<SubmissionPageSnapshot> =
      unsupported("Previous")

  open suspend fun loadFirstPage(): ResourceResult<SubmissionPageSnapshot> = unsupported("First")

  open suspend fun loadLastPage(): ResourceResult<SubmissionPageSnapshot> = unsupported("Last")

  open suspend fun jumpToPage(pageNumber: Int): ResourceResult<SubmissionPageSnapshot> =
      unsupported("Jump")

  private fun unsupported(operation: String): ResourceResult<SubmissionPageSnapshot> =
      ResourceResult.Error(
          UnsupportedOperationException("$operation page is unsupported for $sourceKind")
      )
}
