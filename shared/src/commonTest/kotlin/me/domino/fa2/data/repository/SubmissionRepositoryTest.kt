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
import me.domino.fa2.data.network.endpoint.AttachmentDownloadPayload
import me.domino.fa2.data.network.endpoint.AttachmentDownloadResult
import me.domino.fa2.data.network.endpoint.AttachmentDownloadSource
import me.domino.fa2.data.network.endpoint.FavoritesEndpoint
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
import me.domino.fa2.util.attachmenttext.AttachmentTextFormat

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
    assertEquals("1665402309.annetpeas_the_hookah_fa.png", detail.downloadFileName)
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
    assertEquals("1660265303.terriniss_ауцпекн6г.jpg", state.data.downloadFileName)
  }

  @Test
  fun loadAttachmentTextParsesDownloadedTextFile() = runTest {
    val repository =
        buildRepository(
            source = SubmissionScriptedHtmlDataSource(),
            attachmentDownloadSource =
                FakeAttachmentDownloadSource(
                    AttachmentDownloadResult.Success(
                        AttachmentDownloadPayload(
                            bytes = "Hello\n\nWorld".encodeToByteArray(),
                            contentType = "text/plain",
                        )
                    )
                ),
        )

    val state =
        repository.loadAttachmentText(
            downloadUrl = "https://example.com/sample.txt",
            downloadFileName = "sample.txt",
        )

    assertTrue(state is PageState.Success)
    assertEquals(AttachmentTextFormat.TEXT, state.data.format)
    assertTrue(state.data.html.contains("<p>Hello</p>"))
  }

  @Test
  fun loadAttachmentTextReturnsChallengeState() = runTest {
    val repository =
        buildRepository(
            source = SubmissionScriptedHtmlDataSource(),
            attachmentDownloadSource =
                FakeAttachmentDownloadSource(AttachmentDownloadResult.Challenge(cfRay = "abc")),
        )

    val state =
        repository.loadAttachmentText(
            downloadUrl = "https://example.com/sample.txt",
            downloadFileName = "sample.txt",
        )

    assertEquals(PageState.CfChallenge, state)
  }

  private fun buildRepository(
      source: FaHtmlDataSource,
      attachmentDownloadSource: AttachmentDownloadSource? = null,
  ): SubmissionRepository {
    val pageCacheDao = InMemoryPageCacheDao()
    val store =
        SubmissionStore(
            dataSource =
                SubmissionDataSource(
                    endpoint = SubmissionEndpoint(source),
                    parser = SubmissionParser(),
                ),
            pageCacheDao = pageCacheDao,
        )
    val galleryStore =
        GalleryStore(
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
        attachmentDownloadSource = attachmentDownloadSource,
    )
  }
}

private class FakeAttachmentDownloadSource(private val result: AttachmentDownloadResult) :
    AttachmentDownloadSource {
  override suspend fun fetch(url: String, fileName: String): AttachmentDownloadResult = result
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
