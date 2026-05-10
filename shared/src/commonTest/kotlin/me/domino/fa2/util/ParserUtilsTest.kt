package me.domino.fa2.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import me.domino.fa2.fake.TestFixtures

class ParserUtilsTest {
  @Test
  fun skipsAvatarUrlsWhenAvatarMtimeIsMissing() {
    val html = TestFixtures.read("www.furaffinity.net:gallery:artist-alpha:.html")

    val avatars = parseSubmissionAvatarUrls(html)

    assertFalse(avatars.values.any { value -> value.contains("/0/") })
  }

  @Test
  fun derivesThumbnailUrlFromFullImageUrl() {
    val result =
        deriveSubmissionThumbnailUrlFromFullImage(
            sid = 10000001,
            fullImageUrl =
                "https://d.furaffinity.net/art/artist-delta/1665402309/1665402309.artist-delta_sanitized_submission_one.png",
        )

    assertEquals("https://t.furaffinity.net/10000001@600-1665402309.jpg", result)
  }

  @Test
  fun derivesThumbnailUrlFromLegacyFacdnHost() {
    val result =
        deriveSubmissionThumbnailUrlFromFullImage(
            sid = 14977134,
            fullImageUrl =
                "http://d.facdn.net/art/waccoon/1411105444/1411105444.waccoon_my_picture.jpg",
        )

    assertEquals("https://t.furaffinity.net/14977134@600-1411105444.jpg", result)
  }

  @Test
  fun derivesThumbnailUrlWhenFullUrlContainsQuery() {
    val result =
        deriveSubmissionThumbnailUrlFromFullImage(
            sid = 10000006,
            fullImageUrl =
                "https://d.furaffinity.net/art/minmohere/1684537865/1684537865.minmohere_5-18-23blupee.png?token=abc",
        )

    assertEquals("https://t.furaffinity.net/10000006@600-1684537865.jpg", result)
  }

  @Test
  fun returnsNullWhenFullImageUrlDoesNotMatchPattern() {
    val result =
        deriveSubmissionThumbnailUrlFromFullImage(
            sid = 10000001,
            fullImageUrl = "https://example.com/image.png",
        )

    assertNull(result)
  }

  @Test
  fun returnsNullForNonFaHostEvenIfPathLooksSimilar() {
    val result =
        deriveSubmissionThumbnailUrlFromFullImage(
            sid = 123456,
            fullImageUrl = "https://example.com/art/user/1665402309/1665402309.file.png",
        )

    assertNull(result)
  }
}
