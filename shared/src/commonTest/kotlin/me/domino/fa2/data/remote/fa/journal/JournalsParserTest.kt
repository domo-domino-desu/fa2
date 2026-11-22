package me.domino.fa2.data.remote.fa.journal

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.utils.FaUrls

/** JournalsParser 解析测试。 */
class JournalsParserTest {
  @Test
  fun parsesNonEmptyJournalsAndOlderPage() {
    val html = TestFixtures.read("live/journals.html")
    val parser = JournalsParser()

    val page = parser.parse(html = html, baseUrl = FaUrls.journals("artist-alpha"))

    assertTrue(page.journals.isNotEmpty())
    assertTrue(page.nextPageUrl.orEmpty().contains("/journals/"))
    val first = page.journals.first()
    assertTrue(first.id > 0)
    assertTrue(first.title.isNotBlank())
    assertTrue(first.journalUrl.startsWith("https://www.furaffinity.net/journal/"))
    assertTrue(first.excerpt.isNotBlank())
  }

  @Test
  fun parsesEmptyJournalsPage() {
    val html = "<html><body></body></html>"
    val parser = JournalsParser()

    val page = parser.parse(html = html, baseUrl = FaUrls.journals("empty-user"))

    assertTrue(page.journals.isEmpty())
    assertNull(page.nextPageUrl)
  }
}
