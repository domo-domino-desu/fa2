package me.domino.fa2.ui.pages.user.journal

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.domino.fa2.data.model.JournalDetail
import me.domino.fa2.data.model.PageComment
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.repository.JournalDetailRepository
import me.domino.fa2.ui.pages.submission.SubmissionTranslationSourceMode
import me.domino.fa2.ui.pages.submission.createTestSubmissionTranslationService
import me.domino.fa2.ui.state.SubmissionDescriptionTranslationStatus
import me.domino.fa2.util.FaUrls

@OptIn(ExperimentalCoroutinesApi::class)
class JournalDetailScreenModelTest {
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
  fun togglesWrapAndCachesRawAndWrappedTranslationsSeparately() =
      runTest(dispatcher.scheduler) {
        val repository =
            FakeJournalDetailRepository(
                details = mutableMapOf(1 to journalDetail(1, bodyHtml = "<p>hello<br>world</p>"))
            )
        val translatedRequests = mutableListOf<String>()
        val model =
            JournalDetailScreenModel(
                journalId = 1,
                journalUrl = null,
                repository = repository,
                translationService =
                    createTestSubmissionTranslationService { request ->
                      translatedRequests += request.sourceText
                      request.sourceText.uppercase()
                    },
            )

        runCurrent()

        val initialState = model.state.value as JournalDetailUiState.Success
        assertEquals(
            SubmissionTranslationSourceMode.RAW,
            initialState.bodyTranslationState.sourceMode,
        )
        assertEquals(false, initialState.bodyTranslationState.showTranslation)
        assertEquals(
            "hello\nworld",
            initialState.bodyTranslationState.sourceBlocks.single().sourceText,
        )

        model.translateCurrent()
        advanceUntilIdle()

        val rawTranslatedState = model.state.value as JournalDetailUiState.Success
        assertEquals(true, rawTranslatedState.bodyTranslationState.showTranslation)
        assertEquals(
            SubmissionDescriptionTranslationStatus.SUCCESS,
            rawTranslatedState.bodyTranslationState.blocks.single().status,
        )
        assertEquals(
            "HELLO\nWORLD",
            rawTranslatedState.bodyTranslationState.blocks.single().translated,
        )
        assertEquals(listOf("hello\nworld"), translatedRequests)

        model.translateCurrent()
        runCurrent()
        model.toggleWrapTextCurrent()
        runCurrent()

        val wrappedIdleState = model.state.value as JournalDetailUiState.Success
        assertEquals(
            SubmissionTranslationSourceMode.WRAPPED,
            wrappedIdleState.bodyTranslationState.sourceMode,
        )
        assertEquals(false, wrappedIdleState.bodyTranslationState.showTranslation)
        assertEquals(
            "hello world",
            wrappedIdleState.bodyTranslationState.sourceBlocks.single().sourceText,
        )
        assertEquals(
            "hello world",
            wrappedIdleState.bodyTranslationState.blocks.single().originalText,
        )
        assertEquals(
            SubmissionDescriptionTranslationStatus.IDLE,
            wrappedIdleState.bodyTranslationState.blocks.single().status,
        )

        model.translateCurrent()
        advanceUntilIdle()

        val wrappedTranslatedState = model.state.value as JournalDetailUiState.Success
        assertEquals(true, wrappedTranslatedState.bodyTranslationState.showTranslation)
        assertEquals(
            SubmissionDescriptionTranslationStatus.SUCCESS,
            wrappedTranslatedState.bodyTranslationState.blocks.single().status,
        )
        assertEquals(
            "HELLO WORLD",
            wrappedTranslatedState.bodyTranslationState.blocks.single().translated,
        )
        assertEquals(listOf("hello\nworld", "hello world"), translatedRequests)

        model.translateCurrent()
        runCurrent()
        model.toggleWrapTextCurrent()
        runCurrent()
        model.translateCurrent()
        advanceUntilIdle()

        val restoredRawTranslatedState = model.state.value as JournalDetailUiState.Success
        assertEquals(
            SubmissionTranslationSourceMode.RAW,
            restoredRawTranslatedState.bodyTranslationState.sourceMode,
        )
        assertEquals(true, restoredRawTranslatedState.bodyTranslationState.showTranslation)
        assertEquals(
            "HELLO\nWORLD",
            restoredRawTranslatedState.bodyTranslationState.blocks.single().translated,
        )
        assertEquals(listOf("hello\nworld", "hello world"), translatedRequests)
      }

  @Test
  fun resetsBodyTranslationWhenJournalBodyChanges() =
      runTest(dispatcher.scheduler) {
        val repository =
            FakeJournalDetailRepository(
                details = mutableMapOf(1 to journalDetail(1, bodyHtml = "<p>hello</p>"))
            )
        val model =
            JournalDetailScreenModel(
                journalId = 1,
                journalUrl = null,
                repository = repository,
                translationService =
                    createTestSubmissionTranslationService { request ->
                      request.sourceText.uppercase()
                    },
            )

        runCurrent()
        model.translateCurrent()
        advanceUntilIdle()

        val translatedState = model.state.value as JournalDetailUiState.Success
        assertEquals(true, translatedState.bodyTranslationState.showTranslation)
        assertEquals(
            SubmissionDescriptionTranslationStatus.SUCCESS,
            translatedState.bodyTranslationState.blocks.single().status,
        )

        repository.details[1] = journalDetail(1, bodyHtml = "<p>changed</p>")
        model.load()
        advanceUntilIdle()

        val resetState = model.state.value as JournalDetailUiState.Success
        assertEquals(false, resetState.bodyTranslationState.showTranslation)
        assertEquals("<p>changed</p>", resetState.bodyTranslationState.sourceHtml)
        assertEquals(
            SubmissionDescriptionTranslationStatus.IDLE,
            resetState.bodyTranslationState.blocks.single().status,
        )
        assertEquals(null, resetState.bodyTranslationState.blocks.single().translated)
      }
}

private class FakeJournalDetailRepository(
    val details: MutableMap<Int, JournalDetail>,
) : JournalDetailRepository {
  override suspend fun loadJournalDetail(journalId: Int): PageState<JournalDetail> =
      details[journalId]?.let { detail -> PageState.Success(detail) }
          ?: PageState.Error(IllegalStateException("Missing journalId=$journalId"))

  override suspend fun loadJournalDetailByUrl(url: String): PageState<JournalDetail> {
    val matched = details.values.firstOrNull { detail -> detail.journalUrl == url }
    return matched?.let { detail -> PageState.Success(detail) }
        ?: PageState.Error(IllegalStateException("Missing journalUrl=$url"))
  }
}

private fun journalDetail(
    journalId: Int,
    bodyHtml: String,
) =
    JournalDetail(
        id = journalId,
        title = "Journal $journalId",
        journalUrl = FaUrls.journal(journalId),
        timestampNatural = "now",
        timestampRaw = null,
        rating = "General",
        bodyHtml = bodyHtml,
        commentCount = 1,
        comments = listOf(journalComment()),
        commentPostingEnabled = true,
        commentPostingMessage = null,
    )

private fun journalComment() =
    PageComment(
        id = 1L,
        author = "author",
        authorDisplayName = "Author",
        authorAvatarUrl = "",
        timestampNatural = "now",
        timestampRaw = null,
        bodyHtml = "<p>comment</p>",
        depth = 0,
    )
