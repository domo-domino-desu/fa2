package me.domino.fa2.ui.pages.user.journal

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.domino.fa2.data.model.JournalPage
import me.domino.fa2.data.model.JournalSummary
import me.domino.fa2.data.model.PageState

@OptIn(ExperimentalCoroutinesApi::class)
class JournalPagerContextScreenModelTest {
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
  fun setCurrentIndexUsesJournalIdAnchor() {
    val model = JournalPagerContextScreenModel()
    model.seed(
        ownerUsername = "artist",
        journals = listOf(journal(1), journal(2), journal(3)),
        selectedJournalId = 2,
        nextPageUrl = "page-2",
    )

    model.setCurrentIndex(2)

    assertEquals(3, model.state.value.currentJournalId)
    assertEquals(2, model.state.value.currentIndex)
  }

  @Test
  fun requestAppendMergesJournalsAndKeepsCurrentJournal() =
      runTest(dispatcher.scheduler) {
        val model = JournalPagerContextScreenModel()
        var loadCount = 0
        model.seed(
            ownerUsername = "artist",
            journals = listOf(journal(1), journal(2)),
            selectedJournalId = 2,
            nextPageUrl = "page-2",
        )

        model.requestAppend {
          loadCount++
          PageState.Success(
              JournalPage(journals = listOf(journal(2), journal(3)), nextPageUrl = null)
          )
        }
        model.requestAppend {
          loadCount++
          PageState.Success(JournalPage(journals = listOf(journal(4)), nextPageUrl = null))
        }
        runCurrent()

        assertEquals(1, loadCount)
        assertEquals(listOf(1, 2, 3), model.state.value.journals.map { it.id })
        assertEquals(2, model.state.value.currentJournalId)
        assertEquals(null, model.state.value.nextPageUrl)
      }
}

private fun journal(id: Int): JournalSummary =
    JournalSummary(
        id = id,
        title = "Journal $id",
        journalUrl = "https://www.furaffinity.net/journal/$id/",
        timestampNatural = "today",
        timestampRaw = null,
        commentCount = 0,
        excerpt = "",
    )
