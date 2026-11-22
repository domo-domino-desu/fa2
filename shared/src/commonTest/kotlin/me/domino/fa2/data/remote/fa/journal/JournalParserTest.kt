package me.domino.fa2.data.remote.fa.journal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.utils.FaUrls

/** JournalParser 解析测试。 */
class JournalParserTest {
  @Test
  fun parsesJournalWithComments() {
    val html = TestFixtures.read("live/journal.html")
    val parser = JournalParser()

    val detail = parser.parse(html = html, url = FaUrls.journal(11377368))

    assertEquals(11377368, detail.id)
    assertTrue(detail.title.isNotBlank())
    assertTrue(detail.timestampNatural.isNotBlank())
    assertTrue(detail.rating.isNotBlank())
    assertTrue(detail.bodyHtml.isNotBlank())
    assertTrue(detail.commentCount >= 0)
  }

  @Test
  fun parsesJournalBody() {
    val html = TestFixtures.read("live/journal.html")
    val parser = JournalParser()

    val detail = parser.parse(html = html, url = FaUrls.journal(11377368))

    assertEquals(11377368, detail.id)
    assertTrue(detail.title.isNotBlank())
    assertTrue(detail.bodyHtml.contains("bbcode"))
    assertTrue(detail.commentCount >= 0)
  }
}
