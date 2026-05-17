package me.domino.fa2.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.util.FaUrls

/** SubmissionParser 解析测试。 */
class SubmissionParserTest {
  private val latestFixture = "www.furaffinity.net:view:20000009.html"
  private val latestSid = 20000009

  @Test
  fun parsesLatestSubmissionPageLayout() {
    val html = TestFixtures.read(latestFixture)
    val parser = SubmissionParser()

    val detail = parser.parse(html = html, url = FaUrls.submission(latestSid))

    assertEquals(latestSid, detail.id)
    assertEquals("Sanitized Latest Submission", detail.title)
    assertEquals("artist-alpha", detail.author)
    assertEquals("Artist Alpha", detail.authorDisplayName)
    assertTrue(detail.authorAvatarUrl.startsWith("https://a.furaffinity.net/"))
    assertEquals(657, detail.viewCount)
    assertEquals(8, detail.commentCount)
    assertEquals(126, detail.favoriteCount)
    assertEquals("Adult", detail.rating)
    assertEquals("All", detail.category)
    assertEquals("All", detail.type)
    assertEquals("Unspecified / Any", detail.species)
    assertEquals("2351 x 1567", detail.size)
    assertEquals("3.19 MB", detail.fileSize)
    assertTrue(detail.favoriteActionUrl.contains("/fav/"))
    assertEquals(false, detail.isFavorited)
    assertTrue(detail.keywords.isNotEmpty())
    assertTrue(detail.keywords.contains("alpha"))
    assertTrue(detail.keywords.contains("eta"))
    assertTrue(detail.tagBlockNonce.isNotBlank())
    assertTrue(detail.downloadUrl?.startsWith("https://d.furaffinity.net/") == true)
    assertEquals("1700000009.artist-alpha_sanitized_latest_submission.png", detail.downloadFileName)
    assertTrue(detail.submissionUrl.endsWith("/view/$latestSid/"))
    assertTrue(detail.previewImageUrl.startsWith("https://t.furaffinity.net/"))
    assertTrue(detail.fullImageUrl.startsWith("https://d.furaffinity.net/"))
    assertTrue(detail.descriptionHtml.contains("First sanitized description paragraph"))
    assertEquals(8, detail.comments.size)
    assertTrue(detail.comments.any { comment -> comment.depth > 0 })
  }

  @Test
  fun detectsFavoritedStateFromUnfavAction() {
    val html = TestFixtures.read(latestFixture)
    val parser = SubmissionParser()
    val mutated = html.replace("/fav/$latestSid/", "/unfav/$latestSid/").replace(">+Fav<", ">-Fav<")

    val detail = parser.parse(html = mutated, url = FaUrls.submission(latestSid))

    assertEquals(true, detail.isFavorited)
    assertTrue(detail.favoriteActionUrl.contains("/unfav/"))
  }

  @Test
  fun parsesBlockedTagStateFromSubmissionPage() {
    val html = TestFixtures.read(latestFixture)
    val parser = SubmissionParser()

    val detail = parser.parse(html = html, url = FaUrls.submission(latestSid))

    assertTrue(detail.tagBlockNonce.isNotBlank())
    assertTrue(detail.blockedTagNames.contains("blocked-alpha"))
    assertTrue(detail.blockedTagNames.contains("blocked-beta"))
    assertTrue(detail.keywords.contains("alpha"))
  }

  @Test
  fun fallsBackToPreviewWhenFullImageUrlMissing() {
    val html = TestFixtures.read(latestFixture)
    val parser = SubmissionParser()
    val mutated =
        html.replace(Regex("""(?s)(<img[^>]*id="submissionImg"[^>]*)(>)""")) { match ->
          match.groupValues[1]
              .replace(Regex("""\sdata-fullview-src="[^"]*"""), "")
              .replace(
                  Regex("""\ssrc="[^"]*"""),
                  """ src="//t.furaffinity.net/20000009@600-1700000009.jpg"""",
              ) + match.groupValues[2]
        }

    val detail = parser.parse(html = mutated, url = FaUrls.submission(latestSid))

    assertTrue(detail.previewImageUrl.startsWith("https://"))
    assertEquals("https://t.furaffinity.net/20000009@600-1700000009.jpg", detail.fullImageUrl)
  }

  @Test
  fun fallsBackToOgImageWhenSubmissionImgHasNoUsableUrls() {
    val html = TestFixtures.read(latestFixture)
    val parser = SubmissionParser()
    val mutated =
        html.replace(Regex("""(?s)(<img[^>]*id="submissionImg"[^>]*)(>)""")) { match ->
          match.groupValues[1]
              .replace(Regex("""\sdata-fullview-src="[^"]*"""), "")
              .replace(Regex("""\sdata-preview-src="[^"]*"""), "")
              .replace(Regex("""\ssrc="[^"]*"""), "") + match.groupValues[2]
        }

    val detail = parser.parse(html = mutated, url = FaUrls.submission(latestSid))

    assertTrue(detail.previewImageUrl.isBlank())
    assertEquals(
        "https://www.furaffinity.net/themes/beta/img/banners/fa_logo.png?v2",
        detail.fullImageUrl,
    )
  }

  @Test
  fun allowsMissingResolutionRow() {
    val html = TestFixtures.read(latestFixture)
    val parser = SubmissionParser()
    val mutated = html.replace(">Resolution<", ">Resolution-Missing<")

    val detail = parser.parse(html = mutated, url = FaUrls.submission(latestSid))

    assertEquals(null, detail.size)
    assertEquals(1f, detail.aspectRatio)
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
