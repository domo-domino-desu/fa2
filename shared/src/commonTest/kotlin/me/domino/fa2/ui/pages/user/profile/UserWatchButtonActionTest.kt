package me.domino.fa2.ui.pages.user.profile

import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.following_recommendation_unblock
import fa2.shared.generated.resources.processing
import fa2.shared.generated.resources.unwatch
import fa2.shared.generated.resources.watch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserWatchButtonActionTest {
  @Test
  fun notWatchingAndNotHiddenWatchesOnClickAndHidesOnLongClick() {
    val action =
        resolveUserWatchButtonAction(
            isWatching = false,
            recommendationHidden = false,
            updating = false,
            watchActionAvailable = true,
        )

    assertEquals(Res.string.watch, action.contentDescription)
    assertEquals(UserWatchButtonClickAction.ToggleWatch, action.clickAction)
    assertTrue(action.longClickHidesRecommendation)
    assertTrue(action.enabled)
  }

  @Test
  fun watchingUsesUnwatchClickAndNoLongClick() {
    val action =
        resolveUserWatchButtonAction(
            isWatching = true,
            recommendationHidden = false,
            updating = false,
            watchActionAvailable = true,
        )

    assertEquals(Res.string.unwatch, action.contentDescription)
    assertEquals(UserWatchButtonClickAction.ToggleWatch, action.clickAction)
    assertFalse(action.longClickHidesRecommendation)
    assertTrue(action.enabled)
  }

  @Test
  fun hiddenAndNotWatchingUnhidesOnClickAndNoLongClick() {
    val action =
        resolveUserWatchButtonAction(
            isWatching = false,
            recommendationHidden = true,
            updating = false,
            watchActionAvailable = true,
        )

    assertEquals(Res.string.following_recommendation_unblock, action.contentDescription)
    assertEquals(UserWatchButtonClickAction.UnhideRecommendation, action.clickAction)
    assertFalse(action.longClickHidesRecommendation)
    assertTrue(action.enabled)
  }

  @Test
  fun watchingAndHiddenStillUsesUnwatchClickAndNoLongClick() {
    val action =
        resolveUserWatchButtonAction(
            isWatching = true,
            recommendationHidden = true,
            updating = false,
            watchActionAvailable = true,
        )

    assertEquals(Res.string.unwatch, action.contentDescription)
    assertEquals(UserWatchButtonClickAction.ToggleWatch, action.clickAction)
    assertFalse(action.longClickHidesRecommendation)
    assertTrue(action.enabled)
  }

  @Test
  fun updatingDisablesAction() {
    val action =
        resolveUserWatchButtonAction(
            isWatching = false,
            recommendationHidden = false,
            updating = true,
            watchActionAvailable = true,
        )

    assertEquals(Res.string.processing, action.contentDescription)
    assertEquals(UserWatchButtonClickAction.None, action.clickAction)
    assertFalse(action.longClickHidesRecommendation)
    assertFalse(action.enabled)
  }
}
