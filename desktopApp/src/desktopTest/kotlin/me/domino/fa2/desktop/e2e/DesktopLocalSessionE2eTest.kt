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

/** 使用本地真实会话数据的桌面端 E2E 测试套件。 */
class DesktopLocalSessionE2eTest {
  /** Compose UI 测试规则。 */
  @get:Rule val composeRule = createComposeRule()

  /** 当前测试使用的 E2E 运行时实例。 */
  private lateinit var runtime: DesktopE2eRuntime

  /** 测试前置：执行预检并启动应用运行时。 */
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

  /** 测试后置：执行节流等待并关闭运行时。 */
  @After
  fun tearDown() {
    runCatching { DesktopE2eThrottle.pauseAfterTest() }
    runCatching { runtime.close() }
  }

  /** 验证应用启动后能恢复本地会话并正确进入主导航界面。 */
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

  /** 验证顶层导航各页及首个作品详情页均可正常访问。 */
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
    DesktopE2eChallengePolicy.waitUntilTagExistsOrSkip(
        rule = composeRule,
        tag = "more-screen",
        description = "more screen root",
    )
    composeRule.onNodeWithTag("more-following", useUnmergedTree = true).assertExists()
    composeRule.onNodeWithTag("more-favorites", useUnmergedTree = true).assertExists()

    DesktopE2eThrottle.pauseBeforeAction("open-search")
    composeRule.onNodeWithTag("nav-search", useUnmergedTree = true).performClick()
    DesktopE2eChallengePolicy.waitUntilTextExistsOrSkip(
        rule = composeRule,
        text = "点击搜索框设置条件后提交搜索",
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
    /** 类级别的预检结果，在所有测试运行前通过 BeforeClass 填充。 */
    private var preflightResult: DesktopE2ePreflightResult? = null

    /** 在测试类初始化时执行 E2E 预检，判断是否具备运行条件。 */
    @JvmStatic
    @BeforeClass
    fun preflight() {
      preflightResult = DesktopE2eSessionLoader.preflight()
    }
  }
}
