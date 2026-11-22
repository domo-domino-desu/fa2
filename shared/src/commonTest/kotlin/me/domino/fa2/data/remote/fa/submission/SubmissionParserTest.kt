package me.domino.fa2.data.remote.fa.submission

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.utils.FaUrls

/** SubmissionParser 解析测试。 */
class SubmissionParserTest {
  private val latestFixture = "live/submission.html"
  private val latestSid = 67119566

  @Test
  fun parsesLatestSubmissionPageLayout() {
    val detail =
        SubmissionParser().parse(TestFixtures.read(latestFixture), FaUrls.submission(latestSid))
    assertEquals(latestSid, detail.id)
    assertTrue(detail.title.isNotBlank())
    assertTrue(detail.author.isNotBlank())
    assertTrue(detail.authorDisplayName.isNotBlank())
    assertTrue(detail.authorAvatarUrl.startsWith("https://a.furaffinity.net/"))
    assertTrue(detail.viewCount >= 0 && detail.favoriteCount >= 0 && detail.commentCount >= 0)
    assertTrue(detail.rating.isNotBlank())
    assertTrue(detail.descriptionHtml.isNotBlank())
    assertTrue(detail.previewImageUrl.startsWith("https://"))
    assertTrue(detail.fullImageUrl.startsWith("https://"))
    assertTrue(detail.downloadFileName.orEmpty().isNotBlank())
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
    val html =
        TestFixtures.read(latestFixture)
            .replace(
                Regex("""data-tag-blocklist="[^"]*""""),
                """data-tag-blocklist="unit-block-tag"""",
            )
    val detail = SubmissionParser().parse(html, FaUrls.submission(latestSid))
    assertTrue(detail.blockedTagNames.contains("unit-block-tag"))
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
        com.fleeksoft.ksoup.Ksoup.parse(html)
            .selectFirst("meta[property=og:image]")!!
            .attr("content"),
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
      parser.parse(html = "<html><body><h1>broken</h1></body></html>", url = FaUrls.submission(1))
    }
  }
}
