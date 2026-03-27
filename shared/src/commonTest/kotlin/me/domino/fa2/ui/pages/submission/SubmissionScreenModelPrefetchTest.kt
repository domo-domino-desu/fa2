package me.domino.fa2.ui.pages.submission

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.domain.attachmenttext.AttachmentTextDocument
import me.domino.fa2.domain.attachmenttext.AttachmentTextProgress
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.parseSubmissionSid

@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionScreenModelPrefetchTest {
  private val dispatcher = StandardTestDispatcher()

  @BeforeTest
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun computesPrefetchWindowIndices() {
    assertEquals(
        listOf(1, 2, 3),
        computeSubmissionPrefetchIndices(currentIndex = 0, lastIndex = 3),
    )
    assertEquals(
        listOf(3, 4, 5, 1),
        computeSubmissionPrefetchIndices(currentIndex = 2, lastIndex = 5),
    )
    assertEquals(listOf(4), computeSubmissionPrefetchIndices(currentIndex = 5, lastIndex = 5))
  }

  @Test
  fun prefetchesNext3AndPrevious1AfterDebounce() =
      runTest(dispatcher.scheduler) {
        val items = (1..8).map { sid -> testThumbnail(sid) }
        val detailSource = RecordingDetailSource()
        createSubmissionScreenModelForTest(
            initialSid = 4,
            items = items,
            submissionSource = detailSource,
            translationService = createTestSubmissionTranslationService(),
        )

        runCurrent()
        assertEquals(emptyList(), detailSource.sidPrefetchRequests)

        advanceTimeBy(pagerPrefetchDebounceMs - 1)
        runCurrent()
        assertEquals(emptyList(), detailSource.sidPrefetchRequests)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(5, 6, 7, 3), detailSource.sidPrefetchRequests)
      }
}

private fun testThumbnail(sid: Int): SubmissionThumbnail =
    SubmissionThumbnail(
        id = sid,
        submissionUrl = FaUrls.submission(sid),
        title = "title-$sid",
        author = "author",
        authorAvatarUrl = "",
        thumbnailUrl = "https://t.furaffinity.net/$sid@400-0.jpg",
        thumbnailAspectRatio = 1f,
        categoryTag = "c_all",
    )

private class RecordingDetailSource : SubmissionPagerDetailSource {
  val sidPrefetchRequests: MutableList<Int> = mutableListOf()

  override suspend fun loadBySid(sid: Int): PageState<Submission> {
    sidPrefetchRequests += sid
    return PageState.Success(testSubmission(sid))
  }

  override suspend fun loadByUrl(url: String): PageState<Submission> {
    val sid =
        parseSubmissionSid(url)
            ?: return PageState.Error(IllegalArgumentException("Invalid submission url: $url"))
    return PageState.Success(testSubmission(sid))
  }

  override suspend fun loadAttachmentText(
      downloadUrl: String,
      downloadFileName: String,
      onProgress: (AttachmentTextProgress) -> Unit,
  ): PageState<AttachmentTextDocument> =
      PageState.Error(IllegalStateException("No attachment parsing expected in this test"))

  override suspend fun toggleFavorite(sid: Int, actionUrl: String): PageState<Unit> =
      PageState.Success(Unit)

  override suspend fun blockTag(
      sid: Int,
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): PageState<Unit> = PageState.Success(Unit)
}

private fun testSubmission(sid: Int): Submission =
    Submission(
        id = sid,
        submissionUrl = FaUrls.submission(sid),
        title = "submission-$sid",
        author = "author-$sid",
        authorDisplayName = "Author $sid",
        timestampRaw = null,
        timestampNatural = "now",
        viewCount = 1,
        commentCount = 2,
        favoriteCount = 3,
        isFavorited = false,
        favoriteActionUrl = "",
        rating = "General",
        category = "Category",
        type = "Type",
        species = "Species",
        size = "100x100",
        fileSize = "100 KB",
        keywords = emptyList(),
        previewImageUrl = "https://d.furaffinity.net/art/test/$sid-preview.jpg",
        fullImageUrl = "https://d.furaffinity.net/art/test/$sid-full.jpg",
        downloadUrl = null,
        aspectRatio = 1f,
        descriptionHtml = "",
    )
