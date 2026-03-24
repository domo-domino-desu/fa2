package me.domino.fa2.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.util.FaUrls

/** SubmissionParser 解析测试。 */
class SubmissionParserTest {
  @Test
  fun parsesSubmissionPageWithoutComments() {
    val html = TestFixtures.read("www.furaffinity.net:view:49338772-nocomment.html")
    val parser = SubmissionParser()

    val detail = parser.parse(html = html, url = FaUrls.submission(49338772))

    assertEquals(49338772, detail.id)
    assertEquals("The hookah", detail.title)
    assertEquals("annetpeas", detail.author)
    assertTrue(detail.authorAvatarUrl.startsWith("https://a.furaffinity.net/"))
    assertEquals(769, detail.viewCount)
    assertEquals(65, detail.favoriteCount)
    assertTrue(detail.favoriteActionUrl.contains("/fav/"))
    assertEquals(false, detail.isFavorited)
    assertEquals("Artwork (Digital)", detail.category)
    assertEquals("All", detail.type)
    assertEquals("Rabbit / Hare", detail.species)
    assertEquals("1217 x 1280", detail.size)
    assertEquals("1.22 MB", detail.fileSize)
    assertTrue(detail.previewImageUrl.isNotBlank())
    assertTrue(detail.fullImageUrl.isNotBlank())
    assertTrue(detail.downloadUrl?.startsWith("https://") == true)
    assertTrue(detail.descriptionHtml.isNotBlank())
  }

  @Test
  fun parsesSubmissionPageWithComments() {
    val html = TestFixtures.read("www.furaffinity.net:view:48519387-comments.html")
    val parser = SubmissionParser()

    val detail = parser.parse(html = html, url = FaUrls.submission(48519387))

    assertEquals(48519387, detail.id)
    assertEquals("[CLOSED] Adopts Auction - Moon and Dawn", detail.title)
    assertEquals(428, detail.viewCount)
    assertEquals(27, detail.favoriteCount)
    assertEquals("All", detail.category)
    assertEquals("All", detail.type)
    assertEquals("Unspecified / Any", detail.species)
    assertEquals("1280 x 603", detail.size)
    assertEquals("100.2 kB", detail.fileSize)
    assertTrue(detail.keywords.isNotEmpty())
    assertTrue(detail.keywords.contains("auction_adoptable"))
    assertTrue(detail.tagBlockNonce.isNotBlank())
    assertTrue(detail.downloadUrl?.startsWith("https://") == true)
    assertTrue(detail.submissionUrl.endsWith("/view/48519387/"))
  }

  @Test
  fun parsesBlockedTagStateFromSubmissionPage() {
    val html = TestFixtures.read("www.furaffinity.net:view:64419428-blocked-male.html")
    val parser = SubmissionParser()

    val detail = parser.parse(html = html, url = FaUrls.submission(64419428))

    assertTrue(detail.tagBlockNonce.isNotBlank())
    assertTrue(detail.blockedTagNames.contains("male"))
    assertTrue(detail.keywords.contains("male"))
  }

  @Test
  fun failsOnMalformedHtml() {
    val parser = SubmissionParser()
    assertFailsWith<IllegalStateException> {
      parser.parse(
          html = "<html><body><h1>broken</h1></body></html>",
          url = FaUrls.submission(1),
      )
    }
  }
}
