package me.domino.fa2.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import java.util.Locale
import kotlin.test.assertTrue
import me.domino.fa2.ui.components.settings.SettingsGroup
import me.domino.fa2.ui.components.settings.SettingsSwitchRow
import me.domino.fa2.ui.theme.Fa2Theme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AccessibilitySemanticsTest {
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
  fun filterDialogTriggerField_exposes_named_button_state() {
    var opened by mutableStateOf(false)

    composeRule.setContent {
      Fa2Theme {
        FilterDialogTriggerField(
            label = "Category",
            valueLabel = "Artwork",
            onOpenPicker = { opened = true },
        )
      }
    }

    composeRule
        .onNode(hasContentDescription("Category"))
        .assertExists()
        .assertHasClickAction()
        .assert(hasStateDescription("Artwork"))
        .performClick()

    composeRule.runOnIdle { assertTrue(opened) }
  }

  @Test
  fun filterDialogTriggerField_reports_not_set_when_value_is_blank() {
    composeRule.setContent {
      Fa2Theme { FilterDialogTriggerField(label = "Species", valueLabel = "", onOpenPicker = {}) }
    }

    composeRule
        .onNode(hasContentDescription("Species"))
        .assertExists()
        .assert(hasStateDescription("Not set"))
  }

  @Test
  fun settingsSwitchRow_is_toggleable_from_row_label() {
    var checked by mutableStateOf(false)

    composeRule.setContent {
      Fa2Theme {
        SettingsSwitchRow(
            label = "Allow system media indexing",
            checked = checked,
            onCheckedChange = { checked = it },
        )
      }
    }

    composeRule
        .onNode(hasContentDescription("Allow system media indexing"))
        .assertExists()
        .assertHasClickAction()
        .performClick()

    composeRule.runOnIdle { assertTrue(checked) }
  }

  @Test
  fun settingsGroup_title_is_exposed_as_heading() {
    composeRule.setContent { Fa2Theme { SettingsGroup(title = "Accessibility") { Box {} } } }

    composeRule.onNode(hasTextWithHeading("Accessibility")).assertExists()
  }

  @Test
  fun appFeedbackHost_marks_snackbar_host_as_live_region() {
    composeRule.setContent { Fa2Theme { AppFeedbackHost(content = {}) } }

    composeRule.onNode(hasLiveRegion(LiveRegionMode.Polite), useUnmergedTree = true).assertExists()
  }

  private fun hasStateDescription(text: String): SemanticsMatcher =
      SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

  private fun hasTextWithHeading(text: String): SemanticsMatcher =
      hasText(text).and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))

  private fun hasLiveRegion(mode: LiveRegionMode): SemanticsMatcher =
      SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, mode)
}
