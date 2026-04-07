package me.domino.fa2.ui.pages.user.watchlist

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import java.util.Locale
import me.domino.fa2.ui.layouts.UserWatchlistRouteTopBar
import me.domino.fa2.ui.theme.Fa2Theme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UserWatchlistRouteTopBarTest {
  @get:Rule val composeRule = createComposeRule()

  private val savedLocale: Locale = Locale.getDefault()

  @Before
  fun forceEnglishLocale() {
    Locale.setDefault(Locale.ENGLISH)
  }

  @After
  fun restoreLocale() {
    Locale.setDefault(savedLocale)
  }

  @Test
  fun showsShuffleActionBeforeCopyAction() {
    composeRule.setContent {
      Fa2Theme {
        UserWatchlistRouteTopBar(
            title = "Following",
            onBack = {},
            onGoHome = {},
            shareUrl = "https://example.com/u",
            showShuffleAction = true,
            onShuffle = {},
        )
      }
    }

    composeRule.onNodeWithContentDescription("Shuffle following").assertExists()
    composeRule.onNodeWithContentDescription("Copy link").assertExists()

    val shuffleBounds =
        composeRule
            .onNodeWithContentDescription("Shuffle following")
            .fetchSemanticsNode()
            .boundsInRoot
    val copyBounds =
        composeRule.onNodeWithContentDescription("Copy link").fetchSemanticsNode().boundsInRoot
    kotlin.test.assertTrue(shuffleBounds.left < copyBounds.left)
  }

  @Test
  fun hidesShuffleActionWhenNotRequested() {
    composeRule.setContent {
      Fa2Theme {
        UserWatchlistRouteTopBar(
            title = "Followers",
            onBack = {},
            onGoHome = {},
            shareUrl = "https://example.com/u",
        )
      }
    }

    composeRule.onNodeWithContentDescription("Shuffle following").assertDoesNotExist()
    composeRule.onNodeWithContentDescription("Copy link").assertExists()
  }

  @Test
  fun showsLoadingIndicatorWhenShuffling() {
    composeRule.setContent {
      Fa2Theme {
        UserWatchlistRouteTopBar(
            title = "Following",
            onBack = {},
            onGoHome = {},
            shareUrl = "https://example.com/u",
            showShuffleAction = true,
            isShuffling = true,
            onShuffle = {},
        )
      }
    }

    composeRule.onNodeWithContentDescription("Shuffling following").assertExists()
    composeRule.onNodeWithContentDescription("Shuffle following").assertDoesNotExist()
  }
}
