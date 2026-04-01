package me.domino.fa2.ui.pages.submission

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
import me.domino.fa2.domain.attachmenttext.AttachmentTextFormat
import me.domino.fa2.domain.attachmenttext.AttachmentTextParagraph
import me.domino.fa2.domain.attachmenttext.AttachmentTextProgress
import me.domino.fa2.ui.state.SubmissionDescriptionTranslationStatus
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.parseSubmissionSid

@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionScreenModelTranslationAndScrollTest {
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
  fun cachesDescriptionTranslationAcrossPageChangesAndResetsWhenDescriptionChanges() =
      runTest(dispatcher.scheduler) {
        val items = listOf(translationTestThumbnail(1), translationTestThumbnail(2))
        val detailSource =
            MutableSubmissionDetailSource(
                submissions =
                    mutableMapOf(
                        1 to translationTestSubmission(1, descriptionHtml = "<p>hello</p>"),
                        2 to translationTestSubmission(2, descriptionHtml = "<p>world</p>"),
                    )
            )
        val model =
            createSubmissionScreenModelForTest(
                initialSid = 1,
                items = items,
                submissionSource = detailSource,
                translationService =
                    createTestSubmissionTranslationService { request ->
                      request.sourceText.uppercase()
                    },
            )

        runCurrent()
        model.translateDescriptionCurrent()
        advanceUntilIdle()

        val translatedState =
            ((model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(1)
                    as SubmissionDetailUiState.Success)
                .descriptionTranslationState
        assertEquals(true, translatedState.hasTriggered)
        assertEquals(
            SubmissionDescriptionTranslationStatus.SUCCESS,
            translatedState.blocks.single().status,
        )
        assertEquals("HELLO", translatedState.blocks.single().translated)

        model.onPageChanged(1)
        runCurrent()
        model.onPageChanged(0)
        runCurrent()

        val restoredState =
            ((model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(1)
                    as SubmissionDetailUiState.Success)
                .descriptionTranslationState
        assertEquals(
            SubmissionDescriptionTranslationStatus.SUCCESS,
            restoredState.blocks.single().status,
        )
        assertEquals("HELLO", restoredState.blocks.single().translated)

        detailSource.submissions[1] =
            translationTestSubmission(1, descriptionHtml = "<p>changed</p>")
        model.retryCurrentDetail()
        advanceUntilIdle()

        val resetState =
            ((model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(1)
                    as SubmissionDetailUiState.Success)
                .descriptionTranslationState
        assertEquals(false, resetState.hasTriggered)
        assertEquals("<p>changed</p>", resetState.sourceHtml)
        assertEquals(
            SubmissionDescriptionTranslationStatus.IDLE,
            resetState.blocks.single().status,
        )
        assertEquals(null, resetState.blocks.single().translated)
      }

  @Test
  fun resetsAttachmentTranslationWhenAttachmentSourceChanges() =
      runTest(dispatcher.scheduler) {
        val detailSource =
            MutableSubmissionDetailSource(
                submissions =
                    mutableMapOf(
                        1 to
                            translationTestSubmission(
                                1,
                                descriptionHtml = "<p>desc</p>",
                                downloadUrl = "https://example.com/sample.txt",
                                downloadFileName = "sample.txt",
                            )
                    ),
                attachmentResults =
                    ArrayDeque(
                        listOf(PageState.Success(translationTestAttachmentDocument("hello")))
                    ),
            )
        val model =
            createSubmissionScreenModelForTest(
                initialSid = 1,
                items = listOf(translationTestThumbnail(1)),
                submissionSource = detailSource,
                translationService =
                    createTestSubmissionTranslationService { request ->
                      request.sourceText.uppercase()
                    },
            )

        runCurrent()
        model.loadAttachmentTextCurrent()
        advanceUntilIdle()
        model.translateAttachmentCurrent()
        advanceUntilIdle()

        val translatedAttachmentState =
            ((model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(1)
                    as SubmissionDetailUiState.Success)
                .attachmentTranslationState
        assertNotNull(translatedAttachmentState)
        assertEquals(
            SubmissionDescriptionTranslationStatus.SUCCESS,
            translatedAttachmentState.blocks.single().status,
        )
        assertEquals("HELLO", translatedAttachmentState.blocks.single().translated)

        detailSource.submissions[1] =
            translationTestSubmission(
                1,
                descriptionHtml = "<p>desc</p>",
                downloadUrl = "https://example.com/other.txt",
                downloadFileName = "other.txt",
            )
        model.retryCurrentDetail()
        advanceUntilIdle()

        val refreshedState =
            (model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(1)
                as SubmissionDetailUiState.Success
        assertEquals(
            SubmissionAttachmentTextUiState.Idle("other.txt"),
            refreshedState.attachmentTextState,
        )
        assertNull(refreshedState.attachmentTranslationState)
      }

  @Test
  fun togglesDescriptionWrapAndCachesRawAndWrappedTranslationsSeparately() =
      runTest(dispatcher.scheduler) {
        val detailSource =
            MutableSubmissionDetailSource(
                submissions =
                    mutableMapOf(
                        1 to translationTestSubmission(1, descriptionHtml = "<p>hello<br>world</p>")
                    )
            )
        val translatedRequests = mutableListOf<String>()
        val model =
            createSubmissionScreenModelForTest(
                initialSid = 1,
                items = listOf(translationTestThumbnail(1)),
                submissionSource = detailSource,
                translationService =
                    createTestSubmissionTranslationService { request ->
                      translatedRequests += request.sourceText
                      request.sourceText.uppercase()
                    },
            )

        runCurrent()

        val initialState =
            ((model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(1)
                    as SubmissionDetailUiState.Success)
                .descriptionTranslationState
        assertEquals(SubmissionTranslationSourceMode.RAW, initialState.sourceMode)
        assertEquals(false, initialState.showTranslation)
        assertEquals("hello\nworld", initialState.sourceBlocks.single().sourceText)

        model.translateDescriptionCurrent()
        advanceUntilIdle()

        val rawTranslatedState =
            ((model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(1)
                    as SubmissionDetailUiState.Success)
                .descriptionTranslationState
        assertEquals(true, rawTranslatedState.showTranslation)
        assertEquals(false, rawTranslatedState.isWrapped)
        assertEquals(
            SubmissionDescriptionTranslationStatus.SUCCESS,
            rawTranslatedState.blocks.single().status,
        )
        assertEquals("HELLO\nWORLD", rawTranslatedState.blocks.single().translated)
        assertEquals(listOf("hello\nworld"), translatedRequests)

        model.translateDescriptionCurrent()
        runCurrent()
        model.toggleDescriptionWrapCurrent()
        runCurrent()

        val wrappedIdleState =
            ((model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(1)
                    as SubmissionDetailUiState.Success)
                .descriptionTranslationState
        assertEquals(SubmissionTranslationSourceMode.WRAPPED, wrappedIdleState.sourceMode)
        assertEquals(false, wrappedIdleState.showTranslation)
        assertEquals("hello world", wrappedIdleState.sourceBlocks.single().sourceText)
        assertEquals("hello world", wrappedIdleState.blocks.single().originalText)
        assertEquals(
            SubmissionDescriptionTranslationStatus.IDLE,
            wrappedIdleState.blocks.single().status,
        )

        model.translateDescriptionCurrent()
        advanceUntilIdle()

        val wrappedTranslatedState =
            ((model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(1)
                    as SubmissionDetailUiState.Success)
                .descriptionTranslationState
        assertEquals(true, wrappedTranslatedState.showTranslation)
        assertEquals(true, wrappedTranslatedState.isWrapped)
        assertEquals(
            SubmissionDescriptionTranslationStatus.SUCCESS,
            wrappedTranslatedState.blocks.single().status,
        )
        assertEquals("HELLO WORLD", wrappedTranslatedState.blocks.single().translated)
        assertEquals(listOf("hello\nworld", "hello world"), translatedRequests)

        model.translateDescriptionCurrent()
        runCurrent()
        model.toggleDescriptionWrapCurrent()
        runCurrent()
        model.translateDescriptionCurrent()
        advanceUntilIdle()

        val restoredRawTranslatedState =
            ((model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(1)
                    as SubmissionDetailUiState.Success)
                .descriptionTranslationState
        assertEquals(SubmissionTranslationSourceMode.RAW, restoredRawTranslatedState.sourceMode)
        assertEquals(true, restoredRawTranslatedState.showTranslation)
        assertEquals("HELLO\nWORLD", restoredRawTranslatedState.blocks.single().translated)
        assertEquals(listOf("hello\nworld", "hello world"), translatedRequests)
      }

  @Test
  fun retriesDescriptionTranslationAfterFailureIsDismissed() =
      runTest(dispatcher.scheduler) {
        val detailSource =
            MutableSubmissionDetailSource(
                submissions =
                    mutableMapOf(
                        1 to translationTestSubmission(1, descriptionHtml = "<p>hello</p>")
                    )
            )
        var requestCount = 0
        val model =
            createSubmissionScreenModelForTest(
                initialSid = 1,
                items = listOf(translationTestThumbnail(1)),
                submissionSource = detailSource,
                translationService =
                    createTestSubmissionTranslationService { request ->
                      requestCount += 1
                      if (requestCount == 1) {
                        error("boom")
                      }
                      request.sourceText.uppercase()
                    },
            )

        runCurrent()

        model.translateDescriptionCurrent()
        advanceUntilIdle()

        val failedState =
            ((model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(1)
                    as SubmissionDetailUiState.Success)
                .descriptionTranslationState
        assertEquals(true, failedState.showTranslation)
        assertEquals(
            SubmissionDescriptionTranslationStatus.FAILURE,
            failedState.blocks.single().status,
        )

        model.translateDescriptionCurrent()
        runCurrent()

        val hiddenState =
            ((model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(1)
                    as SubmissionDetailUiState.Success)
                .descriptionTranslationState
        assertEquals(false, hiddenState.showTranslation)

        model.translateDescriptionCurrent()
        advanceUntilIdle()

        val retriedState =
            ((model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(1)
                    as SubmissionDetailUiState.Success)
                .descriptionTranslationState
        assertEquals(2, requestCount)
        assertEquals(true, retriedState.showTranslation)
        assertEquals(
            SubmissionDescriptionTranslationStatus.SUCCESS,
            retriedState.blocks.single().status,
        )
        assertEquals("HELLO", retriedState.blocks.single().translated)
        assertTrue(retriedState.hasTriggered)
      }

  @Test
  fun retriesDescriptionTranslationWithNextProviderFromFeedbackAction() =
      runTest(dispatcher.scheduler) {
        val settingsService = createTestAppSettingsService()
        val detailSource =
            MutableSubmissionDetailSource(
                submissions =
                    mutableMapOf(
                        1 to translationTestSubmission(1, descriptionHtml = "<p>hello</p>")
                    )
            )
        val requestedProviders = mutableListOf<TranslationProvider>()
        val feedbackEvents = mutableListOf<me.domino.fa2.ui.components.AppFeedbackRequest>()
        val model =
            createSubmissionScreenModelForTest(
                initialSid = 1,
                items = listOf(translationTestThumbnail(1)),
                submissionSource = detailSource,
                translationService =
                    createTestSubmissionTranslationService(settingsService = settingsService) {
                        request ->
                      requestedProviders += request.provider
                      when (request.provider) {
                        TranslationProvider.GOOGLE -> error("boom")
                        TranslationProvider.MICROSOFT -> request.sourceText.uppercase()
                        TranslationProvider.OPENAI_COMPATIBLE -> error("unexpected provider")
                      }
                    },
                imageOcrTranslationService =
                    createTestSubmissionImageOcrTranslationService(
                        settingsService = settingsService
                    ),
                settingsService = settingsService,
            )
        val feedbackJob = launch {
          model.feedbackEvents.collect { request -> feedbackEvents += request }
        }

        runCurrent()
        model.translateDescriptionCurrent()
        advanceUntilIdle()

        val feedback = assertNotNull(feedbackEvents.firstOrNull())
        feedback.onAction?.invoke()
        advanceUntilIdle()

        val retriedState =
            ((model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(1)
                    as SubmissionDetailUiState.Success)
                .descriptionTranslationState
        assertEquals(
            listOf(TranslationProvider.GOOGLE, TranslationProvider.MICROSOFT),
            requestedProviders,
        )
        assertEquals(
            TranslationProvider.MICROSOFT,
            settingsService.settings.value.translationProvider,
        )
        assertEquals(
            SubmissionDescriptionTranslationStatus.SUCCESS,
            retriedState.blocks.single().status,
        )
        assertEquals("HELLO", retriedState.blocks.single().translated)

        feedbackJob.cancel()
      }

  @Test
  fun retriesAttachmentTranslationWithNextProviderFromFeedbackAction() =
      runTest(dispatcher.scheduler) {
        val settingsService = createTestAppSettingsService()
        val detailSource =
            MutableSubmissionDetailSource(
                submissions =
                    mutableMapOf(
                        1 to
                            translationTestSubmission(
                                1,
                                descriptionHtml = "<p>desc</p>",
                                downloadUrl = "https://example.com/sample.txt",
                                downloadFileName = "sample.txt",
                            )
                    ),
                attachmentResults =
                    ArrayDeque(
                        listOf(PageState.Success(translationTestAttachmentDocument("hello")))
                    ),
            )
        val requestedProviders = mutableListOf<TranslationProvider>()
        val feedbackEvents = mutableListOf<me.domino.fa2.ui.components.AppFeedbackRequest>()
        val model =
            createSubmissionScreenModelForTest(
                initialSid = 1,
                items = listOf(translationTestThumbnail(1)),
                submissionSource = detailSource,
                translationService =
                    createTestSubmissionTranslationService(settingsService = settingsService) {
                        request ->
                      requestedProviders += request.provider
                      when (request.provider) {
                        TranslationProvider.GOOGLE -> error("boom")
                        TranslationProvider.MICROSOFT -> request.sourceText.uppercase()
                        TranslationProvider.OPENAI_COMPATIBLE -> error("unexpected provider")
                      }
                    },
                imageOcrTranslationService =
                    createTestSubmissionImageOcrTranslationService(
                        settingsService = settingsService
                    ),
                settingsService = settingsService,
            )
        val feedbackJob = launch {
          model.feedbackEvents.collect { request -> feedbackEvents += request }
        }

        runCurrent()
        model.loadAttachmentTextCurrent()
        advanceUntilIdle()
        model.translateAttachmentCurrent()
        advanceUntilIdle()

        val feedback = assertNotNull(feedbackEvents.firstOrNull())
        feedback.onAction?.invoke()
        advanceUntilIdle()

        val retriedState =
            ((model.state.value as SubmissionPagerUiState.Data).detailBySid.getValue(1)
                    as SubmissionDetailUiState.Success)
                .attachmentTranslationState
        assertNotNull(retriedState)
        assertEquals(
            listOf(TranslationProvider.GOOGLE, TranslationProvider.MICROSOFT),
            requestedProviders,
        )
        assertEquals(
            TranslationProvider.MICROSOFT,
            settingsService.settings.value.translationProvider,
        )
        assertEquals(
            SubmissionDescriptionTranslationStatus.SUCCESS,
            retriedState.blocks.single().status,
        )
        assertEquals("HELLO", retriedState.blocks.single().translated)

        feedbackJob.cancel()
      }

  @Test
  fun remembersScrollOffsetsAndAdvancesScrollToTopVersionForCurrentSid() =
      runTest(dispatcher.scheduler) {
        val items = listOf(translationTestThumbnail(1), translationTestThumbnail(2))
        val detailSource =
            MutableSubmissionDetailSource(
                submissions =
                    mutableMapOf(
                        1 to translationTestSubmission(1),
                        2 to translationTestSubmission(2),
                    )
            )
        val model =
            createSubmissionScreenModelForTest(
                initialSid = 1,
                items = items,
                submissionSource = detailSource,
                translationService = createTestSubmissionTranslationService(),
            )

        runCurrent()
        model.setCurrentPageScrollOffset(1, 128)
        assertEquals(128, model.scrollOffsetForSid(1))
        assertEquals(0, model.scrollOffsetForSid(2))

        val versionBeforeFirst = model.scrollToTopVersionForSid(1)
        val versionBeforeSecond = model.scrollToTopVersionForSid(2)
        model.requestCurrentPageScrollToTop()

        assertEquals(0, model.scrollOffsetForSid(1))
        assertEquals(versionBeforeFirst + 1, model.scrollToTopVersionForSid(1))
        assertEquals(versionBeforeSecond, model.scrollToTopVersionForSid(2))

        model.onPageChanged(1)
        runCurrent()
        model.setCurrentPageScrollOffset(2, 44)
        model.requestCurrentPageScrollToTop()

        assertEquals(0, model.scrollOffsetForSid(2))
        assertEquals(versionBeforeSecond + 1, model.scrollToTopVersionForSid(2))
        assertEquals(versionBeforeFirst + 1, model.scrollToTopVersionForSid(1))
      }
}

private class MutableSubmissionDetailSource(
    val submissions: MutableMap<Int, Submission>,
    private val attachmentResults: ArrayDeque<PageState<AttachmentTextDocument>> = ArrayDeque(),
) : SubmissionPagerDetailSource {
  override suspend fun loadBySid(sid: Int): PageState<Submission> =
      submissions[sid]?.let { submission -> PageState.Success(submission) }
          ?: PageState.Error(IllegalStateException("Missing submission for sid=$sid"))

  override suspend fun loadByUrl(url: String): PageState<Submission> {
    val sid =
        parseSubmissionSid(url)
            ?: return PageState.Error(IllegalArgumentException("Invalid submission url: $url"))
    return loadBySid(sid)
  }

  override suspend fun loadAttachmentText(
      downloadUrl: String,
      downloadFileName: String,
      onProgress: (AttachmentTextProgress) -> Unit,
  ): PageState<AttachmentTextDocument> {
    onProgress(
        AttachmentTextProgress(
            overallFraction = 0.5f,
            stageIndex = 1,
            stageCount = 1,
            stageId = "decode_bytes",
            stageLabel = "解析附件",
            stageFraction = 0.5f,
            message = "处理中",
            currentItemLabel = downloadFileName,
        )
    )
    return attachmentResults.removeFirstOrNull()
        ?: PageState.Error(IllegalStateException("No attachment result prepared"))
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

private fun translationTestAttachmentDocument(text: String): AttachmentTextDocument =
    AttachmentTextDocument(
        format = AttachmentTextFormat.TEXT,
        html = "<p>$text</p>",
        paragraphs = listOf(AttachmentTextParagraph(html = "<p>$text</p>")),
    )

private fun translationTestThumbnail(sid: Int): SubmissionThumbnail =
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

private fun translationTestSubmission(
    sid: Int,
    descriptionHtml: String = "<p>submission-$sid</p>",
    downloadUrl: String? = null,
    downloadFileName: String? = null,
): Submission =
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
        downloadUrl = downloadUrl,
        downloadFileName = downloadFileName,
        aspectRatio = 1f,
        descriptionHtml = descriptionHtml,
    )
