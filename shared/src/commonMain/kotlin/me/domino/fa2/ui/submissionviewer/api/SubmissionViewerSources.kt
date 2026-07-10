package me.domino.fa2.ui.submissionviewer.api

import me.domino.fa2.data.model.FeedPage
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.Submission
import me.domino.fa2.domain.attachmenttext.AttachmentTextDocument
import me.domino.fa2.domain.attachmenttext.AttachmentTextProgress

interface SubmissionPagerFeedSource {
  suspend fun loadPageByNextUrl(nextPageUrl: String): ResourceResult<FeedPage>
}

interface SubmissionPagerDetailSource {
  suspend fun loadBySid(sid: Int): ResourceResult<Submission>

  suspend fun loadByUrl(url: String): ResourceResult<Submission>

  suspend fun loadAttachmentText(
      downloadUrl: String,
      downloadFileName: String,
      onProgress: (AttachmentTextProgress) -> Unit = {},
  ): ResourceResult<AttachmentTextDocument>

  suspend fun toggleFavorite(sid: Int, actionUrl: String): ResourceResult<Unit>

  suspend fun blockTag(
      sid: Int,
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): ResourceResult<Unit>
}

internal interface SubmissionSourceAdapter {
  val sourceKind: SubmissionContextSourceKind
  val paginationModel: SubmissionPaginationModel

  suspend fun loadInitialPage(): ResourceResult<SubmissionLoadedPage>

  suspend fun loadNextPage(requestKey: String): ResourceResult<SubmissionLoadedPage> =
      unsupported("Next")

  suspend fun loadPreviousPage(requestKey: String): ResourceResult<SubmissionLoadedPage> =
      unsupported("Previous")

  suspend fun loadFirstPage(): ResourceResult<SubmissionLoadedPage> = unsupported("First")

  suspend fun loadLastPage(): ResourceResult<SubmissionLoadedPage> = unsupported("Last")

  suspend fun jumpToPage(pageNumber: Int): ResourceResult<SubmissionLoadedPage> =
      unsupported("Jump")

  private fun unsupported(operation: String): ResourceResult<SubmissionLoadedPage> =
      ResourceResult.Error(
          UnsupportedOperationException("$operation page is unsupported for $sourceKind")
      )
}
