package me.domino.fa2.ui.pages.submission

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.domain.attachmenttext.AttachmentTextDocument
import me.domino.fa2.domain.attachmenttext.AttachmentTextProgress
import me.domino.fa2.domain.ocr.ImageOcrResult
import me.domino.fa2.domain.ocr.NormalizedImagePoint
import me.domino.fa2.domain.ocr.RecognizedTextBlock
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.parseSubmissionSid

@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionScreenModelImageOcrTest {
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
  fun togglesImageOcrBetweenShowingAndHiddenAndRerunsRecognition() =
      runTest(dispatcher.scheduler) {
        val requests = mutableListOf<String>()
        val model =
            createSubmissionScreenModelForTest(
                initialSid = 1,
                items = listOf(imageOcrTestThumbnail(1)),
                submissionSource = ImageOcrRecordingDetailSource(),
                translationService = createTestSubmissionTranslationService(),
                imageOcrService =
                    createTestSubmissionImageOcrService { imageUrl ->
                      requests += imageUrl
                      ImageOcrResult(
                          blocks =
                              listOf(
                                  RecognizedTextBlock(
                                      text = "hello",
                                      points =
                                          listOf(
                                              NormalizedImagePoint(0.1f, 0.2f),
                                              NormalizedImagePoint(0.4f, 0.2f),
                                              NormalizedImagePoint(0.4f, 0.3f),
                                              NormalizedImagePoint(0.1f, 0.3f),
                                          ),
                                  )
                              )
                      )
                    },
                imageOcrTranslationService = createTestSubmissionImageOcrTranslationService(),
            )

        runCurrent()
        model.openImageZoom("https://example.com/ocr-1.jpg")
        runCurrent()

        val openedState = model.state.value as SubmissionPagerUiState.Data
        assertEquals("https://example.com/ocr-1.jpg", openedState.zoomOverlayImageUrl)
        assertEquals(SubmissionImageOcrUiState.Idle, openedState.zoomImageOcrState)

        model.toggleImageOcrCurrent()
        advanceUntilIdle()

        val showingState = model.state.value as SubmissionPagerUiState.Data
        val shownOcrState =
            assertIs<SubmissionImageOcrUiState.Showing>(showingState.zoomImageOcrState)
        assertEquals(1, shownOcrState.blocks.size)
        assertEquals("hello", shownOcrState.blocks.single().displayText)
        assertEquals("hello", shownOcrState.blocks.single().translatedText)
        assertEquals(
            SubmissionImageOcrTranslationStatus.SUCCESS,
            shownOcrState.blocks.single().translationStatus,
        )
        assertEquals(listOf("https://example.com/ocr-1.jpg"), requests)

        model.toggleImageOcrCurrent()
        runCurrent()

        val hiddenState = model.state.value as SubmissionPagerUiState.Data
        assertEquals(SubmissionImageOcrUiState.Idle, hiddenState.zoomImageOcrState)

        model.toggleImageOcrCurrent()
        advanceUntilIdle()

        val rerunState = model.state.value as SubmissionPagerUiState.Data
        assertIs<SubmissionImageOcrUiState.Showing>(rerunState.zoomImageOcrState)
        assertEquals(
            listOf("https://example.com/ocr-1.jpg", "https://example.com/ocr-1.jpg"),
            requests,
        )
      }

  @Test
  fun ignoresInFlightOcrResultAfterOverlayIsDismissed() =
      runTest(dispatcher.scheduler) {
        val completion = CompletableDeferred<ImageOcrResult>()
        val model =
            createSubmissionScreenModelForTest(
                initialSid = 1,
                items = listOf(imageOcrTestThumbnail(1)),
                submissionSource = ImageOcrRecordingDetailSource(),
                translationService = createTestSubmissionTranslationService(),
                imageOcrService = createTestSubmissionImageOcrService { _ -> completion.await() },
                imageOcrTranslationService = createTestSubmissionImageOcrTranslationService(),
            )

        runCurrent()
        model.openImageZoom("https://example.com/ocr-2.jpg")
        runCurrent()
        model.toggleImageOcrCurrent()
        runCurrent()

        val loadingState = model.state.value as SubmissionPagerUiState.Data
        assertEquals(SubmissionImageOcrUiState.Loading, loadingState.zoomImageOcrState)

        model.dismissImageZoom()
        runCurrent()

        val dismissedState = model.state.value as SubmissionPagerUiState.Data
        assertNull(dismissedState.zoomOverlayImageUrl)
        assertEquals(SubmissionImageOcrUiState.Idle, dismissedState.zoomImageOcrState)

        completion.complete(
            ImageOcrResult(
                blocks =
                    listOf(
                        RecognizedTextBlock(
                            text = "late",
                            points =
                                listOf(
                                    NormalizedImagePoint(0f, 0f),
                                    NormalizedImagePoint(1f, 0f),
                                    NormalizedImagePoint(1f, 1f),
                                    NormalizedImagePoint(0f, 1f),
                                ),
                        )
                    )
            )
        )
        advanceUntilIdle()

        val finalState = model.state.value as SubmissionPagerUiState.Data
        assertNull(finalState.zoomOverlayImageUrl)
        assertEquals(SubmissionImageOcrUiState.Idle, finalState.zoomImageOcrState)
      }

  @Test
  fun refreshesSingleEditedBlockTranslationAndKeepsDialogOpen() =
      runTest(dispatcher.scheduler) {
        val model =
            createSubmissionScreenModelForTest(
                initialSid = 1,
                items = listOf(imageOcrTestThumbnail(1)),
                submissionSource = ImageOcrRecordingDetailSource(),
                translationService = createTestSubmissionTranslationService(),
                imageOcrService =
                    createTestSubmissionImageOcrService { _ ->
                      ImageOcrResult(
                          blocks =
                              listOf(
                                  RecognizedTextBlock(
                                      text = "hello",
                                      points =
                                          listOf(
                                              NormalizedImagePoint(0.1f, 0.2f),
                                              NormalizedImagePoint(0.4f, 0.2f),
                                              NormalizedImagePoint(0.4f, 0.3f),
                                              NormalizedImagePoint(0.1f, 0.3f),
                                          ),
                                  )
                              )
                      )
                    },
                imageOcrTranslationService =
                    createTestSubmissionImageOcrTranslationService { request ->
                      request.sourceText.uppercase()
                    },
            )

        runCurrent()
        model.openImageZoom("https://example.com/ocr-3.jpg")
        runCurrent()
        model.toggleImageOcrCurrent()
        advanceUntilIdle()

        model.openImageOcrDialog("ocr-block-0")
        runCurrent()
        model.updateImageOcrDialogDraft("changed text")
        runCurrent()
        model.refreshImageOcrDialogTranslation()
        advanceUntilIdle()

        val showingState =
            assertIs<SubmissionImageOcrUiState.Showing>(
                (model.state.value as SubmissionPagerUiState.Data).zoomImageOcrState
            )
        val refreshedBlock = showingState.blocks.single()
        assertEquals("changed text", refreshedBlock.originalText)
        assertEquals("CHANGED TEXT", refreshedBlock.translatedText)
        assertEquals("CHANGED TEXT", refreshedBlock.displayText)
        assertEquals(
            SubmissionImageOcrTranslationStatus.SUCCESS,
            refreshedBlock.translationStatus,
        )
        assertTrue(showingState.dialog != null)
        assertEquals("changed text", showingState.dialog?.draftOriginalText)
        assertEquals("CHANGED TEXT", showingState.dialog?.translatedText)
      }
}

private class ImageOcrRecordingDetailSource : SubmissionPagerDetailSource {
  override suspend fun loadBySid(sid: Int): PageState<Submission> =
      PageState.Success(imageOcrTestSubmission(sid))

  override suspend fun loadByUrl(url: String): PageState<Submission> {
    val sid =
        parseSubmissionSid(url)
            ?: return PageState.Error(IllegalArgumentException("Invalid submission url: $url"))
    return PageState.Success(imageOcrTestSubmission(sid))
  }

  override suspend fun loadAttachmentText(
      downloadUrl: String,
      downloadFileName: String,
      onProgress: (AttachmentTextProgress) -> Unit,
  ): PageState<AttachmentTextDocument> =
      PageState.Error(IllegalStateException("No attachment parsing expected in OCR test"))

  override suspend fun toggleFavorite(sid: Int, actionUrl: String): PageState<Unit> =
      PageState.Success(Unit)

  override suspend fun blockTag(
      sid: Int,
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): PageState<Unit> = PageState.Success(Unit)
}

private fun imageOcrTestThumbnail(sid: Int): SubmissionThumbnail =
    SubmissionThumbnail(
        id = sid,
        submissionUrl = FaUrls.submission(sid),
        title = "title-$sid",
        author = "author-$sid",
        authorAvatarUrl = "",
        thumbnailUrl = "https://t.furaffinity.net/$sid@400-0.jpg",
        thumbnailAspectRatio = 1f,
        categoryTag = "c_all",
    )

private fun imageOcrTestSubmission(sid: Int): Submission =
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
