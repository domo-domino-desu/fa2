package me.domino.fa2.ui.pages.submission

import kotlin.test.Test
import kotlin.test.assertNotEquals
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.util.FaUrls

class SubmissionRouteScreenKeyTest {
  @Test
  fun keyDiffersBySeedSeriesKey() {
    val first =
        SubmissionRouteScreen(
            initialSid = 1,
            holderTag = "submission-series:test",
            seedSubmissions = listOf(testSeedThumbnail(1)),
            seedSeriesKey = "series-a",
        )
    val second =
        SubmissionRouteScreen(
            initialSid = 1,
            holderTag = "submission-series:test",
            seedSubmissions = listOf(testSeedThumbnail(1)),
            seedSeriesKey = "series-b",
        )

    assertNotEquals(first.key, second.key)
  }

  @Test
  fun keyDiffersByHolderTag() {
    val first = SubmissionRouteScreen(initialSid = 1, holderTag = "holder-a")
    val second = SubmissionRouteScreen(initialSid = 1, holderTag = "holder-b")

    assertNotEquals(first.key, second.key)
  }
}

private fun testSeedThumbnail(sid: Int): SubmissionThumbnail =
    SubmissionThumbnail(
        id = sid,
        submissionUrl = FaUrls.submission(sid),
        title = "title-$sid",
        author = "author-$sid",
        thumbnailUrl = "https://t.furaffinity.net/$sid@300-0.jpg",
        thumbnailAspectRatio = 1f,
        categoryTag = "",
    )
