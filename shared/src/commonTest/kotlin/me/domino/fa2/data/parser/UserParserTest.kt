package me.domino.fa2.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.util.FaUrls

/** UserParser 解析测试。 */
class UserParserTest {
  @Test
  fun parsesUserHeaderFromHomePage() {
    val html = TestFixtures.read("www.furaffinity.net:user:terriniss.html")
    val parser = UserParser()

    val header = parser.parse(html = html, url = FaUrls.user("terriniss"))

    assertEquals("terriniss", header.username.lowercase())
    assertTrue(header.displayName.isNotBlank())
    assertTrue(header.registeredAt.isNotBlank())
    assertTrue(header.profileHtml.contains("bbcode"))
    assertTrue(header.isWatching)
    assertTrue(
        header.watchActionUrl.contains("/watch/") || header.watchActionUrl.contains("/unwatch/")
    )
    assertEquals(2257, header.watchedByCount)
    assertEquals(120, header.watchingCount)
    assertTrue(header.watchedByListUrl.contains("/watchlist/to/terriniss"))
    assertTrue(header.watchingListUrl.contains("/watchlist/by/terriniss"))
  }

  @Test
  fun mapsSystemErrorToReadableException() {
    val html = TestFixtures.read("www.furaffinity.net:user:username-system-error.html")
    val parser = UserParser()

    val error =
        assertFailsWith<IllegalStateException> {
          parser.parse(html = html, url = FaUrls.user("unknown"))
        }
    assertTrue(error.message.orEmpty().contains("cannot be found", ignoreCase = true))
  }

  @Test
  fun mapsDisabledUserToReadableException() {
    val html = TestFixtures.read("www.furaffinity.net:user:disabled-user-message.html")
    val parser = UserParser()

    val error =
        assertFailsWith<IllegalStateException> {
          parser.parse(html = html, url = FaUrls.user("disabled"))
        }
    assertTrue(error.message.orEmpty().contains("disabled", ignoreCase = true))
  }

  @Test
  fun mapsPendingDeletionToReadableException() {
    val html = TestFixtures.read("www.furaffinity.net:user:pending-deletion-message.html")
    val parser = UserParser()

    val error =
        assertFailsWith<IllegalStateException> {
          parser.parse(html = html, url = FaUrls.user("pending"))
        }
    assertTrue(error.message.orEmpty().contains("pending deletion", ignoreCase = true))
  }

  @Test
  fun parsesProfileBannerFromBannerImageNode() {
    val html = TestFixtures.read("www.furaffinity.net:journal:10882268-disabled-comments.html")
    val parser = UserParser()

    val header = parser.parse(html = html, url = FaUrls.user("fender"))

    assertTrue(header.profileBannerUrl.startsWith("https://d.furaffinity.net/"))
    assertTrue(header.profileBannerUrl.contains("/profile_banner.jpg"))
  }
}
