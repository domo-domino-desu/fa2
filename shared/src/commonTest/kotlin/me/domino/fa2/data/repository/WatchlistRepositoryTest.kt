package me.domino.fa2.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.datasource.WatchlistDataSource
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistUser
import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.data.network.endpoint.WatchlistEndpoint
import me.domino.fa2.data.parser.WatchlistParser
import me.domino.fa2.data.store.WatchlistStore
import me.domino.fa2.fake.InMemoryPageCacheDao
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.util.FaUrls

/** WatchlistRepository 链路测试。 */
class WatchlistRepositoryTest {
  @Test
  fun loadUsesCacheAndRefreshForcesRefetch() = runTest {
    val source = WatchlistScriptedHtmlDataSource()
    val repository = buildRepository(source)
    val watchedByUrl = FaUrls.watchlistTo("terriniss")

    source.enqueue(
        url = watchedByUrl,
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("www.furaffinity.net:watchlist:to:terriniss.html"),
                url = watchedByUrl,
            ),
    )

    val first =
        repository.loadWatchlistPage(
            username = "terriniss",
            category = WatchlistCategory.WatchedBy,
            nextPageUrl = null,
        )
    assertTrue(first is PageState.Success)
    assertEquals(1, source.requestCount(watchedByUrl))

    val second =
        repository.loadWatchlistPage(
            username = "terriniss",
            category = WatchlistCategory.WatchedBy,
            nextPageUrl = null,
        )
    assertTrue(second is PageState.Success)
    assertEquals(1, source.requestCount(watchedByUrl))

    source.enqueue(
        url = watchedByUrl,
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("www.furaffinity.net:watchlist:to:razithedragon.html"),
                url = watchedByUrl,
            ),
    )

    val refreshed =
        repository.refreshWatchlistFirstPage(
            username = "terriniss",
            category = WatchlistCategory.WatchedBy,
        )
    assertTrue(refreshed is PageState.Success)
    assertEquals(2, source.requestCount(watchedByUrl))
  }

  @Test
  fun categoriesUseIndependentCaches() = runTest {
    val source = WatchlistScriptedHtmlDataSource()
    val repository = buildRepository(source)
    val watchedByUrl = FaUrls.watchlistTo("terriniss")
    val watchingUrl = FaUrls.watchlistBy("terriniss")

    source.enqueue(
        url = watchedByUrl,
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("www.furaffinity.net:watchlist:to:terriniss.html"),
                url = watchedByUrl,
            ),
    )
    source.enqueue(
        url = watchingUrl,
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("www.furaffinity.net:watchlist:by:terriniss.html"),
                url = watchingUrl,
            ),
    )

    val watchedBy =
        repository.loadWatchlistPage(
            username = "terriniss",
            category = WatchlistCategory.WatchedBy,
            nextPageUrl = null,
        )
    val watching =
        repository.loadWatchlistPage(
            username = "terriniss",
            category = WatchlistCategory.Watching,
            nextPageUrl = null,
        )
    assertTrue(watchedBy is PageState.Success)
    assertTrue(watching is PageState.Success)
    assertEquals(1, source.requestCount(watchedByUrl))
    assertEquals(1, source.requestCount(watchingUrl))

    val watchedByCached =
        repository.loadWatchlistPage(
            username = "terriniss",
            category = WatchlistCategory.WatchedBy,
            nextPageUrl = null,
        )
    assertTrue(watchedByCached is PageState.Success)
    assertEquals(1, source.requestCount(watchedByUrl))
    assertEquals(1, source.requestCount(watchingUrl))
  }

  @Test
  fun loadAllWatchlistUsersAggregatesPagesAndDeduplicates() = runTest {
    val source = WatchlistScriptedHtmlDataSource()
    val repository = buildRepository(source)
    val firstUrl = FaUrls.watchlistBy("me")
    val nextUrl = "${FaUrls.watchlistBy("me")}?page=2"

    source.enqueue(
        url = firstUrl,
        response =
            HtmlResponseResult.Success(
                body =
                    buildWatchlistHtml(
                        users =
                            listOf(
                                watchUser("alpha", "Alpha"),
                                watchUser("beta", "Beta"),
                            ),
                        nextPage = "2",
                    ),
                url = firstUrl,
            ),
    )
    source.enqueue(
        url = nextUrl,
        response =
            HtmlResponseResult.Success(
                body =
                    buildWatchlistHtml(
                        users =
                            listOf(
                                watchUser("beta", "Beta"),
                                watchUser("gamma", "Gamma"),
                            ),
                    ),
                url = nextUrl,
            ),
    )

    val result =
        repository.loadAllWatchlistUsers(
            username = "me",
            category = WatchlistCategory.Watching,
        )

    val success = assertIs<PageState.Success<List<WatchlistUser>>>(result)
    assertEquals(listOf("alpha", "beta", "gamma"), success.data.map { it.username })
    assertEquals(1, source.requestCount(firstUrl))
    assertEquals(1, source.requestCount(nextUrl))
  }

  @Test
  fun loadAllWatchlistUsersStopsOnError() = runTest {
    val source = WatchlistScriptedHtmlDataSource()
    val repository = buildRepository(source)
    val firstUrl = FaUrls.watchlistBy("me")
    val nextUrl = "${FaUrls.watchlistBy("me")}?page=2"

    source.enqueue(
        url = firstUrl,
        response =
            HtmlResponseResult.Success(
                body =
                    buildWatchlistHtml(users = listOf(watchUser("alpha", "Alpha")), nextPage = "2"),
                url = firstUrl,
            ),
    )
    source.enqueue(
        url = nextUrl,
        response = HtmlResponseResult.Error(statusCode = 500, message = "boom"),
    )

    val result =
        repository.loadAllWatchlistUsers(
            username = "me",
            category = WatchlistCategory.Watching,
        )

    val error = assertIs<PageState.Error>(result)
    assertTrue(error.exception.message.orEmpty().contains("boom"))
  }

  private fun buildRepository(source: FaHtmlDataSource): WatchlistRepository {
    val store =
        WatchlistStore(
            dataSource =
                WatchlistDataSource(
                    endpoint = WatchlistEndpoint(source),
                    parser = WatchlistParser(),
                ),
            pageCacheDao = InMemoryPageCacheDao(),
        )
    return WatchlistRepository(store)
  }
}

private fun watchUser(username: String, displayName: String): WatchlistUser =
    WatchlistUser(
        username = username,
        displayName = displayName,
        profileUrl = "https://www.furaffinity.net/user/$username/",
    )

private fun buildWatchlistHtml(
    users: List<WatchlistUser>,
    nextPage: String? = null,
): String = buildString {
  append("<html><head><title>Watchlist</title></head><body>")
  append("<div class='watch-list-items'>")
  users.forEach { user ->
    append("<a href='/user/${user.username}/'>")
    append("<span class='c-usernameBlockSimple__displayName'>${user.displayName}</span>")
    append("</a>")
  }
  append("</div>")
  if (nextPage != null) {
    append("<div class='watchlist-navigation'>")
    append("<form action='/watchlist/by/me/'>")
    append("<input name='page' value='$nextPage' />")
    append("<button>Next</button>")
    append("</form>")
    append("</div>")
  }
  append("</body></html>")
}

/** 脚本化 HTML 数据源，用于控制请求返回。 */
private class WatchlistScriptedHtmlDataSource : FaHtmlDataSource {
  private val queueByUrl: MutableMap<String, ArrayDeque<HtmlResponseResult>> = mutableMapOf()
  private val requestCountByUrl: MutableMap<String, Int> = mutableMapOf()

  fun enqueue(url: String, response: HtmlResponseResult) {
    queueByUrl.getOrPut(url) { ArrayDeque() }.addLast(response)
  }

  fun requestCount(url: String): Int = requestCountByUrl[url] ?: 0

  override suspend fun get(url: String): HtmlResponseResult {
    requestCountByUrl[url] = (requestCountByUrl[url] ?: 0) + 1
    val queue = queueByUrl[url]
    if (queue == null || queue.isEmpty()) {
      return HtmlResponseResult.Error(
          statusCode = 500,
          message = "No scripted response for $url",
      )
    }
    return queue.removeFirst()
  }
}
