package me.domino.fa2.data.network.endpoint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SocialActionTagBlockingResponseParserTest {
  private val parser = SocialActionTagBlockingResponseParser()

  @Test
  fun parsesSuccessJson() {
    val outcome =
        parser.parse(
            SocialActionHttpResponse(
                statusCode = 200,
                headers = emptyMap(),
                body = """{"success":true}""",
            )
        )

    val result = assertIs<SocialActionTagBlockingOutcome.Result>(outcome)
    assertEquals(SocialActionResult.Completed(redirected = false), result.result)
  }

  @Test
  fun parsesRateLimitedJsonWithClampedDelay() {
    val outcome =
        parser.parse(
            SocialActionHttpResponse(
                statusCode = 200,
                headers = emptyMap(),
                body = """{"error":"rate-limited","message":"Too fast","time-left":"0"}""",
            )
        )

    val retry = assertIs<SocialActionTagBlockingOutcome.RetryAfter>(outcome)
    assertEquals(200L, retry.delayMs)
    assertEquals("Too fast", retry.failureMessage)
  }

  @Test
  fun fallsBackToHtmlClassificationForChallengePages() {
    val outcome =
        parser.parse(
            SocialActionHttpResponse(
                statusCode = 403,
                headers = mapOf("cf-ray" to listOf("ray-5"), "server" to listOf("cloudflare")),
                body =
                    "<html><title>Just a moment</title><body id=\"challenge-running\"></body></html>",
            )
        )

    val result = assertIs<SocialActionTagBlockingOutcome.Result>(outcome)
    assertEquals(SocialActionResult.Challenge(cfRay = "ray-5"), result.result)
  }
}
