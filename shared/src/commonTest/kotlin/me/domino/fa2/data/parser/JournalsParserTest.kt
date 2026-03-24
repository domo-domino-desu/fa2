package me.domino.fa2.data.parser

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.util.FaUrls

/** JournalsParser 解析测试。 */
class JournalsParserTest {
  @Test
  fun parsesNonEmptyJournalsAndOlderPage() {
    val html = TestFixtures.read("www.furaffinity.net:journals:tiaamaito:.html")
    val parser = JournalsParser()

    val page = parser.parse(html = html, baseUrl = FaUrls.journals("tiaamaito"))

    assertTrue(page.journals.isNotEmpty())
    assertTrue(page.nextPageUrl.orEmpty().contains("/journals/tiaamaito/2/"))
    val first = page.journals.first()
    assertTrue(first.id > 0)
    assertTrue(first.title.isNotBlank())
    assertTrue(first.journalUrl.startsWith("https://www.furaffinity.net/journal/"))
    assertTrue(first.excerpt.isNotBlank())
  }

  @Test
  fun parsesEmptyJournalsPage() {
    val html = TestFixtures.read("www.furaffinity.net:journals:maziurek-empty.html")
    val parser = JournalsParser()

    val page = parser.parse(html = html, baseUrl = FaUrls.journals("maziurek"))

    assertTrue(page.journals.isEmpty())
    assertNull(page.nextPageUrl)
  }
}
