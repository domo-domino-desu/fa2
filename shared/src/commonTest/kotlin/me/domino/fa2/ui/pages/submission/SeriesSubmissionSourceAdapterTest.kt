package me.domino.fa2.ui.pages.submission

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import me.domino.fa2.application.submissionseries.SubmissionSeriesResolvedSeries
import me.domino.fa2.application.submissionseries.SubmissionSeriesRule
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.repository.SubmissionDetailRepository
import me.domino.fa2.util.FaUrls

class SeriesSubmissionSourceAdapterTest {
  @Test
  fun numberedSeriesCanContinuePastTwentiethSubmission() =
      kotlinx.coroutines.test.runTest {
        val repository =
            FakeSeriesSubmissionRepository(
                details =
                    listOf(
                        testSubmission(11),
                        testSubmission(20),
                    )
            )
        val adapter =
            SeriesSubmissionSourceAdapter(
                repository = repository,
                series =
                    SubmissionSeriesResolvedSeries(
                        candidateKey = "numbered-series",
                        firstSid = 1,
                        firstSubmissionUrl = FaUrls.submission(1),
                        seedSubmissions = (1..10).map(::testThumbnail),
                        previousRequestKey = null,
                        nextRequestKey = FaUrls.submission(11),
                        rule = SubmissionSeriesRule.NUMBERED_BLOCKS,
                        orderedSubmissionUrls = (1..25).map(FaUrls::submission),
                    ),
            )

        val initialPage = adapter.loadInitialPage() as? PageState.Success
        val eleventhPage = adapter.loadNextPage(FaUrls.submission(11)) as? PageState.Success
        val twentiethPage = adapter.loadNextPage(FaUrls.submission(20)) as? PageState.Success

        assertNotNull(initialPage)
        assertNotNull(eleventhPage)
        assertNotNull(twentiethPage)
        assertEquals(FaUrls.submission(11), initialPage.data.nextRequestKey)
        assertEquals(FaUrls.submission(12), eleventhPage.data.nextRequestKey)
        assertEquals(FaUrls.submission(21), twentiethPage.data.nextRequestKey)
      }

  @Test
  fun prevNextSeriesDoesNotExposeAlreadyLoadedLoopTargets() =
      kotlinx.coroutines.test.runTest {
        val repository =
            FakeSeriesSubmissionRepository(
                details =
                    listOf(
                        testSubmission(
                            sid = 4,
                            descriptionHtml =
                                """
                                <p>
                                  <a href="/view/3/">PREVIOUS</a>
                                  <a href="/view/2/">NEXT</a>
                                </p>
                                """
                                    .trimIndent(),
                        ),
                        testSubmission(
                            sid = 9,
                            descriptionHtml =
                                """
                                <p>
                                  <a href="/view/8/">PREVIOUS</a>
                                  <a href="/view/10/">NEXT</a>
                                </p>
                                """
                                    .trimIndent(),
                        ),
                    )
            )
        val adapter =
            SeriesSubmissionSourceAdapter(
                repository = repository,
                series =
                    SubmissionSeriesResolvedSeries(
                        candidateKey = "linked-series",
                        firstSid = 1,
                        firstSubmissionUrl = FaUrls.submission(1),
                        seedSubmissions =
                            listOf(testThumbnail(1), testThumbnail(2), testThumbnail(3)),
                        previousRequestKey = null,
                        nextRequestKey = FaUrls.submission(4),
                        rule = SubmissionSeriesRule.PREV_NEXT_BLOCK,
                        orderedSubmissionUrls = emptyList(),
                    ),
            )
        val backwardAdapter =
            SeriesSubmissionSourceAdapter(
                repository = repository,
                series =
                    SubmissionSeriesResolvedSeries(
                        candidateKey = "linked-series-backward",
                        firstSid = 10,
                        firstSubmissionUrl = FaUrls.submission(10),
                        seedSubmissions =
                            listOf(testThumbnail(10), testThumbnail(11), testThumbnail(12)),
                        previousRequestKey = FaUrls.submission(9),
                        nextRequestKey = null,
                        rule = SubmissionSeriesRule.PREV_NEXT_BLOCK,
                        orderedSubmissionUrls = emptyList(),
                    ),
            )

        val nextPage = adapter.loadNextPage(FaUrls.submission(4)) as? PageState.Success
        val previousPage =
            backwardAdapter.loadPreviousPage(FaUrls.submission(9)) as? PageState.Success

        assertNotNull(nextPage)
        assertNotNull(previousPage)
        assertNull(nextPage.data.previousRequestKey)
        assertNull(nextPage.data.nextRequestKey)
        assertEquals(FaUrls.submission(8), previousPage.data.previousRequestKey)
        assertNull(previousPage.data.nextRequestKey)
      }
}

private class FakeSeriesSubmissionRepository(
    details: List<Submission>,
) : SubmissionDetailRepository {
  private val detailsByUrl = details.associateBy(Submission::submissionUrl)

  override suspend fun loadSubmissionDetailBySid(sid: Int): PageState<Submission> =
      loadSubmissionDetailByUrl(FaUrls.submission(sid))

  override suspend fun loadSubmissionDetailByUrl(url: String): PageState<Submission> =
      detailsByUrl[url]?.let { detail -> PageState.Success(detail) }
          ?: PageState.Error(IllegalStateException("Missing detail for $url"))
}

private fun testThumbnail(sid: Int): SubmissionThumbnail =
    SubmissionThumbnail(
        id = sid,
        submissionUrl = FaUrls.submission(sid),
        title = "title-$sid",
        author = "author-$sid",
        thumbnailUrl = "https://t.furaffinity.net/$sid@300-0.jpg",
        thumbnailAspectRatio = 1f,
        categoryTag = "",
    )

private fun testSubmission(
    sid: Int,
    descriptionHtml: String = "<p>submission-$sid</p>",
): Submission =
    Submission(
        id = sid,
        submissionUrl = FaUrls.submission(sid),
        title = "submission-$sid",
        author = "author-$sid",
        authorDisplayName = "Author $sid",
        timestampRaw = null,
        timestampNatural = "now",
        viewCount = 0,
        commentCount = 0,
        favoriteCount = 0,
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
        descriptionHtml = descriptionHtml,
    )
