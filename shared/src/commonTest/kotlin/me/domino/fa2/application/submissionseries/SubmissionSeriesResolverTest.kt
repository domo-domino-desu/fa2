package me.domino.fa2.application.submissionseries

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.Submission
import me.domino.fa2.data.repository.SubmissionDetailRepository
import me.domino.fa2.util.FaUrls

@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionSeriesResolverTest {
  private val resolver = SubmissionSeriesResolver(repository = FakeSubmissionDetailRepository())

  @Test
  fun detectsPrevNextCandidateBeforeNumberedBlocks() {
    val sourceHtml =
        """
        <p><a href="/view/101/">PART 1</a></p>
        <p><a href="/view/102/">PART 2</a></p>
        <p><a href="/view/103/">PART 3</a></p>
        <p>
          <a href="/view/090/">PREVIOUS</a>
          <a href="/view/110/">NEXT</a>
        </p>
        """
            .trimIndent()

    val candidate =
        resolver.detectCandidate(sourceHtml = sourceHtml, baseUrl = FaUrls.submission(999))

    assertNotNull(candidate)
    assertEquals(SubmissionSeriesRule.PREV_NEXT_BLOCK, candidate.rule)
    assertEquals(FaUrls.submission(90), candidate.previousSubmissionUrl)
    assertEquals(FaUrls.submission(110), candidate.nextSubmissionUrl)
  }

  @Test
  fun detectsNavigationCandidateWhenOnlyPrevExistsAndDecoratedTextIsUsed() {
    val sourceHtml =
        """
        <p>
          <a href="/view/63200997/">&lt;&lt;&lt; PREV</a> |
          <a href="/view/63200968/">FIRST</a> |
          <a href="/view/63201022/">NEXT &gt;&gt;&gt;</a>
        </p>
        """
            .trimIndent()

    val candidate =
        resolver.detectCandidate(sourceHtml = sourceHtml, baseUrl = FaUrls.submission(63201008))

    assertNotNull(candidate)
    assertEquals(SubmissionSeriesRule.PREV_NEXT_BLOCK, candidate.rule)
    assertEquals(FaUrls.submission(63200997), candidate.previousSubmissionUrl)
    assertEquals(FaUrls.submission(63201022), candidate.nextSubmissionUrl)
    assertEquals(FaUrls.submission(63200968), candidate.firstSubmissionUrl)
  }

  @Test
  fun detectsNavigationCandidateWhenOnlyPrevExists() {
    val sourceHtml =
        """
        <p><a href="/view/701/">PREV</a></p>
        """
            .trimIndent()

    val candidate =
        resolver.detectCandidate(sourceHtml = sourceHtml, baseUrl = FaUrls.submission(702))

    assertNotNull(candidate)
    assertEquals(SubmissionSeriesRule.PREV_NEXT_BLOCK, candidate.rule)
    assertEquals(FaUrls.submission(701), candidate.previousSubmissionUrl)
    assertNull(candidate.nextSubmissionUrl)
  }

  @Test
  fun detectsStrictNumberedBlocksOnlyWhenThreeConsecutiveAndPart1Present() {
    val validHtml =
        """
        <p><a href="/view/201/">part 1</a></p>
        <p><a href="/view/202/">part 2</a></p>
        <p><a href="/view/203/">p3</a></p>
        """
            .trimIndent()
    val missingPartOneHtml =
        """
        <p><a href="/view/202/">part 2</a></p>
        <p><a href="/view/203/">part 3</a></p>
        <p><a href="/view/204/">part 4</a></p>
        """
            .trimIndent()
    val tooShortHtml =
        """
        <p><a href="/view/201/">part 1</a></p>
        <p><a href="/view/202/">part 2</a></p>
        """
            .trimIndent()

    val validCandidate = resolver.detectCandidate(validHtml, baseUrl = FaUrls.submission(999))

    assertNotNull(validCandidate)
    assertEquals(SubmissionSeriesRule.NUMBERED_BLOCKS, validCandidate.rule)
    assertEquals(FaUrls.submission(201), validCandidate.firstSubmissionUrl)
    assertEquals(
        listOf(FaUrls.submission(201), FaUrls.submission(202), FaUrls.submission(203)),
        validCandidate.orderedSubmissionUrls,
    )
    assertNull(resolver.detectCandidate(missingPartOneHtml, baseUrl = FaUrls.submission(999)))
    assertNull(resolver.detectCandidate(tooShortHtml, baseUrl = FaUrls.submission(999)))
  }

  @Test
  fun ignoresMixedJournalAndSubmissionLinksForNumberedBlocks() {
    val sourceHtml =
        """
        <p><a href="/view/301/">part 1</a></p>
        <p><a href="/journal/302/">part 2</a></p>
        <p><a href="/view/303/">part 3</a></p>
        """
            .trimIndent()

    val candidate =
        resolver.detectCandidate(sourceHtml = sourceHtml, baseUrl = FaUrls.submission(999))

    assertNull(candidate)
  }

  @Test
  fun resolvesSeriesFromExplicitFirstAndUsesNumberedOrder() = runTest {
    val repository =
        FakeSubmissionDetailRepository(
            details =
                listOf(
                    fakeSubmission(
                        sid = 401,
                        descriptionHtml =
                            """
                            <p><a href="/view/401/">part 1</a></p>
                            <p><a href="/view/402/">part 2</a></p>
                            <p><a href="/view/403/">part 3</a></p>
                            """
                                .trimIndent(),
                    ),
                    fakeSubmission(sid = 402, descriptionHtml = "<p>middle</p>"),
                    fakeSubmission(sid = 403, descriptionHtml = "<p>last</p>"),
                )
        )
    val localResolver = SubmissionSeriesResolver(repository)
    val candidate =
        localResolver.detectCandidate(
            """
            <p><a href="/view/401/">part 1</a></p>
            <p><a href="/view/402/">part 2</a></p>
            <p><a href="/view/403/">part 3</a></p>
            """
                .trimIndent(),
            baseUrl = FaUrls.submission(499),
        )

    val resolved = localResolver.resolveSeries(candidate!!)

    assertNotNull(resolved)
    assertEquals(listOf(401, 402, 403), resolved.seedSubmissions.map { it.id })
    assertEquals(401, resolved.firstSid)
    assertEquals(FaUrls.submission(401), resolved.firstSubmissionUrl)
    assertNull(resolved.previousRequestKey)
    assertNull(resolved.nextRequestKey)
    assertEquals(
        listOf(FaUrls.submission(401), FaUrls.submission(402), FaUrls.submission(403)),
        repository.requestedUrls,
    )
  }

  @Test
  fun resolvesSeriesByWalkingPreviousLinksWithThrottle() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val repository =
        FakeSubmissionDetailRepository(
            nowMs = { dispatcher.scheduler.currentTime },
            details =
                listOf(
                    fakeSubmission(
                        sid = 501,
                        descriptionHtml =
                            """
                            <p><a href="/view/501/">part 1</a></p>
                            <p><a href="/view/502/">part 2</a></p>
                            <p><a href="/view/503/">part 3</a></p>
                            """
                                .trimIndent(),
                    ),
                    fakeSubmission(
                        sid = 502,
                        descriptionHtml =
                            """
                            <p>
                              <a href="/view/501/">PREV</a>
                              <a href="/view/503/">NEXT</a>
                            </p>
                            """
                                .trimIndent(),
                    ),
                    fakeSubmission(
                        sid = 503,
                        descriptionHtml =
                            """
                            <p>
                              <a href="/view/502/">PREVIOUS</a>
                              <a href="/view/504/">NEXT</a>
                            </p>
                            """
                                .trimIndent(),
                    ),
                ),
        )
    val localResolver = SubmissionSeriesResolver(repository)
    val candidate =
        localResolver.detectCandidate(
            """
            <p>
              <a href="/view/502/">PREVIOUS</a>
              <a href="/view/504/">NEXT</a>
            </p>
            """
                .trimIndent(),
            baseUrl = FaUrls.submission(503),
        )

    val deferred =
        backgroundScope.launch {
          val resolved = localResolver.resolveSeries(candidate!!)
          assertNotNull(resolved)
          assertEquals(listOf(501, 502, 503), resolved.seedSubmissions.map { it.id })
        }

    runCurrent()
    assertEquals(listOf(FaUrls.submission(502)), repository.requestedUrls)
    assertEquals(listOf(0L), repository.requestTimesMs)

    advanceTimeBy(submissionSeriesRequestThrottleMs - 1)
    runCurrent()
    assertEquals(1, repository.requestedUrls.size)

    advanceTimeBy(1)
    runCurrent()
    assertEquals(listOf(FaUrls.submission(502), FaUrls.submission(501)), repository.requestedUrls)
    assertEquals(listOf(0L, submissionSeriesRequestThrottleMs), repository.requestTimesMs)

    advanceTimeBy(submissionSeriesRequestThrottleMs)
    runCurrent()
    assertEquals(
        listOf(
            FaUrls.submission(502),
            FaUrls.submission(501),
            FaUrls.submission(502),
        ),
        repository.requestedUrls,
    )

    advanceTimeBy(submissionSeriesRequestThrottleMs)
    runCurrent()
    assertEquals(
        listOf(
            0L,
            submissionSeriesRequestThrottleMs,
            submissionSeriesRequestThrottleMs * 2,
            submissionSeriesRequestThrottleMs * 3,
        ),
        repository.requestTimesMs,
    )
    deferred.join()
  }

  @Test
  fun failsWhenResolvedSeriesHasFewerThanThreeSubmissions() = runTest {
    val repository =
        FakeSubmissionDetailRepository(
            details =
                listOf(
                    fakeSubmission(
                        sid = 601,
                        descriptionHtml =
                            """
                            <p><a href="/view/601/">part 1</a></p>
                            <p><a href="/view/602/">part 2</a></p>
                            <p><a href="/view/603/">part 3</a></p>
                            """
                                .trimIndent(),
                    ),
                    fakeSubmission(sid = 602, descriptionHtml = "<p>second</p>"),
                )
        )
    val localResolver = SubmissionSeriesResolver(repository)
    val candidate =
        localResolver.detectCandidate(
            """
            <p><a href="/view/601/">part 1</a></p>
            <p><a href="/view/602/">part 2</a></p>
            <p><a href="/view/603/">part 3</a></p>
            """
                .trimIndent(),
            baseUrl = FaUrls.submission(699),
        )

    val resolved = localResolver.resolveSeries(candidate!!)

    assertNull(resolved)
  }

  @Test
  fun resolvesNumberedSeriesWithInitialSeedOnlyAndKeepsFullOrderedUrls() = runTest {
    val orderedSids = (1001..1025).toList()
    val numberedHtml =
        orderedSids.joinToString(separator = "\n") { sid ->
          """<p><a href="/view/$sid/">part ${sid - 1000}</a></p>"""
        }
    val repository =
        FakeSubmissionDetailRepository(
            details =
                buildList {
                  add(fakeSubmission(sid = 1001, descriptionHtml = numberedHtml))
                  addAll(
                      orderedSids.drop(1).take(seriesInitialReadyCount - 1).map { sid ->
                        fakeSubmission(sid = sid, descriptionHtml = "<p>part</p>")
                      }
                  )
                }
        )
    val localResolver = SubmissionSeriesResolver(repository)
    val candidate = localResolver.detectCandidate(numberedHtml, baseUrl = FaUrls.submission(1999))

    val resolved = localResolver.resolveSeries(candidate!!)

    assertNotNull(resolved)
    assertEquals(seriesInitialReadyCount, resolved.seedSubmissions.size)
    assertEquals(orderedSids.take(seriesInitialReadyCount), resolved.seedSubmissions.map { it.id })
    assertEquals(orderedSids.map(FaUrls::submission), resolved.orderedSubmissionUrls)
    assertEquals(FaUrls.submission(1011), resolved.nextRequestKey)
  }

  @Test
  fun resolvesPreviousOnlyChainBackToFirstPageWithoutExplicitFirstLink() = runTest {
    val repository =
        FakeSubmissionDetailRepository(
            details =
                listOf(
                    fakeSubmission(
                        sid = 801,
                        descriptionHtml =
                            """
                            <p><a href="/view/802/">NEXT</a></p>
                            """
                                .trimIndent(),
                    ),
                    fakeSubmission(
                        sid = 802,
                        descriptionHtml =
                            """
                            <p>
                              <a href="/view/801/">PREVIOUS</a>
                              <a href="/view/803/">NEXT</a>
                            </p>
                            """
                                .trimIndent(),
                    ),
                    fakeSubmission(
                        sid = 803,
                        descriptionHtml =
                            """
                            <p>
                              <a href="/view/802/">PREVIOUS</a>
                              <a href="/view/804/">NEXT</a>
                            </p>
                            """
                                .trimIndent(),
                    ),
                    fakeSubmission(
                        sid = 804,
                        descriptionHtml =
                            """
                            <p>
                              <a href="/view/803/">PREVIOUS</a>
                              <a href="/view/805/">NEXT</a>
                            </p>
                            """
                                .trimIndent(),
                    ),
                    fakeSubmission(
                        sid = 805,
                        descriptionHtml =
                            """
                            <p><a href="/view/804/">PREVIOUS</a></p>
                            """
                                .trimIndent(),
                    ),
                )
        )
    val localResolver = SubmissionSeriesResolver(repository)
    val candidate =
        localResolver.detectCandidate(
            """
            <p>
              <a href="/view/803/">PREVIOUS</a>
              <a href="/view/805/">NEXT</a>
            </p>
            """
                .trimIndent(),
            baseUrl = FaUrls.submission(804),
        )

    val resolved = localResolver.resolveSeries(candidate!!)

    assertNotNull(resolved)
    assertEquals(801, resolved.firstSid)
    assertEquals(listOf(801, 802, 803, 804, 805), resolved.seedSubmissions.map { it.id })
    assertNull(resolved.previousRequestKey)
    assertNull(resolved.nextRequestKey)
  }

  @Test
  fun failsSafelyWhenPreviousChainLoops() = runTest {
    val repository =
        FakeSubmissionDetailRepository(
            details =
                listOf(
                    fakeSubmission(
                        sid = 901,
                        descriptionHtml =
                            """
                            <p>
                              <a href="/view/902/">PREVIOUS</a>
                              <a href="/view/903/">NEXT</a>
                            </p>
                            """
                                .trimIndent(),
                    ),
                    fakeSubmission(
                        sid = 902,
                        descriptionHtml =
                            """
                            <p>
                              <a href="/view/901/">PREVIOUS</a>
                              <a href="/view/903/">NEXT</a>
                            </p>
                            """
                                .trimIndent(),
                    ),
                    fakeSubmission(
                        sid = 903,
                        descriptionHtml =
                            """
                            <p><a href="/view/901/">PREVIOUS</a></p>
                            """
                                .trimIndent(),
                    ),
                )
        )
    val localResolver = SubmissionSeriesResolver(repository)
    val candidate =
        localResolver.detectCandidate(
            """
            <p><a href="/view/901/">PREVIOUS</a></p>
            """
                .trimIndent(),
            baseUrl = FaUrls.submission(903),
        )

    val resolved = localResolver.resolveSeries(candidate!!)

    assertNull(resolved)
    assertTrue(repository.requestedUrls.size <= seriesBacktrackLimit + 1)
  }
}

private class FakeSubmissionDetailRepository(
    private val nowMs: () -> Long = { 0L },
    details: List<Submission> = emptyList(),
) : SubmissionDetailRepository {
  private val byUrl = details.associateBy { it.submissionUrl }.toMutableMap()
  val requestedUrls: MutableList<String> = mutableListOf()
  val requestTimesMs: MutableList<Long> = mutableListOf()

  override suspend fun loadSubmissionDetailBySid(sid: Int): PageState<Submission> =
      loadSubmissionDetailByUrl(FaUrls.submission(sid))

  override suspend fun loadSubmissionDetailByUrl(url: String): PageState<Submission> {
    val normalizedUrl = FaUrls.submission(url.substringAfter("/view/").substringBefore('/').toInt())
    requestedUrls += normalizedUrl
    requestTimesMs += nowMs()
    return byUrl[normalizedUrl]?.let { detail -> PageState.Success(detail) }
        ?: PageState.Error(IllegalStateException("Missing url=$normalizedUrl"))
  }
}

private fun fakeSubmission(
    sid: Int,
    descriptionHtml: String,
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
