package me.domino.fa2.data.remote.fa.watchlist

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.utils.FaUrls

/** WatchlistParser 解析测试。 */
class WatchlistParserTest {
  @Test
  fun parsesWatchedByPageAndNextPageUrl() {
    val html = TestFixtures.read("live/watchers.html")
    val parser = WatchlistParser()

    val page = parser.parse(html = html, baseUrl = FaUrls.watchlistTo("user-alpha"))

    assertTrue(page.users.isNotEmpty())
    assertTrue(page.users.first().username.isNotBlank())
    assertTrue(page.users.first().displayName.isNotBlank())
    assertTrue(page.users.first().profileUrl.startsWith("https://www.furaffinity.net/user/"))
    assertTrue(page.nextPageUrl.orEmpty().contains("/watchlist/to/"))
    assertTrue(page.nextPageUrl.orEmpty().contains("page=2"))
  }

  @Test
  fun parsesCurrentEmptyWatchingList() {
    val page =
        WatchlistParser()
            .parse(TestFixtures.read("live/watching.html"), FaUrls.watchlistBy("fender"))
    assertTrue(page.users.isEmpty())
    assertNull(page.nextPageUrl)
  }

  @Test
  fun parsesAbsoluteUserLinksFromLocalFixture() {
    val html = TestFixtures.read("live/watchers.html")
    val parser = WatchlistParser()

    val page = parser.parse(html = html, baseUrl = FaUrls.watchlistTo("user-beta"))

    assertTrue(page.users.isNotEmpty())
    assertTrue(page.users.first().username.isNotBlank())
    assertTrue(page.users.first().displayName.isNotBlank())
    assertTrue(page.users.first().profileUrl.startsWith("https://www.furaffinity.net/user/"))
    assertTrue(page.nextPageUrl.orEmpty().contains("page=2"))
  }
}
