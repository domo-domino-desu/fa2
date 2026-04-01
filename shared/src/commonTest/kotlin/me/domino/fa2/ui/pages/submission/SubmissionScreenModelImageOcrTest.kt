package me.domino.fa2.ui.pages.submission

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.settings.TranslationProvider
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
        assertEquals(SubmissionImageOcrTranslationMode.IDLE, shownOcrState.translationMode)
        assertEquals("hello", shownOcrState.blocks.single().displayText)
        assertEquals(null, shownOcrState.blocks.single().translatedText)
        assertEquals(
            SubmissionImageOcrTranslationStatus.IDLE,
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
  fun premergesRecognizedDialogueFragmentsBeforeShowingOverlay() =
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
                                  ocrBlock("hello", 0.10f, 0.10f, 0.20f, 0.15f),
                                  ocrBlock("there", 0.11f, 0.18f, 0.22f, 0.205f),
                                  ocrBlock("friend", 0.12f, 0.23f, 0.25f, 0.28f),
                              )
                      )
                    },
                imageOcrTranslationService = createTestSubmissionImageOcrTranslationService(),
            )

        runCurrent()
        model.openImageZoom("https://example.com/ocr-premerge.jpg")
        runCurrent()
        model.toggleImageOcrCurrent()
        advanceUntilIdle()

        val showingState =
            assertIs<SubmissionImageOcrUiState.Showing>(
                (model.state.value as SubmissionPagerUiState.Data).zoomImageOcrState
            )
        assertEquals(1, showingState.blocks.size)
        assertEquals("hello there friend", showingState.blocks.single().originalText)
      }

  @Test
  fun imageOcrDialogRemainsUnavailableUntilTranslationIsApplied() =
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
                      "translated:${request.sourceText}"
                    },
            )

        runCurrent()
        model.openImageZoom("https://example.com/ocr-dialog.jpg")
        runCurrent()
        model.toggleImageOcrCurrent()
        advanceUntilIdle()

        model.openImageOcrDialog("ocr-block-0")
        runCurrent()

        val rawShowingState =
            assertIs<SubmissionImageOcrUiState.Showing>(
                (model.state.value as SubmissionPagerUiState.Data).zoomImageOcrState
            )
        assertNull(rawShowingState.dialog)

        model.translateImageOcrCurrent()
        advanceUntilIdle()
        model.openImageOcrDialog("ocr-block-0")
        runCurrent()

        val translatedShowingState =
            assertIs<SubmissionImageOcrUiState.Showing>(
                (model.state.value as SubmissionPagerUiState.Data).zoomImageOcrState
            )
        assertEquals(
            SubmissionImageOcrTranslationMode.APPLIED,
            translatedShowingState.translationMode,
        )
        assertEquals("translated:hello", translatedShowingState.blocks.single().translatedText)
        assertTrue(translatedShowingState.dialog != null)
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
        model.translateImageOcrCurrent()
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
        val dialogState = showingState.dialog
        assertTrue(dialogState != null)
        assertEquals("changed text", dialogState.draftOriginalText)
        assertEquals("CHANGED TEXT", dialogState.translatedText)
      }

  @Test
  fun retriesImageOcrTranslationWithNextProviderFromFeedbackAction() =
      runTest(dispatcher.scheduler) {
        val settingsService = createTestAppSettingsService()
        val requestedProviders = mutableListOf<TranslationProvider>()
        val feedbackEvents = mutableListOf<me.domino.fa2.ui.components.AppFeedbackRequest>()
        val model =
            createSubmissionScreenModelForTest(
                initialSid = 1,
                items = listOf(imageOcrTestThumbnail(1)),
                submissionSource = ImageOcrRecordingDetailSource(),
                translationService =
                    createTestSubmissionTranslationService(settingsService = settingsService),
                imageOcrService =
                    createTestSubmissionImageOcrService { _ ->
                      ImageOcrResult(blocks = listOf(ocrBlock("hello", 0.10f, 0.10f, 0.20f, 0.18f)))
                    },
                imageOcrTranslationService =
                    createTestSubmissionImageOcrTranslationService(
                        settingsService = settingsService
                    ) { request ->
                      requestedProviders += request.provider
                      when (request.provider) {
                        TranslationProvider.GOOGLE -> error("boom")
                        TranslationProvider.MICROSOFT -> request.sourceText.uppercase()
                        TranslationProvider.OPENAI_COMPATIBLE -> error("unexpected provider")
                      }
                    },
                settingsService = settingsService,
            )

        val feedbackJob = launch {
          model.feedbackEvents.collect { request -> feedbackEvents += request }
        }

        runCurrent()
        model.openImageZoom("https://example.com/ocr-retry.jpg")
        runCurrent()
        model.toggleImageOcrCurrent()
        advanceUntilIdle()
        model.translateImageOcrCurrent()
        advanceUntilIdle()

        val feedback = assertNotNull(feedbackEvents.firstOrNull())
        assertEquals(TranslationProvider.GOOGLE, requestedProviders.single())

        feedback.onAction?.invoke()
        advanceUntilIdle()

        val showingState =
            assertIs<SubmissionImageOcrUiState.Showing>(
                (model.state.value as SubmissionPagerUiState.Data).zoomImageOcrState
            )
        assertEquals(
            listOf(TranslationProvider.GOOGLE, TranslationProvider.MICROSOFT),
            requestedProviders,
        )
        assertEquals(
            TranslationProvider.MICROSOFT,
            settingsService.settings.value.translationProvider,
        )
        assertEquals(SubmissionImageOcrTranslationMode.APPLIED, showingState.translationMode)
        assertEquals("HELLO", showingState.blocks.single().translatedText)

        feedbackJob.cancel()
      }

  @Test
  fun mergesAllBlocksInsideSelectedRegionAndRetranslatesMergedBlock() =
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
                                  ocrBlock("hello", 0.10f, 0.10f, 0.20f, 0.18f),
                                  ocrBlock("there", 0.40f, 0.10f, 0.50f, 0.18f),
                                  ocrBlock("friend", 0.11f, 0.42f, 0.29f, 0.50f),
                              )
                      )
                    },
                imageOcrTranslationService =
                    createTestSubmissionImageOcrTranslationService { request ->
                      request.sourceText.uppercase()
                    },
            )

        runCurrent()
        model.openImageZoom("https://example.com/ocr-merge.jpg")
        runCurrent()
        model.toggleImageOcrCurrent()
        advanceUntilIdle()
        model.translateImageOcrCurrent()
        advanceUntilIdle()

        model.mergeImageOcrBlocks(
            draggedBlockId = "ocr-block-0",
            mergeRegionPoints =
                listOf(
                    NormalizedImagePoint(0.09f, 0.09f),
                    NormalizedImagePoint(0.51f, 0.09f),
                    NormalizedImagePoint(0.51f, 0.51f),
                    NormalizedImagePoint(0.09f, 0.51f),
                ),
        )
        advanceUntilIdle()

        val showingState =
            assertIs<SubmissionImageOcrUiState.Showing>(
                (model.state.value as SubmissionPagerUiState.Data).zoomImageOcrState
            )
        assertEquals(SubmissionImageOcrTranslationMode.APPLIED, showingState.translationMode)
        assertEquals(1, showingState.blocks.size)
        val mergedBlock = showingState.blocks.single()
        assertTrue(mergedBlock.id.startsWith("ocr-merged-"))
        assertEquals("hello there friend", mergedBlock.originalText)
        assertEquals("HELLO THERE FRIEND", mergedBlock.translatedText)
        assertEquals(SubmissionImageOcrTranslationStatus.SUCCESS, mergedBlock.translationStatus)

        model.openImageOcrDialog(mergedBlock.id)
        runCurrent()

        val dialogShowingState =
            assertIs<SubmissionImageOcrUiState.Showing>(
                (model.state.value as SubmissionPagerUiState.Data).zoomImageOcrState
            )
        assertEquals(mergedBlock.id, dialogShowingState.dialog?.blockId)
      }

  @Test
  fun dismissingZoomOverlayClearsSessionMergedBlocks() =
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
                                  ocrBlock("hello", 0.10f, 0.10f, 0.20f, 0.18f),
                                  ocrBlock("there", 0.40f, 0.10f, 0.50f, 0.18f),
                              )
                      )
                    },
                imageOcrTranslationService = createTestSubmissionImageOcrTranslationService(),
            )

        runCurrent()
        model.openImageZoom("https://example.com/ocr-reset.jpg")
        runCurrent()
        model.toggleImageOcrCurrent()
        advanceUntilIdle()
        model.mergeImageOcrBlocks(
            draggedBlockId = "ocr-block-0",
            mergeRegionPoints =
                listOf(
                    NormalizedImagePoint(0.09f, 0.09f),
                    NormalizedImagePoint(0.51f, 0.09f),
                    NormalizedImagePoint(0.51f, 0.20f),
                    NormalizedImagePoint(0.09f, 0.20f),
                ),
        )
        advanceUntilIdle()

        val mergedState =
            assertIs<SubmissionImageOcrUiState.Showing>(
                (model.state.value as SubmissionPagerUiState.Data).zoomImageOcrState
            )
        assertEquals(1, mergedState.blocks.size)
        assertTrue(mergedState.blocks.single().id.startsWith("ocr-merged-"))

        model.dismissImageZoom()
        runCurrent()
        model.openImageZoom("https://example.com/ocr-reset.jpg")
        runCurrent()
        model.toggleImageOcrCurrent()
        advanceUntilIdle()

        val reopenedState =
            assertIs<SubmissionImageOcrUiState.Showing>(
                (model.state.value as SubmissionPagerUiState.Data).zoomImageOcrState
            )
        assertEquals(listOf("ocr-block-0", "ocr-block-1"), reopenedState.blocks.map { it.id })
      }

  @Test
  fun preservesSuccessfulImageOcrTranslationSnapshotAfterOverlayDismissed() =
      runTest(dispatcher.scheduler) {
        val settingsService = createTestAppSettingsService()
        val model =
            createSubmissionScreenModelForTest(
                initialSid = 1,
                items = listOf(imageOcrTestThumbnail(1)),
                submissionSource = ImageOcrRecordingDetailSource(),
                translationService =
                    createTestSubmissionTranslationService(settingsService = settingsService),
                imageOcrService =
                    createTestSubmissionImageOcrService { _ ->
                      ImageOcrResult(blocks = listOf(ocrBlock("hello", 0.10f, 0.10f, 0.20f, 0.18f)))
                    },
                imageOcrTranslationService =
                    createTestSubmissionImageOcrTranslationService(
                        settingsService = settingsService
                    ) { request ->
                      request.sourceText.uppercase()
                    },
                settingsService = settingsService,
            )

        runCurrent()
        model.openImageZoom("https://example.com/ocr-snapshot.jpg")
        runCurrent()
        model.toggleImageOcrCurrent()
        advanceUntilIdle()
        model.translateImageOcrCurrent()
        advanceUntilIdle()

        model.dismissImageZoom()
        runCurrent()

        val detailState =
            ((model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(1)
                as SubmissionDetailUiState.Success)
        val snapshot = assertNotNull(detailState.imageOcrTranslationExportSnapshot)
        assertEquals("https://example.com/ocr-snapshot.jpg", snapshot.imageUrl)
        assertEquals(TranslationProvider.GOOGLE, snapshot.provider)
        assertEquals("HELLO", snapshot.blocks.single().translatedText)
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

private fun ocrBlock(
    text: String,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
): RecognizedTextBlock =
    RecognizedTextBlock(
        text = text,
        points =
            listOf(
                NormalizedImagePoint(left, top),
                NormalizedImagePoint(right, top),
                NormalizedImagePoint(right, bottom),
                NormalizedImagePoint(left, bottom),
            ),
    )
