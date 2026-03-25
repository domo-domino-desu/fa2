package me.domino.fa2.desktop.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class DesktopLocalSessionE2eTest {
  @get:Rule val composeRule = createComposeRule()

  private lateinit var runtime: DesktopE2eRuntime

  @Before
  fun setUp() {
    when (val result = preflightResult) {
      is DesktopE2ePreflightResult.Skip -> assumeTrue(result.message, false)
      is DesktopE2ePreflightResult.Fail -> error(result.message)
      is DesktopE2ePreflightResult.Ready -> {
        runtime = DesktopE2eRuntime.create(result.snapshot)
        runtime.start(composeRule)
      }

      null -> error("Desktop e2e preflight did not run.")
    }
  }

  @After
  fun tearDown() {
    runCatching { DesktopE2eThrottle.pauseAfterTest() }
    runCatching { runtime.close() }
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun startup_restores_local_session_and_enters_main_shell() {
    DesktopE2eThrottle.pauseBeforeStartup()
    DesktopE2eChallengePolicy.waitUntilTagExistsOrSkip(
        rule = composeRule,
        tag = "nav-feed",
        description = "main shell feed navigation",
        timeoutMillis = desktopE2eStartupTimeoutMs,
    )
    composeRule.onNodeWithTag("nav-feed", useUnmergedTree = true).assertExists()
    composeRule.onNodeWithTag("nav-browse", useUnmergedTree = true).assertExists()
    composeRule.onNodeWithTag("nav-search", useUnmergedTree = true).assertExists()
    composeRule.onNodeWithTag("nav-more", useUnmergedTree = true).assertExists()
    DesktopE2eChallengePolicy.ensureNoAuthScreen(composeRule)
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun top_level_navigation_and_first_submission_detail_are_reachable() {
    DesktopE2eThrottle.pauseBeforeStartup()
    DesktopE2eChallengePolicy.waitUntilTagExistsOrSkip(
        rule = composeRule,
        tag = "feed-screen",
        description = "feed screen root",
        timeoutMillis = desktopE2eStartupTimeoutMs,
    )

    DesktopE2eThrottle.pauseBeforeAction("open-more")
    composeRule.onNodeWithTag("nav-more", useUnmergedTree = true).performClick()
    DesktopE2eChallengePolicy.waitUntilTextExistsOrSkip(
        rule = composeRule,
        text = "账号中心",
        description = "more screen content",
    )

    DesktopE2eThrottle.pauseBeforeAction("open-search")
    composeRule.onNodeWithTag("nav-search", useUnmergedTree = true).performClick()
    DesktopE2eChallengePolicy.waitUntilTextExistsOrSkip(
        rule = composeRule,
        text = "点击搜索框设置条件后提交搜索。",
        description = "search screen hint",
    )

    DesktopE2eThrottle.pauseBeforeAction("return-feed")
    composeRule.onNodeWithTag("nav-feed", useUnmergedTree = true).performClick()
    DesktopE2eChallengePolicy.waitUntilAnyNodeWithTagExistsOrSkip(
        rule = composeRule,
        tag = "submission-waterfall-card",
        description = "first feed submission card",
    )

    DesktopE2eThrottle.pauseBeforeAction("open-first-submission")
    composeRule
        .onAllNodesWithTag("submission-waterfall-card", useUnmergedTree = true)[0]
        .performClick()
    DesktopE2eChallengePolicy.waitUntilTagExistsOrSkip(
        rule = composeRule,
        tag = "submission-route",
        description = "submission route screen",
    )
  }

  companion object {
    private var preflightResult: DesktopE2ePreflightResult? = null

    @JvmStatic
    @BeforeClass
    fun preflight() {
      preflightResult = DesktopE2eSessionLoader.preflight()
    }
  }
}
