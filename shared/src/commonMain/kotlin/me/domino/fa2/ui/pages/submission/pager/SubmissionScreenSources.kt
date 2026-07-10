package me.domino.fa2.ui.pages.submission.pager

import me.domino.fa2.data.fa.feed.FeedRepository
import me.domino.fa2.data.fa.submission.SubmissionRepository
import me.domino.fa2.data.model.FeedPage
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.Submission
import me.domino.fa2.domain.attachmenttext.AttachmentTextDocument
import me.domino.fa2.domain.attachmenttext.AttachmentTextProgress
import me.domino.fa2.domain.attachmenttext.AttachmentTextService
import me.domino.fa2.ui.submissionviewer.api.SubmissionPagerDetailSource
import me.domino.fa2.ui.submissionviewer.api.SubmissionPagerFeedSource
import me.domino.fa2.utils.concurrency.runSuspendCatching

class SubmissionPagerFeedSourceImpl(private val repository: FeedRepository) :
    SubmissionPagerFeedSource {
  override suspend fun loadPageByNextUrl(nextPageUrl: String): ResourceResult<FeedPage> =
      repository.loadPageByNextUrl(nextPageUrl)
}

class SubmissionPagerDetailSourceImpl(
    private val repository: SubmissionRepository,
    private val attachmentTextService: AttachmentTextService,
) : SubmissionPagerDetailSource {
  override suspend fun loadBySid(sid: Int): ResourceResult<Submission> =
      repository.loadSubmissionDetailBySid(sid)

  override suspend fun loadByUrl(url: String): ResourceResult<Submission> =
      repository.loadSubmissionDetailByUrl(url)

  override suspend fun loadAttachmentText(
      downloadUrl: String,
      downloadFileName: String,
      onProgress: (AttachmentTextProgress) -> Unit,
  ): ResourceResult<AttachmentTextDocument> =
      runSuspendCatching {
            attachmentTextService.load(
                downloadUrl = downloadUrl,
                downloadFileName = downloadFileName,
                onProgress = onProgress,
            )
          }
          .getOrElse { ResourceResult.Error(it) }

  override suspend fun toggleFavorite(sid: Int, actionUrl: String): ResourceResult<Unit> =
      repository.toggleFavorite(sid = sid, actionUrl = actionUrl)

  override suspend fun blockTag(
      sid: Int,
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): ResourceResult<Unit> =
      repository.blockTag(sid = sid, tagName = tagName, nonce = nonce, toAdd = toAdd)
}
