package me.domino.fa2.desktop.e2e

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import kotlin.test.fail
import org.junit.AssumptionViolatedException

internal object DesktopE2eChallengePolicy {
  private const val challengeTag = "cf-challenge-status"

  @OptIn(ExperimentalTestApi::class)
  fun waitUntilTagExistsOrSkip(
      rule: ComposeContentTestRule,
      tag: String,
      description: String,
      timeoutMillis: Long = desktopE2eRouteTimeoutMs,
  ) {
    waitUntilOrSkip(rule = rule, description = description, timeoutMillis = timeoutMillis) {
      hasNode(rule = rule, matcher = hasTestTag(tag), useUnmergedTree = true)
    }
  }

  @OptIn(ExperimentalTestApi::class)
  fun waitUntilTextExistsOrSkip(
      rule: ComposeContentTestRule,
      text: String,
      description: String,
      timeoutMillis: Long = desktopE2eRouteTimeoutMs,
  ) {
    waitUntilOrSkip(rule = rule, description = description, timeoutMillis = timeoutMillis) {
      hasNode(rule = rule, matcher = hasText(text), useUnmergedTree = true)
    }
  }

  @OptIn(ExperimentalTestApi::class)
  fun waitUntilAnyNodeWithTagExistsOrSkip(
      rule: ComposeContentTestRule,
      tag: String,
      description: String,
      timeoutMillis: Long = desktopE2eRouteTimeoutMs,
  ) {
    waitUntilOrSkip(rule = rule, description = description, timeoutMillis = timeoutMillis) {
      hasNode(rule = rule, matcher = hasTestTag(tag), useUnmergedTree = true)
    }
  }

  @OptIn(ExperimentalTestApi::class)
  fun ensureNoAuthScreen(rule: ComposeContentTestRule) {
    if (hasNode(rule = rule, matcher = hasTestTag("auth-screen"), useUnmergedTree = true)) {
      fail(
          "Desktop e2e unexpectedly stayed on auth screen after local-session preflight succeeded."
      )
    }
  }

  @OptIn(ExperimentalTestApi::class)
  private fun waitUntilOrSkip(
      rule: ComposeContentTestRule,
      description: String,
      timeoutMillis: Long,
      condition: () -> Boolean,
  ) {
    val startAt = System.currentTimeMillis()
    while (System.currentTimeMillis() - startAt <= timeoutMillis) {
      rule.waitForIdle()
      if (isChallengeVisible(rule)) {
        throw AssumptionViolatedException(
            "Cloudflare challenge encountered while waiting for $description."
        )
      }
      if (condition()) {
        return
      }
      Thread.sleep(desktopE2eDefaultPollIntervalMs)
    }
    fail("Timed out waiting for $description within ${timeoutMillis}ms.")
  }

  @OptIn(ExperimentalTestApi::class)
  private fun isChallengeVisible(rule: ComposeContentTestRule): Boolean =
      hasNode(rule = rule, matcher = hasTestTag(challengeTag), useUnmergedTree = true) ||
          hasNode(
              rule = rule,
              matcher = hasText("Cloudflare", substring = true, ignoreCase = true),
              useUnmergedTree = true,
          )

  private fun hasNode(
      rule: ComposeContentTestRule,
      matcher: SemanticsMatcher,
      useUnmergedTree: Boolean,
  ): Boolean =
      runCatching {
            rule
                .onAllNodes(matcher = matcher, useUnmergedTree = useUnmergedTree)
                .fetchSemanticsNodes()
          }
          .getOrDefault(emptyList<SemanticsNode>())
          .isNotEmpty()
}
