package me.domino.fa2.data.remote.fa.gallery

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import me.domino.fa2.data.remote.fa.journal.JournalsParser
import me.domino.fa2.data.remote.fa.listing.SubmissionListingParser
import me.domino.fa2.fake.TestFixtures

class LiveFixtureParserTest {
  @Test
  fun currentBrowseAndSearchRetainItemsAndPagination() {
    val parser = SubmissionListingParser()
    for ((file, path) in listOf("browse.html" to "/browse/", "search.html" to "/search/?q=wolf")) {
      val page =
          parser.parseListing(
              html = TestFixtures.read("live/$file"),
              baseUrl = "https://www.furaffinity.net$path",
          )
      assertTrue(page.submissions.isNotEmpty(), file)
      assertTrue(page.submissions.all { it.id > 0 }, file)
      assertNotNull(page.nextPageUrl, file)
      val next = Url(page.nextPageUrl)
      assertEquals(listOf("2"), next.parameters.getAll("page"), file)
      if (file == "search.html") {
        assertEquals("wolf", next.parameters["q"])
        assertEquals(1, page.currentPageNumber)
      }
    }
  }

  @Test
  fun currentJournalsRetainItemsAndPagination() {
    val page =
        JournalsParser()
            .parse(
                html = TestFixtures.read("live/journals.html"),
                baseUrl = "https://www.furaffinity.net/journals/fender/",
            )
    assertTrue(page.journals.isNotEmpty())
    assertTrue(page.journals.all { it.id > 0 && it.title.isNotBlank() })
    assertNotNull(page.nextPageUrl)
  }
}
