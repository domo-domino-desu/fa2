package me.domino.fa2.ui.pages.user.route

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import java.util.Locale
import kotlin.test.assertTrue
import me.domino.fa2.ui.layouts.UserRouteTopBar
import me.domino.fa2.ui.theme.Fa2Theme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UserRouteTopBarTest {
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
  fun showsSimilarUsersActionBeforeCopyAction() {
    composeRule.setContent {
      Fa2Theme {
        UserRouteTopBar(
            title = "~artist",
            onBack = {},
            onGoHome = {},
            shareUrl = "https://example.com/u",
            onExploreSimilarUsers = {},
        )
      }
    }

    composeRule.onNodeWithContentDescription("Similar Users").assertExists()
    composeRule.onNodeWithContentDescription("Copy link").assertExists()

    val similarUsersBounds =
        composeRule.onNodeWithContentDescription("Similar Users").fetchSemanticsNode().boundsInRoot
    val copyBounds =
        composeRule.onNodeWithContentDescription("Copy link").fetchSemanticsNode().boundsInRoot
    assertTrue(similarUsersBounds.left < copyBounds.left)
  }

  @Test
  fun hidesSimilarUsersActionWhenNotRequested() {
    composeRule.setContent {
      Fa2Theme {
        UserRouteTopBar(
            title = "~artist",
            onBack = {},
            onGoHome = {},
            shareUrl = "https://example.com/u",
        )
      }
    }

    composeRule.onNodeWithContentDescription("Similar Users").assertDoesNotExist()
    composeRule.onNodeWithContentDescription("Copy link").assertExists()
  }
}
