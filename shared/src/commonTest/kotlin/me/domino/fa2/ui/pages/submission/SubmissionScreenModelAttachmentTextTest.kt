package me.domino.fa2.ui.pages.submission

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.domino.fa2.data.model.FeedPage
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.domain.attachmenttext.AttachmentTextDocument
import me.domino.fa2.domain.attachmenttext.AttachmentTextFormat
import me.domino.fa2.domain.attachmenttext.AttachmentTextParagraph
import me.domino.fa2.domain.attachmenttext.AttachmentTextProgress
import me.domino.fa2.ui.navigation.SubmissionListHolder
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.parseSubmissionSid

@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionScreenModelAttachmentTextTest {
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
  fun loadsAttachmentTextAndReusesSuccessState() =
      runTest(dispatcher.scheduler) {
        val holder = SubmissionListHolder()
        holder.replace(submissions = listOf(testThumbnail(1)), nextPageUrl = null)
        val detailSource =
            AttachmentRecordingDetailSource(
                attachmentResults =
                    ArrayDeque(listOf(PageState.Success(testAttachmentDocument("hello"))))
            )
        val model =
            SubmissionScreenModel(
                initialSid = 1,
                holder = holder,
                feedSource = NoopFeedSourceForAttachmentTest(),
                submissionSource = detailSource,
                translationService = createTestSubmissionTranslationService(),
            )

        runCurrent()
        model.loadAttachmentTextCurrent()
        runCurrent()

        val successState =
            (model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(1)
                as SubmissionDetailUiState.Success
        assertIs<SubmissionAttachmentTextUiState.Success>(successState.attachmentTextState)
        assertEquals(1, detailSource.attachmentRequests.size)

        model.loadAttachmentTextCurrent()
        runCurrent()
        assertEquals(1, detailSource.attachmentRequests.size)
      }

  @Test
  fun retriesAttachmentTextAfterFailure() =
      runTest(dispatcher.scheduler) {
        val holder = SubmissionListHolder()
        holder.replace(submissions = listOf(testThumbnail(2)), nextPageUrl = null)
        val detailSource =
            AttachmentRecordingDetailSource(
                attachmentResults =
                    ArrayDeque(
                        listOf(
                            PageState.Error(IllegalStateException("boom")),
                            PageState.Success(testAttachmentDocument("recovered")),
                        )
                    )
            )
        val model =
            SubmissionScreenModel(
                initialSid = 2,
                holder = holder,
                feedSource = NoopFeedSourceForAttachmentTest(),
                submissionSource = detailSource,
                translationService = createTestSubmissionTranslationService(),
            )

        runCurrent()
        model.loadAttachmentTextCurrent()
        runCurrent()

        val failedState =
            (model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(2)
                as SubmissionDetailUiState.Success
        assertIs<SubmissionAttachmentTextUiState.Error>(failedState.attachmentTextState)

        model.loadAttachmentTextCurrent()
        runCurrent()

        val recoveredState =
            (model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(2)
                as SubmissionDetailUiState.Success
        assertIs<SubmissionAttachmentTextUiState.Success>(recoveredState.attachmentTextState)
        assertEquals(2, detailSource.attachmentRequests.size)
      }
}

private class AttachmentRecordingDetailSource(
    private val attachmentResults: ArrayDeque<PageState<AttachmentTextDocument>>
) : SubmissionPagerDetailSource {
  val attachmentRequests: MutableList<Pair<String, String>> = mutableListOf()

  override suspend fun loadBySid(sid: Int): PageState<Submission> =
      PageState.Success(testSubmission(sid))

  override suspend fun loadByUrl(url: String): PageState<Submission> {
    val sid =
        parseSubmissionSid(url)
            ?: return PageState.Error(IllegalArgumentException("Invalid submission url: $url"))
    return PageState.Success(
        testSubmission(sid)
            .copy(
                downloadUrl = "https://example.com/sample.txt",
                downloadFileName = "sample.txt",
            )
    )
  }

  override suspend fun loadAttachmentText(
      downloadUrl: String,
      downloadFileName: String,
      onProgress: (AttachmentTextProgress) -> Unit,
  ): PageState<AttachmentTextDocument> {
    attachmentRequests += downloadUrl to downloadFileName
    onProgress(
        AttachmentTextProgress(
            overallFraction = 0.5f,
            stageIndex = 1,
            stageCount = 2,
            stageId = "decode_bytes",
            stageLabel = "解码字节",
            stageFraction = 1f,
            message = "处理中",
            currentItemLabel = downloadFileName,
        )
    )
    return attachmentResults.removeFirst()
  }

  override suspend fun toggleFavorite(sid: Int, actionUrl: String): PageState<Unit> =
      PageState.Success(Unit)

  override suspend fun blockTag(
      sid: Int,
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): PageState<Unit> = PageState.Success(Unit)
}

private class NoopFeedSourceForAttachmentTest : SubmissionPagerFeedSource {
  override suspend fun loadPageByNextUrl(nextPageUrl: String): PageState<FeedPage> =
      PageState.Error(IllegalStateException("No feed prefetch expected in this test"))
}

private fun testAttachmentDocument(text: String): AttachmentTextDocument =
    AttachmentTextDocument(
        format = AttachmentTextFormat.TEXT,
        html = "<p>$text</p>",
        paragraphs = listOf(AttachmentTextParagraph(html = "<p>$text</p>")),
    )

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

private fun testSubmission(sid: Int): Submission =
    Submission(
        id = sid,
        submissionUrl = FaUrls.submission(sid),
        title = "submission-$sid",
        author = "author-$sid",
        authorDisplayName = "Author $sid",
        timestampRaw = null,
        timestampNatural = "now",
        viewCount = 1,
        commentCount = 2,
        favoriteCount = 3,
        isFavorited = false,
        favoriteActionUrl = "",
        rating = "General",
        category = "Category",
        type = "Type",
        species = "Species",
        size = "100x100",
        fileSize = "100 KB",
        keywords = emptyList(),
        previewImageUrl = "https://d.furaffinity.net/art/test/$sid-preview.jpg",
        fullImageUrl = "https://d.furaffinity.net/art/test/$sid-full.jpg",
        downloadUrl = null,
        downloadFileName = null,
        aspectRatio = 1f,
        descriptionHtml = "",
    )
