package me.domino.fa2.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.util.FaUrls

/** JournalParser 解析测试。 */
class JournalParserTest {
  @Test
  fun parsesJournalWithComments() {
    val html = TestFixtures.read("www.furaffinity.net:journal:20000001-withcomments.html")
    val parser = JournalParser()

    val detail = parser.parse(html = html, url = FaUrls.journal(20000001))

    assertEquals(20000001, detail.id)
    assertTrue(detail.title.isNotBlank())
    assertTrue(detail.timestampNatural.isNotBlank())
    assertTrue(detail.rating.isNotBlank())
    assertTrue(detail.bodyHtml.isNotBlank())
    assertTrue(detail.commentCount >= 0)
  }

  @Test
  fun parsesJournalWithDisabledComments() {
    val html = TestFixtures.read("www.furaffinity.net:journal:20000002-disabled-comments.html")
    val parser = JournalParser()

    val detail = parser.parse(html = html, url = FaUrls.journal(20000002))

    assertEquals(20000002, detail.id)
    assertTrue(detail.title.isNotBlank())
    assertTrue(detail.bodyHtml.contains("bbcode"))
    assertTrue(detail.commentCount >= 0)
  }
}
