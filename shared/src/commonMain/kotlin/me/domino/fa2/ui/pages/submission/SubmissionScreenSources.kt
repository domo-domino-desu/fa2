package me.domino.fa2.ui.pages.submission

import me.domino.fa2.data.fa.feed.FeedRepository
import me.domino.fa2.data.fa.submission.SubmissionRepository
import me.domino.fa2.data.model.FeedPage
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.Submission
import me.domino.fa2.domain.attachmenttext.AttachmentTextDocument
import me.domino.fa2.domain.attachmenttext.AttachmentTextProgress
import me.domino.fa2.domain.attachmenttext.AttachmentTextService

interface SubmissionPagerFeedSource {
  suspend fun loadPageByNextUrl(nextPageUrl: String): PageState<FeedPage>
}

interface SubmissionPagerDetailSource {
  suspend fun loadBySid(sid: Int): PageState<Submission>

  suspend fun loadByUrl(url: String): PageState<Submission>

  suspend fun loadAttachmentText(
      downloadUrl: String,
      downloadFileName: String,
      onProgress: (AttachmentTextProgress) -> Unit = {},
  ): PageState<AttachmentTextDocument>

  suspend fun toggleFavorite(sid: Int, actionUrl: String): PageState<Unit>

  suspend fun blockTag(sid: Int, tagName: String, nonce: String, toAdd: Boolean): PageState<Unit>
}

class SubmissionPagerFeedSourceImpl(private val repository: FeedRepository) :
    SubmissionPagerFeedSource {
  override suspend fun loadPageByNextUrl(nextPageUrl: String): PageState<FeedPage> =
      repository.loadPageByNextUrl(nextPageUrl)
}

class SubmissionPagerDetailSourceImpl(
    private val repository: SubmissionRepository,
    private val attachmentTextService: AttachmentTextService,
) : SubmissionPagerDetailSource {
  override suspend fun loadBySid(sid: Int): PageState<Submission> =
      repository.loadSubmissionDetailBySid(sid)

  override suspend fun loadByUrl(url: String): PageState<Submission> =
      repository.loadSubmissionDetailByUrl(url)

  override suspend fun loadAttachmentText(
      downloadUrl: String,
      downloadFileName: String,
      onProgress: (AttachmentTextProgress) -> Unit,
  ): PageState<AttachmentTextDocument> =
      runCatching {
            attachmentTextService.load(
                downloadUrl = downloadUrl,
                downloadFileName = downloadFileName,
                onProgress = onProgress,
            )
          }
          .getOrElse { PageState.Error(it) }

  override suspend fun toggleFavorite(sid: Int, actionUrl: String): PageState<Unit> =
      repository.toggleFavorite(sid = sid, actionUrl = actionUrl)

  override suspend fun blockTag(
      sid: Int,
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): PageState<Unit> =
      repository.blockTag(sid = sid, tagName = tagName, nonce = nonce, toAdd = toAdd)
}
