package me.domino.fa2.data.remote.fa.submission

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.remote.fa.core.FaHtmlDataSource
import me.domino.fa2.data.remote.fa.core.HtmlResponseResult
import me.domino.fa2.data.remote.fa.core.PageQueryCache
import me.domino.fa2.fake.InMemoryPageCacheDao
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.fake.UnusedSocialActionSource
import me.domino.fa2.utils.FaUrls

/** SubmissionRepository 详情链路测试。 */
class SubmissionRepositoryTest {
  private val latestFixture = "live/submission.html"
  private val latestSid = 67119566

  @Test
  fun loadSubmissionDetailBySidParsesMetadataAndMedia() = runTest {
    val source = SubmissionScriptedHtmlDataSource()
    val repository = buildRepository(source)
    source.enqueue(
        url = FaUrls.submission(latestSid),
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read(latestFixture),
                url = FaUrls.submission(latestSid),
            ),
    )

    val state = repository.loadSubmissionDetailBySid(latestSid)
    assertTrue(state is ResourceResult.Success)
    val detail = state.data

    assertTrue(detail.fullImageUrl.isNotBlank())
    assertTrue(detail.previewImageUrl.isNotBlank())
  }

  @Test
  fun loadSubmissionDetailByUrlUsesSidCacheKey() = runTest {
    val source = SubmissionScriptedHtmlDataSource()
    val repository = buildRepository(source)
    source.enqueue(
        url = FaUrls.submission(latestSid),
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read(latestFixture),
                url = FaUrls.submission(latestSid),
            ),
    )

    val state = repository.loadSubmissionDetailByUrl(FaUrls.submission(latestSid))
    assertTrue(state is ResourceResult.Success)
    assertEquals(latestSid, state.data.id)
    assertTrue(state.data.downloadFileName.orEmpty().isNotBlank())
  }

  private fun buildRepository(source: FaHtmlDataSource): SubmissionRepository {
    return SubmissionRepository(
        html = source,
        parser = SubmissionParser(),
        queries = PageQueryCache(InMemoryPageCacheDao()),
        actions = UnusedSocialActionSource,
    )
  }
}

/** 脚本化 HTML 数据源，用于控制请求返回。 */
private class SubmissionScriptedHtmlDataSource : FaHtmlDataSource {
  private val queueByUrl: MutableMap<String, ArrayDeque<HtmlResponseResult>> = mutableMapOf()

  fun enqueue(url: String, response: HtmlResponseResult) {
    queueByUrl.getOrPut(url) { ArrayDeque() }.addLast(response)
  }

  override suspend fun get(url: String): HtmlResponseResult {
    val queue = queueByUrl[url]
    if (queue == null || queue.isEmpty()) {
      return HtmlResponseResult.Error(statusCode = 500, message = "No scripted response for $url")
    }
    return queue.removeFirst()
  }
}
