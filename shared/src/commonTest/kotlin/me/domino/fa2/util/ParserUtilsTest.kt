package me.domino.fa2.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import me.domino.fa2.fake.TestFixtures

class ParserUtilsTest {
  @Test
  fun skipsAvatarUrlsWhenAvatarMtimeIsMissing() {
    val html = TestFixtures.read("www.furaffinity.net:gallery:tiaamaito:.html")

    val avatars = ParserUtils.parseSubmissionAvatarUrls(html)

    assertFalse(avatars.values.any { value -> value.contains("/0/") })
  }

  @Test
  fun derivesThumbnailUrlFromFullImageUrl() {
    val result =
        ParserUtils.deriveSubmissionThumbnailUrlFromFullImage(
            sid = 49338772,
            fullImageUrl =
                "https://d.furaffinity.net/art/annetpeas/1665402309/1665402309.annetpeas_the_hookah_fa.png",
        )

    assertEquals("https://t.furaffinity.net/49338772@600-1665402309.jpg", result)
  }

  @Test
  fun derivesThumbnailUrlFromLegacyFacdnHost() {
    val result =
        ParserUtils.deriveSubmissionThumbnailUrlFromFullImage(
            sid = 14977134,
            fullImageUrl =
                "http://d.facdn.net/art/waccoon/1411105444/1411105444.waccoon_my_picture.jpg",
        )

    assertEquals("https://t.furaffinity.net/14977134@600-1411105444.jpg", result)
  }

  @Test
  fun derivesThumbnailUrlWhenFullUrlContainsQuery() {
    val result =
        ParserUtils.deriveSubmissionThumbnailUrlFromFullImage(
            sid = 52209828,
            fullImageUrl =
                "https://d.furaffinity.net/art/minmohere/1684537865/1684537865.minmohere_5-18-23blupee.png?token=abc",
        )

    assertEquals("https://t.furaffinity.net/52209828@600-1684537865.jpg", result)
  }

  @Test
  fun returnsNullWhenFullImageUrlDoesNotMatchPattern() {
    val result =
        ParserUtils.deriveSubmissionThumbnailUrlFromFullImage(
            sid = 49338772,
            fullImageUrl = "https://example.com/image.png",
        )

    assertNull(result)
  }

  @Test
  fun returnsNullForNonFaHostEvenIfPathLooksSimilar() {
    val result =
        ParserUtils.deriveSubmissionThumbnailUrlFromFullImage(
            sid = 123456,
            fullImageUrl = "https://example.com/art/user/1665402309/1665402309.file.png",
        )

    assertNull(result)
  }
}
