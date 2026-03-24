package me.domino.fa2.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.datasource.FavoritesDataSource
import me.domino.fa2.data.datasource.GalleryDataSource
import me.domino.fa2.data.datasource.SubmissionDataSource
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.data.network.endpoint.FavoriteEndpoint
import me.domino.fa2.data.network.endpoint.GalleryEndpoint
import me.domino.fa2.data.network.endpoint.SocialActionEndpoint
import me.domino.fa2.data.network.endpoint.SubmissionEndpoint
import me.domino.fa2.data.parser.GalleryParser
import me.domino.fa2.data.parser.SubmissionParser
import me.domino.fa2.data.store.GalleryStore
import me.domino.fa2.data.store.SubmissionStore
import me.domino.fa2.fake.InMemoryPageCacheDao
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.util.FaUrls

/** SubmissionRepository 详情链路测试。 */
class SubmissionRepositoryTest {
  @Test
  fun loadSubmissionDetailBySidParsesMetadataAndMedia() = runTest {
    val source = SubmissionScriptedHtmlDataSource()
    val repository = buildRepository(source)
    val targetSid = 49338772
    source.enqueue(
      url = FaUrls.submission(targetSid),
      response =
        HtmlResponseResult.Success(
          body = TestFixtures.read("www.furaffinity.net:view:49338772-nocomment.html"),
          url = FaUrls.submission(targetSid),
        ),
    )

    val state = repository.loadSubmissionDetailBySid(targetSid)
    assertTrue(state is PageState.Success)
    val detail = state.data
    assertEquals("The hookah", detail.title)
    assertEquals(769, detail.viewCount)
    assertEquals(65, detail.favoriteCount)
    assertEquals("1217 x 1280", detail.size)
    assertEquals("1.22 MB", detail.fileSize)
    assertTrue(detail.fullImageUrl.isNotBlank())
    assertTrue(detail.previewImageUrl.isNotBlank())
  }

  @Test
  fun loadSubmissionDetailByUrlUsesSidCacheKey() = runTest {
    val source = SubmissionScriptedHtmlDataSource()
    val repository = buildRepository(source)
    val targetSid = 48519387
    source.enqueue(
      url = FaUrls.submission(targetSid),
      response =
        HtmlResponseResult.Success(
          body = TestFixtures.read("www.furaffinity.net:view:48519387-comments.html"),
          url = FaUrls.submission(targetSid),
        ),
    )

    val state = repository.loadSubmissionDetailByUrl(FaUrls.submission(targetSid))
    assertTrue(state is PageState.Success)
    assertEquals(targetSid, state.data.id)
  }

  private fun buildRepository(source: FaHtmlDataSource): SubmissionRepository {
    val pageCacheDao = InMemoryPageCacheDao()
    val store =
      SubmissionStore(
        dataSource =
          SubmissionDataSource(endpoint = SubmissionEndpoint(source), parser = SubmissionParser()),
        pageCacheDao = pageCacheDao,
      )
    val galleryStore =
      GalleryStore(
        galleryDataSource =
          GalleryDataSource(endpoint = GalleryEndpoint(source), parser = GalleryParser()),
        favoritesDataSource =
          FavoritesDataSource(endpoint = FavoriteEndpoint(source), parser = GalleryParser()),
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
      return HtmlResponseResult.Error(statusCode = 500, message = "No scripted response for $url")
    }
    return queue.removeFirst()
  }
}
