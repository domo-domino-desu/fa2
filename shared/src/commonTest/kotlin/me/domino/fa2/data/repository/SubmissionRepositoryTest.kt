package me.domino.fa2.data.fa.submission

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.fa.core.FaHtmlDataSource
import me.domino.fa2.data.fa.core.HtmlResponseResult
import me.domino.fa2.data.fa.favorites.FavoritesDataSource
import me.domino.fa2.data.fa.favorites.FavoritesEndpoint
import me.domino.fa2.data.fa.gallery.GalleryDataSource
import me.domino.fa2.data.fa.gallery.GalleryEndpoint
import me.domino.fa2.data.fa.gallery.GalleryPageCache
import me.domino.fa2.data.fa.gallery.GalleryParser
import me.domino.fa2.data.fa.social.SocialActionEndpoint
import me.domino.fa2.data.model.PageState
import me.domino.fa2.fake.InMemoryPageCacheDao
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.utils.FaUrls

/** SubmissionRepository 详情链路测试。 */
class SubmissionRepositoryTest {
  private val latestFixture = "www.furaffinity.net:view:20000009.html"
  private val latestSid = 20000009

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
    assertTrue(state is PageState.Success)
    val detail = state.data
    assertEquals("Sanitized Latest Submission", detail.title)
    assertEquals(657, detail.viewCount)
    assertEquals(126, detail.favoriteCount)
    assertEquals("2351 x 1567", detail.size)
    assertEquals("3.19 MB", detail.fileSize)
    assertTrue(detail.fullImageUrl.isNotBlank())
    assertTrue(detail.previewImageUrl.isNotBlank())
    assertEquals("1700000009.artist-alpha_sanitized_latest_submission.png", detail.downloadFileName)
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
    assertTrue(state is PageState.Success)
    assertEquals(latestSid, state.data.id)
    assertEquals(
        "1700000009.artist-alpha_sanitized_latest_submission.png",
        state.data.downloadFileName,
    )
  }

  private fun buildRepository(source: FaHtmlDataSource): SubmissionRepository {
    val pageCacheDao = InMemoryPageCacheDao()
    val store =
        SubmissionPageCache(
            dataSource =
                SubmissionDataSource(
                    endpoint = SubmissionEndpoint(source),
                    parser = SubmissionParser(),
                ),
            pageCacheDao = pageCacheDao,
        )
    val galleryStore =
        GalleryPageCache(
            galleryDataSource =
                GalleryDataSource(endpoint = GalleryEndpoint(source), parser = GalleryParser()),
            favoritesDataSource =
                FavoritesDataSource(
                    endpoint = FavoritesEndpoint(source),
                    parser = GalleryParser(),
                ),
            pageCacheDao = pageCacheDao,
        )
    return SubmissionRepository(
        submissionStore = store,
        socialActionEndpoint = SocialActionEndpoint(source),
        galleryStore = galleryStore,
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
      return HtmlResponseResult.Error(
          statusCode = 500,
          message = "No scripted response for $url",
      )
    }
    return queue.removeFirst()
  }
}
