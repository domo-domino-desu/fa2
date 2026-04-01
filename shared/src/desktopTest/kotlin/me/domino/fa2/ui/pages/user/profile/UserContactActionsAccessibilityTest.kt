package me.domino.fa2.ui.pages.user.profile

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import me.domino.fa2.data.model.UserContact
import me.domino.fa2.ui.theme.Fa2Theme
import org.junit.Rule
import org.junit.Test

class UserContactActionsAccessibilityTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun icon_contact_exposes_click_and_long_click_actions() {
    composeRule.setContent {
      Fa2Theme {
        UserMetadataAndContactsRow(
            userTitle = "",
            registeredAtText = "",
            contacts =
                listOf(
                    UserContact(
                        label = "twitter",
                        value = "@domino",
                        url = "https://example.com/domino",
                    )
                ),
        )
      }
    }

    composeRule
        .onNode(hasContentDescription("Twitter"), useUnmergedTree = true)
        .assertExists()
        .assertHasClickAction()
        .assert(hasLongClickAction())
  }

  private fun hasContentDescription(text: String): SemanticsMatcher =
      androidx.compose.ui.test.hasContentDescription(text)

  private fun hasLongClickAction(): SemanticsMatcher =
      SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)
}
