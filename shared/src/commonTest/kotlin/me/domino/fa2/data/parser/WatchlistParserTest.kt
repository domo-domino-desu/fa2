package me.domino.fa2.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.util.FaUrls

/**
 * WatchlistParser 解析测试。
 */
class WatchlistParserTest {
    @Test
    fun parsesWatchedByPageAndNextPageUrl() {
        val html = TestFixtures.read("www.furaffinity.net:watchlist:to:terriniss.html")
        val parser = WatchlistParser()

        val page = parser.parse(
            html = html,
            baseUrl = FaUrls.watchlistTo("terriniss"),
        )

        assertTrue(page.users.isNotEmpty())
        assertEquals("-cy-", page.users.first().username)
        assertEquals("-Cy-", page.users.first().displayName)
        assertTrue(page.users.first().profileUrl.startsWith("https://www.furaffinity.net/user/"))
        assertTrue(page.nextPageUrl.orEmpty().contains("/watchlist/to/terriniss"))
        assertTrue(page.nextPageUrl.orEmpty().contains("page=2"))
    }

    @Test
    fun parsesWatchingPageWithoutNextPage() {
        val html = TestFixtures.read("www.furaffinity.net:watchlist:by:terriniss.html")
        val parser = WatchlistParser()

        val page = parser.parse(
            html = html,
            baseUrl = FaUrls.watchlistBy("terriniss"),
        )

        assertTrue(page.users.isNotEmpty())
        assertEquals("-haunter-", page.users.first().username)
        assertEquals("-Haunter-", page.users.first().displayName)
        assertNull(page.nextPageUrl)
    }

    @Test
    fun parsesAbsoluteUserLinksFromLocalFixture() {
        val html = TestFixtures.read("www.furaffinity.net:watchlist:to:razithedragon.html")
        val parser = WatchlistParser()

        val page = parser.parse(
            html = html,
            baseUrl = FaUrls.watchlistTo("razithedragon"),
        )

        assertTrue(page.users.isNotEmpty())
        assertEquals("2glasseyes", page.users.first().username)
        assertEquals("2Glasseyes", page.users.first().displayName)
        assertTrue(page.users.first().profileUrl.startsWith("https://www.furaffinity.net/user/2glasseyes"))
        assertTrue(page.nextPageUrl.orEmpty().contains("page=2"))
    }
}

