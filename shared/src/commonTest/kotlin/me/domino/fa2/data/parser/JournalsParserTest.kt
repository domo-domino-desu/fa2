package me.domino.fa2.data.fa.journal

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.utils.FaUrls

/** JournalsParser 解析测试。 */
class JournalsParserTest {
  @Test
  fun parsesNonEmptyJournalsAndOlderPage() {
    val html = TestFixtures.read("www.furaffinity.net:journals:artist-alpha:.html")
    val parser = JournalsParser()

    val page = parser.parse(html = html, baseUrl = FaUrls.journals("artist-alpha"))

    assertTrue(page.journals.isNotEmpty())
    assertTrue(page.nextPageUrl.orEmpty().contains("/journals/artist-alpha/2/"))
    val first = page.journals.first()
    assertTrue(first.id > 0)
    assertTrue(first.title.isNotBlank())
    assertTrue(first.journalUrl.startsWith("https://www.furaffinity.net/journal/"))
    assertTrue(first.excerpt.isNotBlank())
  }

  @Test
  fun parsesEmptyJournalsPage() {
    val html = TestFixtures.read("www.furaffinity.net:journals:empty-user-empty.html")
    val parser = JournalsParser()

    val page = parser.parse(html = html, baseUrl = FaUrls.journals("empty-user"))

    assertTrue(page.journals.isEmpty())
    assertNull(page.nextPageUrl)
  }
}
