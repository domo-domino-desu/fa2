package me.domino.fa2.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.util.FaUrls

/** WatchlistParser 解析测试。 */
class WatchlistParserTest {
  @Test
  fun parsesWatchedByPageAndNextPageUrl() {
    val html = TestFixtures.read("www.furaffinity.net:watchlist:to:user-alpha.html")
    val parser = WatchlistParser()

    val page = parser.parse(html = html, baseUrl = FaUrls.watchlistTo("user-alpha"))

    assertTrue(page.users.isNotEmpty())
    assertEquals("user-0002", page.users.first().username)
    assertEquals("User 0002", page.users.first().displayName)
    assertTrue(page.users.first().profileUrl.startsWith("https://www.furaffinity.net/user/"))
    assertTrue(page.nextPageUrl.orEmpty().contains("/watchlist/to/artist-alpha"))
    assertTrue(page.nextPageUrl.orEmpty().contains("page=2"))
  }

  @Test
  fun parsesWatchingPageWithoutNextPage() {
    val html = TestFixtures.read("www.furaffinity.net:watchlist:by:user-alpha.html")
    val parser = WatchlistParser()

    val page = parser.parse(html = html, baseUrl = FaUrls.watchlistBy("user-alpha"))

    assertTrue(page.users.isNotEmpty())
    assertEquals("user-0003", page.users.first().username)
    assertEquals("User 0003", page.users.first().displayName)
    assertNull(page.nextPageUrl)
  }

  @Test
  fun parsesAbsoluteUserLinksFromLocalFixture() {
    val html = TestFixtures.read("www.furaffinity.net:watchlist:to:user-beta.html")
    val parser = WatchlistParser()

    val page = parser.parse(html = html, baseUrl = FaUrls.watchlistTo("user-beta"))

    assertTrue(page.users.isNotEmpty())
    assertEquals("user-0001", page.users.first().username)
    assertEquals("User 0001", page.users.first().displayName)
    assertTrue(
        page.users.first().profileUrl.startsWith("https://www.furaffinity.net/user/user-0001")
    )
    assertTrue(page.nextPageUrl.orEmpty().contains("page=2"))
  }
}
