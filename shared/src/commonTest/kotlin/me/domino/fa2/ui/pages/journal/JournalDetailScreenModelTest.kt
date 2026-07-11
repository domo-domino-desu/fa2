package me.domino.fa2.ui.pages.journal

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.domino.fa2.data.model.QueryState
import me.domino.fa2.data.remote.fa.core.FaHtmlDataSource
import me.domino.fa2.data.remote.fa.core.HtmlResponseResult
import me.domino.fa2.data.remote.fa.core.PageQueryCache
import me.domino.fa2.data.remote.fa.journal.JournalParser
import me.domino.fa2.data.remote.fa.journal.JournalRepository
import me.domino.fa2.fake.InMemoryPageCacheDao
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.ui.components.html.TranslationSourceMode
import me.domino.fa2.ui.pages.submission.createTestSubmissionTranslationService
import me.domino.fa2.utils.FaUrls

/**
 * Coverage for JournalDetailScreenModel's QueryState wiring (load/refresh/stale-on-failure) and its
 * translation orchestration (translate/toggle-wrap/reuse-on-replay), which had no test at all after
 * the Store5 -> QueryState migration dropped the pre-refactor equivalent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalDetailScreenModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val journalId = 20000001

  @BeforeTest
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun loadsJournalIntoSuccessState() =
      runTest(dispatcher.scheduler) {
        val source = ScriptedHtmlDataSource()
        source.enqueueFixture(journalId)
        val model =
            JournalDetailScreenModel(
                journalId = journalId,
                journalUrl = null,
                repository = buildRepository(source),
                translationService = createTestSubmissionTranslationService(),
            )

        runCurrent()

        val success = assertIs<QueryState.Success<JournalDetailUiState>>(model.state.value)
        assertEquals(journalId, success.data.detail.id)
        assertEquals(TranslationSourceMode.RAW, success.data.bodyTranslationState.sourceMode)
        assertEquals(false, success.data.bodyTranslationState.showTranslation)
      }

  @Test
  fun refreshOnFailureKeepsStalePreviousData() =
      runTest(dispatcher.scheduler) {
        val source = ScriptedHtmlDataSource()
        source.enqueueFixture(journalId)
        val repository = buildRepository(source)
        val model =
            JournalDetailScreenModel(
                journalId = journalId,
                journalUrl = null,
                repository = repository,
                translationService = createTestSubmissionTranslationService(),
            )
        runCurrent()
        val initial = assertIs<QueryState.Success<JournalDetailUiState>>(model.state.value)

        source.enqueue(
            FaUrls.journal(journalId),
            HtmlResponseResult.Error(statusCode = 503, message = "temporary unavailable"),
        )
        model.load()
        runCurrent()

        val failure = assertIs<QueryState.Failure<JournalDetailUiState>>(model.state.value)
        assertEquals(initial.data.detail.id, failure.data?.detail?.id)
      }

  @Test
  fun translateCurrentTranslatesTogglesAndReusesCachedResult() =
      runTest(dispatcher.scheduler) {
        val source = ScriptedHtmlDataSource()
        source.enqueueFixture(journalId)
        var translateCalls = 0
        val model =
            JournalDetailScreenModel(
                journalId = journalId,
                journalUrl = null,
                repository = buildRepository(source),
                translationService =
                    createTestSubmissionTranslationService(
                        translate = { request ->
                          translateCalls += 1
                          request.sourceText.uppercase()
                        }
                    ),
            )
        runCurrent()
        val loaded = assertIs<QueryState.Success<JournalDetailUiState>>(model.state.value)
        val sourceTexts = loaded.data.bodyTranslationState.sourceBlocks.map { it.sourceText }
        assertTrue(sourceTexts.isNotEmpty())

        model.translateCurrent()
        advanceUntilIdle()

        val translated = assertIs<QueryState.Success<JournalDetailUiState>>(model.state.value)
        assertEquals(true, translated.data.bodyTranslationState.showTranslation)
        assertEquals(
            sourceTexts.map { it.uppercase() },
            translated.data.bodyTranslationState.blocks.map { it.translated },
        )
        assertTrue(translateCalls > 0)
        val callsAfterFirstTranslate = translateCalls

        // Toggling off then back on must not re-request translation (cached per sourceKey).
        model.translateCurrent()
        runCurrent()
        model.translateCurrent()
        runCurrent()
        assertEquals(callsAfterFirstTranslate, translateCalls)
      }

  @Test
  fun toggleWrapTextCurrentSwitchesSourceMode() =
      runTest(dispatcher.scheduler) {
        val source = ScriptedHtmlDataSource()
        source.enqueueFixture(journalId)
        val model =
            JournalDetailScreenModel(
                journalId = journalId,
                journalUrl = null,
                repository = buildRepository(source),
                translationService = createTestSubmissionTranslationService(),
            )
        runCurrent()

        model.toggleWrapTextCurrent()

        val wrapped = assertIs<QueryState.Success<JournalDetailUiState>>(model.state.value)
        assertEquals(TranslationSourceMode.WRAPPED, wrapped.data.bodyTranslationState.sourceMode)
      }

  private fun buildRepository(source: FaHtmlDataSource): JournalRepository =
      JournalRepository(source, JournalParser(), PageQueryCache(InMemoryPageCacheDao()))
}

private class ScriptedHtmlDataSource : FaHtmlDataSource {
  private val queueByUrl: MutableMap<String, ArrayDeque<HtmlResponseResult>> = mutableMapOf()

  fun enqueue(url: String, response: HtmlResponseResult) {
    queueByUrl.getOrPut(url) { ArrayDeque() }.addLast(response)
  }

  fun enqueueFixture(journalId: Int) {
    val url = FaUrls.journal(journalId)
    enqueue(
        url,
        HtmlResponseResult.Success(
            body = TestFixtures.read("www.furaffinity.net:journal:20000001-withcomments.html"),
            url = url,
        ),
    )
  }

  override suspend fun get(url: String): HtmlResponseResult {
    val queue = queueByUrl[url]
    if (queue == null || queue.isEmpty()) {
      return HtmlResponseResult.Error(statusCode = 500, message = "No scripted response for $url")
    }
    return queue.removeFirst()
  }
}
