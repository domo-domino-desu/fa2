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
    val html = TestFixtures.read("www.furaffinity.net:view:10000001-nocomment.html")
    val parser = SubmissionParser()

    val detail = parser.parse(html = html, url = FaUrls.submission(10000001))

    assertEquals(10000001, detail.id)
    assertEquals("Sanitized Submission One", detail.title)
    assertEquals("artist-delta", detail.author)
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
    val html = TestFixtures.read("www.furaffinity.net:view:10000002-comments.html")
    val parser = SubmissionParser()

    val detail = parser.parse(html = html, url = FaUrls.submission(10000002))

    assertEquals(10000002, detail.id)
    assertEquals("Sanitized Submission Two", detail.title)
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
    assertTrue(detail.submissionUrl.endsWith("/view/10000002/"))
    assertTrue(detail.comments.isNotEmpty())
    assertTrue(detail.comments.any { comment -> comment.depth > 0 })
  }

  @Test
  fun detectsFavoritedStateFromUnfavAction() {
    val html = TestFixtures.read("www.furaffinity.net:view:10000001-nocomment.html")
    val parser = SubmissionParser()
    val mutated = html.replace("/fav/10000001/", "/unfav/10000001/").replace(">+Fav<", ">-Fav<")

    val detail = parser.parse(html = mutated, url = FaUrls.submission(10000001))

    assertEquals(true, detail.isFavorited)
    assertTrue(detail.favoriteActionUrl.contains("/unfav/"))
  }

  @Test
  fun parsesBlockedTagStateFromSubmissionPage() {
    val html = TestFixtures.read("www.furaffinity.net:view:10000003-blocked-male.html")
    val parser = SubmissionParser()

    val detail = parser.parse(html = html, url = FaUrls.submission(10000003))

    assertTrue(detail.tagBlockNonce.isNotBlank())
    assertTrue(detail.blockedTagNames.contains("male"))
    assertTrue(detail.keywords.contains("male"))
  }

  @Test
  fun parsesModernSubmissionPageLayout() {
    val html = TestFixtures.read("www.furaffinity.net:view:10000004-modern.html")
    val parser = SubmissionParser()

    val detail = parser.parse(html = html, url = FaUrls.submission(10000004))

    assertEquals(10000004, detail.id)
    assertEquals("Sanitized Modern Submission", detail.title)
    assertEquals("artist-gamma", detail.author)
    assertEquals("Artist Gamma", detail.authorDisplayName)
    assertEquals(22, detail.viewCount)
    assertEquals(0, detail.commentCount)
    assertEquals(1, detail.favoriteCount)
    assertEquals("General", detail.rating)
    assertEquals("All", detail.category)
    assertEquals("Anime", detail.type)
    assertEquals("Pokemon", detail.species)
    assertEquals("2478 x 1487", detail.size)
    assertEquals("1.96 MB", detail.fileSize)
    assertTrue(detail.keywords.contains("female"))
    assertTrue(detail.keywords.contains("pokemon"))
    assertTrue(detail.downloadUrl?.startsWith("https://d.furaffinity.net/") == true)
    assertTrue(detail.descriptionHtml.contains("Sanitized modern description"))
  }

  @Test
  fun fallsBackToPreviewWhenFullImageUrlMissing() {
    val html = TestFixtures.read("www.furaffinity.net:view:10000001-nocomment.html")
    val parser = SubmissionParser()
    val mutated =
        html.replace(Regex("""(?s)(<img[^>]*id="submissionImg"[^>]*)(>)""")) { match ->
          match.groupValues[1]
              .replace(Regex("""\sdata-fullview-src="[^"]*"""), "")
              .replace(
                  Regex("""\ssrc="[^"]*"""),
                  """ src="//t.furaffinity.net/10000001@600-1665402309.jpg"""",
              ) + match.groupValues[2]
        }

    val detail = parser.parse(html = mutated, url = FaUrls.submission(10000001))

    assertTrue(detail.previewImageUrl.startsWith("https://"))
    assertEquals("https://t.furaffinity.net/10000001@600-1665402309.jpg", detail.fullImageUrl)
  }

  @Test
  fun fallsBackToOgImageWhenSubmissionImgHasNoUsableUrls() {
    val html = TestFixtures.read("www.furaffinity.net:view:10000001-nocomment.html")
    val parser = SubmissionParser()
    val mutated =
        html.replace(Regex("""(?s)(<img[^>]*id="submissionImg"[^>]*)(>)""")) { match ->
          match.groupValues[1]
              .replace(Regex("""\sdata-fullview-src="[^"]*"""), "")
              .replace(Regex("""\sdata-preview-src="[^"]*"""), "")
              .replace(Regex("""\ssrc="[^"]*"""), "") + match.groupValues[2]
        }

    val detail = parser.parse(html = mutated, url = FaUrls.submission(10000001))

    assertTrue(detail.previewImageUrl.isBlank())
    assertEquals("https://t.furaffinity.net/10000001@600-1665402309.jpg", detail.fullImageUrl)
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
