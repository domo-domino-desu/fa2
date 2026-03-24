package me.domino.fa2.data.repository

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.datasource.FeedDataSource
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.data.network.endpoint.FeedEndpoint
import me.domino.fa2.data.parser.FeedParser
import me.domino.fa2.data.store.FeedStore
import me.domino.fa2.fake.InMemoryPageCacheDao
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.util.FaUrls

/**
 * FeedRepository 分页链路测试。
 */
class FeedRepositoryPaginationTest {
    @Test
    fun loadPageByNextUrlSupportsSuccessFailureAndRetry() = runTest {
        val source = FeedScriptedHtmlDataSource()
        val repository = buildRepository(source)

        source.enqueue(
            url = FaUrls.submissions(),
            response = HtmlResponseResult.Success(
                body = TestFixtures.read("www.furaffinity.net:msg:submissions-firstpage.html"),
                url = FaUrls.submissions(),
            ),
        )

        val first = repository.loadFirstPage()
        assertTrue(first is PageState.Success)
        val firstPage = first.data
        assertNotNull(firstPage.nextPageUrl)

        source.enqueue(
            url = firstPage.nextPageUrl,
            response = HtmlResponseResult.Success(
                body = TestFixtures.read("www.furaffinity.net:msg:submissions-middlepage.html"),
                url = firstPage.nextPageUrl,
            ),
        )
        val middle = repository.loadPageByNextUrl(firstPage.nextPageUrl)
        assertTrue(middle is PageState.Success)
        val middlePage = middle.data
        assertNotNull(middlePage.nextPageUrl)

        source.enqueue(
            url = middlePage.nextPageUrl,
            response = HtmlResponseResult.Error(
                statusCode = 503,
                message = "temporary unavailable",
            ),
        )
        val failedAppend = repository.loadPageByNextUrl(middlePage.nextPageUrl)
        assertTrue(failedAppend is PageState.Error)

        source.enqueue(
            url = middlePage.nextPageUrl,
            response = HtmlResponseResult.Success(
                body = TestFixtures.read("www.furaffinity.net:msg:submissions-lastpage.html"),
                url = middlePage.nextPageUrl,
            ),
        )
        val retriedAppend = repository.loadPageByNextUrl(middlePage.nextPageUrl)
        assertTrue(retriedAppend is PageState.Success)
        assertNull(retriedAppend.data.nextPageUrl)
    }

    @Test
    fun loadPageByNextUrlReturnsErrorForInvalidUrl() = runTest {
        val repository = buildRepository(FeedScriptedHtmlDataSource())
        val state = repository.loadPageByNextUrl("https://www.furaffinity.net/msg/submissions/invalid/")
        assertTrue(state is PageState.Error)
    }

    private fun buildRepository(source: FaHtmlDataSource): FeedRepository {
        val feedStore = FeedStore(
            dataSource = FeedDataSource(
                endpoint = FeedEndpoint(source),
                parser = FeedParser(),
            ),
            pageCacheDao = InMemoryPageCacheDao(),
        )
        return FeedRepository(feedStore)
    }
}

/**
 * 脚本化 HTML 数据源，用于控制请求返回。
 */
private class FeedScriptedHtmlDataSource : FaHtmlDataSource {
    private val queueByUrl: MutableMap<String, ArrayDeque<HtmlResponseResult>> = mutableMapOf()

    fun enqueue(
        url: String?,
        response: HtmlResponseResult,
    ) {
        val key = url.orEmpty()
        queueByUrl.getOrPut(key) { ArrayDeque() }.addLast(response)
    }

    override suspend fun get(url: String): HtmlResponseResult {
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
