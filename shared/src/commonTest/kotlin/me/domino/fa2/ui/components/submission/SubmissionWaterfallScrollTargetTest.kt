package me.domino.fa2.ui.components.submission

import kotlin.test.Test
import kotlin.test.assertEquals
import me.domino.fa2.ui.pages.submission.WaterfallScrollRequest

class SubmissionWaterfallScrollTargetTest {
  @Test
  fun prefersLowestVisibleLeadingCandidateAcrossFullWindow() {
    val targetSid =
        resolveWaterfallScrollTargetSid(
            request =
                WaterfallScrollRequest(
                    sid = 100,
                    version = 1,
                    targetPageLeadingSids = listOf(100, 101, 102, 103, 104, 105),
                ),
            itemIds = linkedSetOf(95, 96, 100, 101, 102, 103, 104, 105),
            visibleItems =
                listOf(
                    WaterfallVisibleItem(sid = 95, laneOffsetX = 0, itemOffsetY = -120),
                    WaterfallVisibleItem(sid = 96, laneOffsetX = 240, itemOffsetY = -40),
                    WaterfallVisibleItem(sid = 100, laneOffsetX = 0, itemOffsetY = 12),
                    WaterfallVisibleItem(sid = 103, laneOffsetX = 240, itemOffsetY = 168),
                ),
        )

    assertEquals(103, targetSid)
  }

  @Test
  fun fallsBackToLastLoadedCandidateWithinVisibleLaneWindow() {
    val targetSid =
        resolveWaterfallScrollTargetSid(
            request =
                WaterfallScrollRequest(
                    sid = 100,
                    version = 1,
                    targetPageLeadingSids = listOf(100, 101, 102, 103),
                ),
            itemIds = linkedSetOf(90, 91, 100, 101, 102, 103, 104),
            visibleItems =
                listOf(
                    WaterfallVisibleItem(sid = 90, laneOffsetX = 0, itemOffsetY = -120),
                    WaterfallVisibleItem(sid = 91, laneOffsetX = 240, itemOffsetY = -48),
                ),
        )

    assertEquals(101, targetSid)
  }

  @Test
  fun fallsBackToPrimarySidWhenNoCandidatesAreLoaded() {
    val targetSid =
        resolveWaterfallScrollTargetSid(
            request =
                WaterfallScrollRequest(
                    sid = 100,
                    version = 1,
                    targetPageLeadingSids = listOf(100, 101, 102, 103),
                ),
            itemIds = linkedSetOf(90, 91, 92),
            visibleItems =
                listOf(
                    WaterfallVisibleItem(sid = 90, laneOffsetX = 0, itemOffsetY = -120),
                    WaterfallVisibleItem(sid = 91, laneOffsetX = 240, itemOffsetY = -48),
                ),
        )

    assertEquals(100, targetSid)
  }
}
