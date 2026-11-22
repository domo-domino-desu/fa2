package me.domino.fa2.data.remote.fa.feed

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.remote.fa.core.FaHtmlDataSource
import me.domino.fa2.data.remote.fa.core.HtmlResponseResult
import me.domino.fa2.data.remote.fa.core.PageQueryCache
import me.domino.fa2.fake.InMemoryPageCacheDao
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.utils.FaUrls

/** FeedRepository 分页链路测试。 */
class FeedRepositoryPaginationTest {
  @Test
  fun loadPageByNextUrlSupportsSuccessFailureAndRetry() = runTest {
    val source = FeedScriptedHtmlDataSource()
    val repository = buildRepository(source)

    source.enqueue(
        url = FaUrls.submissions(),
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("live/gallery.html"),
                url = FaUrls.submissions(),
            ),
    )

    val first = repository.loadFirstPage()
    assertTrue(first is ResourceResult.Success)
    val firstPage = first.data
    val nextUrl = FaUrls.submissions(firstPage.submissions.last().id)

    source.enqueue(
        url = nextUrl,
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("live/gallery.html"),
                url = nextUrl,
            ),
    )
    val middle = repository.loadPageByNextUrl(nextUrl)
    assertTrue(middle is ResourceResult.Success)
    val middlePage = middle.data
    val lastUrl = FaUrls.submissions(firstPage.submissions.first().id)

    source.enqueue(
        url = lastUrl,
        response = HtmlResponseResult.Error(statusCode = 503, message = "temporary unavailable"),
    )
    val failedAppend = repository.loadPageByNextUrl(lastUrl)
    assertTrue(failedAppend is ResourceResult.Error)

    source.enqueue(
        url = lastUrl,
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("live/gallery.html"),
                url = lastUrl,
            ),
    )
    val retriedAppend = repository.loadPageByNextUrl(lastUrl)
    assertTrue(retriedAppend is ResourceResult.Success)
    assertTrue(retriedAppend.data.submissions.isNotEmpty())
  }

  @Test
  fun loadPageByNextUrlReturnsErrorForInvalidUrl() = runTest {
    val repository = buildRepository(FeedScriptedHtmlDataSource())
    val state = repository.loadPageByNextUrl("https://www.furaffinity.net/msg/submissions/invalid/")
    assertTrue(state is ResourceResult.Error)
  }

  private fun buildRepository(source: FaHtmlDataSource): FeedRepository {
    return FeedRepository(source, FeedParser(), PageQueryCache(InMemoryPageCacheDao()))
  }
}

/** 脚本化 HTML 数据源，用于控制请求返回。 */
private class FeedScriptedHtmlDataSource : FaHtmlDataSource {
  private val queueByUrl: MutableMap<String, ArrayDeque<HtmlResponseResult>> = mutableMapOf()

  fun enqueue(url: String?, response: HtmlResponseResult) {
    val key = url.orEmpty()
    queueByUrl.getOrPut(key) { ArrayDeque() }.addLast(response)
  }

  override suspend fun get(url: String): HtmlResponseResult {
    val queue = queueByUrl[url]
    if (queue == null || queue.isEmpty()) {
      return HtmlResponseResult.Error(statusCode = 500, message = "No scripted response for $url")
    }
    return queue.removeFirst()
  }
}
