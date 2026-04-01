package me.domino.fa2.ui.components.submission

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals

class SubmissionOverlayAndAuthorTest {
  @Test
  fun expandingOverlayRectPreservesHorizontalBoundsAndCenterWhenPossible() {
    val expanded =
        expandOverlayRectForMinimumHeight(
            rawRect = Rect(left = 20f, top = 40f, right = 120f, bottom = 60f),
            minimumHeightPx = 60f,
            contentDisplayRect = IntRect(left = 0, top = 0, right = 200, bottom = 200),
        )

    assertEquals(20f, expanded.left)
    assertEquals(120f, expanded.right)
    assertEquals(60f, expanded.height)
    assertEquals(50f, expanded.center.y)
  }

  @Test
  fun expandingOverlayRectKeepsResultInsideContentBounds() {
    val expanded =
        expandOverlayRectForMinimumHeight(
            rawRect = Rect(left = 20f, top = 5f, right = 120f, bottom = 15f),
            minimumHeightPx = 40f,
            contentDisplayRect = IntRect(left = 0, top = 0, right = 200, bottom = 80),
        )

    assertEquals(0f, expanded.top)
    assertEquals(40f, expanded.bottom)
  }
}
