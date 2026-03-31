package me.domino.fa2.data.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures

/** HtmlResponseResult 行为测试。 */
class HtmlResponseResultTest {
  /** 应该识别 Cloudflare challenge 页面。 */
  @Test
  fun detectsCloudflareChallenge() {
    val result =
        HtmlResponseResult.classify(
            statusCode = 403,
            headers = mapOf("Server" to listOf("cloudflare"), "CF-Ray" to listOf("abc123")),
            body =
                "<html><head><title>Just a moment...</title></head><body>challenge</body></html>",
            requestUrl = "https://www.furaffinity.net/",
            finalUrl = "https://www.furaffinity.net/",
        )

    assertTrue(result is HtmlResponseResult.CfChallenge)
  }

  /** mature 提示页不应误判为 challenge。 */
  @Test
  fun doesNotMisclassifyMaturePageAsChallenge() {
    val matureHtml =
        TestFixtures.read("www.furaffinity.net:view:60245416-mature-content-message.html")
    val result =
        HtmlResponseResult.classify(
            statusCode = 200,
            headers =
                mapOf(
                    "Server" to listOf("cloudflare"),
                    "CF-Ray" to listOf("9ddd33023806e170-NRT"),
                ),
            body = matureHtml,
            requestUrl = "https://www.furaffinity.net/view/60245416/",
            finalUrl = "https://www.furaffinity.net/view/60245416/",
        )

    assertFalse(result is HtmlResponseResult.CfChallenge)
  }

  @Test
  fun detectsAuthRequiredWhenBusinessPageFallsBackToLoggedOutHome() {
    val loggedOutHtml = TestFixtures.read("www.furaffinity.net:loggedout.html")

    val result =
        HtmlResponseResult.classify(
            statusCode = 200,
            headers = emptyMap(),
            body = loggedOutHtml,
            requestUrl = "https://www.furaffinity.net/view/12345/",
            finalUrl = "https://www.furaffinity.net/",
        )

    assertIs<HtmlResponseResult.AuthRequired>(result)
  }
}
