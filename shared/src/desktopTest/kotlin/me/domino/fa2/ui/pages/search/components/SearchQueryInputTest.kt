package me.domino.fa2.ui.pages.search.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchQueryInputTest {
  @get:Rule val ui = createComposeRule()

  @Test
  fun fieldLinksAppendRefocusAndWaitForExplicitSearch() {
    var query by mutableStateOf("fox")
    var submits = 0
    ui.setContent { MaterialTheme { SearchQueryInput(query, { query = it }, { submits++ }, true) } }
    ui.onNodeWithTag("search-input").performClick().performTextInputSelection(TextRange(1))
    ui.onNodeWithTag("search-syntax-help").performClick()
    ui.onNodeWithTag("search-syntax-title").performScrollTo().performClick()
    ui.onNodeWithTag("search-syntax-dialog").assertDoesNotExist()
    ui.onNodeWithTag("search-input").assertIsFocused().performTextInput("wolf")
    ui.runOnIdle {
      assertEquals("fox @title wolf", query)
      assertEquals(0, submits)
    }
    ui.onNodeWithTag("search-input").performImeAction()
    ui.runOnIdle { assertEquals(1, submits) }
  }

  @Test
  fun booleanAndFieldLinksUseFuraffinitySyntax() {
    var query by mutableStateOf("")
    ui.setContent { MaterialTheme { SearchQueryInput(query, { query = it }, {}, true) } }
    for ((tag, expected) in
        listOf(
            "and" to "& ",
            "or" to "| ",
            "not" to "-",
            "not-bang" to "!",
            "title" to "@title ",
            "message" to "@message ",
            "lower" to "@lower ",
            "keywords" to "@keywords ",
        )) {
      ui.runOnIdle { query = "" }
      ui.onNodeWithTag("search-syntax-help").performClick()
      ui.onNodeWithTag("search-syntax-$tag").performScrollTo().performClick()
      ui.onNodeWithTag("search-input").assertIsFocused()
      ui.runOnIdle { assertEquals(expected, query) }
    }
  }

  @Test
  fun phrasesGroupingProximityAndQuorumKeepCaretInsideDelimiters() {
    var query by mutableStateOf("fox")
    ui.setContent { MaterialTheme { SearchQueryInput(query, { query = it }, {}, true) } }
    for ((tag, expected) in
        listOf(
            "phrase" to "fox \"red fox\"",
            "grouping" to "fox (red fox)",
            "proximity" to "fox \"red fox\"~3",
            "quorum" to "fox \"red fox\"/2",
        )) {
      ui.runOnIdle { query = "fox" }
      ui.onNodeWithTag("search-syntax-help").performClick()
      ui.onNodeWithTag("search-syntax-$tag").performScrollTo().performClick()
      ui.onNodeWithTag("search-input").assertIsFocused().performTextInput("red fox")
      ui.runOnIdle { assertEquals(expected, query) }
    }
  }
}
