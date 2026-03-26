package me.domino.fa2.data.network.endpoint

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.domino.fa2.data.network.HtmlResponseResult

internal sealed interface SocialActionTagBlockingOutcome {
  data class Result(val result: SocialActionResult) : SocialActionTagBlockingOutcome

  data class RetryAfter(
      val delayMs: Long,
      val failureMessage: String,
  ) : SocialActionTagBlockingOutcome
}

internal class SocialActionTagBlockingResponseParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
  fun parse(response: SocialActionHttpResponse): SocialActionTagBlockingOutcome {
    if (response.statusCode in REDIRECT_STATUS_CODES) {
      return SocialActionTagBlockingOutcome.Result(SocialActionResult.Completed(redirected = true))
    }

    val jsonObject = runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull()
    if (jsonObject != null) {
      val success = jsonObject["success"]?.jsonPrimitive?.booleanOrNull == true
      if (success) {
        return SocialActionTagBlockingOutcome.Result(
            SocialActionResult.Completed(redirected = false)
        )
      }

      val error = jsonObject["error"]?.jsonPrimitive?.contentOrNull.orEmpty()
      val message =
          jsonObject["message"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
              ?: error.ifBlank { "Tag blocking failed" }
      if (error.equals("rate-limited", ignoreCase = true)) {
        val timeLeftSeconds =
            jsonObject["time-left"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.toFloatOrNull()
                ?.coerceAtLeast(0f) ?: 0.5f
        val delayMs = (timeLeftSeconds * 1000).toLong().coerceIn(200L, 3_000L)
        return SocialActionTagBlockingOutcome.RetryAfter(
            delayMs = delayMs,
            failureMessage = message,
        )
      }
      return SocialActionTagBlockingOutcome.Result(SocialActionResult.Failed(message))
    }

    return when (
        val classified =
            HtmlResponseResult.classify(
                statusCode = response.statusCode,
                headers = response.headers,
                body = response.body,
                url = TAG_BLOCKING_URL,
            )
    ) {
      is HtmlResponseResult.Success ->
          SocialActionTagBlockingOutcome.Result(SocialActionResult.Completed(redirected = false))
      is HtmlResponseResult.MatureBlocked ->
          SocialActionTagBlockingOutcome.Result(SocialActionResult.Blocked(classified.reason))
      is HtmlResponseResult.Error ->
          SocialActionTagBlockingOutcome.Result(SocialActionResult.Failed(classified.message))
      is HtmlResponseResult.CfChallenge ->
          SocialActionTagBlockingOutcome.Result(
              SocialActionResult.Challenge(cfRay = classified.cfRay)
          )
    }
  }

  private companion object {
    private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
  }
}
