package me.domino.fa2.data.network.endpoint

import kotlinx.coroutines.delay
import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult

internal interface SocialActionBackend {
  suspend fun execute(actionUrl: String): SocialActionResult

  suspend fun updateTagBlocklist(
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): SocialActionResult
}

internal class DataSourceSocialActionBackend(
    private val dataSource: FaHtmlDataSource,
    private val challengePolicy: SocialActionChallengePolicy,
) : SocialActionBackend {
  override suspend fun execute(actionUrl: String): SocialActionResult {
    var challengeRetryCount = 0
    while (true) {
      when (val response = dataSource.get(actionUrl)) {
        is HtmlResponseResult.Success -> return SocialActionResult.Completed(redirected = false)
        is HtmlResponseResult.AuthRequired -> return SocialActionResult.Failed(response.message)
        is HtmlResponseResult.MatureBlocked -> return SocialActionResult.Blocked(response.reason)
        is HtmlResponseResult.Error -> return SocialActionResult.Failed(response.message)
        is HtmlResponseResult.CfChallenge ->
            when (
                val decision =
                    challengePolicy.decide(
                        requestUrl = actionUrl,
                        cfRay = response.cfRay,
                        retryCount = challengeRetryCount,
                    )
            ) {
              SocialActionChallengeDecision.Retry -> challengeRetryCount += 1
              is SocialActionChallengeDecision.Terminal -> return decision.result
            }
        HtmlResponseResult.ChallengeAborted ->
            return SocialActionResult.Failed("Cloudflare challenge aborted")
      }
    }
  }

  override suspend fun updateTagBlocklist(
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): SocialActionResult = SocialActionResult.Failed("No HTTP backend for tag blocking")
}

internal class RawHttpSocialActionBackend(
    private val transport: SocialActionHttpTransport,
    private val challengePolicy: SocialActionChallengePolicy,
    private val tagBlockingResponseParser: SocialActionTagBlockingResponseParser,
) : SocialActionBackend {
  override suspend fun execute(actionUrl: String): SocialActionResult {
    var challengeRetryCount = 0
    while (true) {
      val response = transport.get(actionUrl)
      if (response.statusCode in REDIRECT_STATUS_CODES) {
        return SocialActionResult.Completed(redirected = true)
      }

      when (
          val classified =
              HtmlResponseResult.classify(
                  statusCode = response.statusCode,
                  headers = response.headers,
                  body = response.body,
                  requestUrl = actionUrl,
                  finalUrl = actionUrl,
              )
      ) {
        is HtmlResponseResult.Success -> return SocialActionResult.Completed(redirected = false)
        is HtmlResponseResult.AuthRequired -> return SocialActionResult.Failed(classified.message)
        is HtmlResponseResult.MatureBlocked -> return SocialActionResult.Blocked(classified.reason)
        is HtmlResponseResult.Error -> return SocialActionResult.Failed(classified.message)
        is HtmlResponseResult.CfChallenge ->
            when (
                val decision =
                    challengePolicy.decide(
                        requestUrl = actionUrl,
                        cfRay = classified.cfRay,
                        retryCount = challengeRetryCount,
                    )
            ) {
              SocialActionChallengeDecision.Retry -> challengeRetryCount += 1
              is SocialActionChallengeDecision.Terminal -> return decision.result
            }
        HtmlResponseResult.ChallengeAborted ->
            return SocialActionResult.Failed("Cloudflare challenge aborted")
      }
    }
  }

  override suspend fun updateTagBlocklist(
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): SocialActionResult {
    var challengeRetryCount = 0
    var rateLimitedRetryCount = 0

    while (true) {
      val response =
          transport.postTagBlocklist(
              tagName = tagName,
              nonce = nonce,
              toAdd = toAdd,
          )

      when (val outcome = tagBlockingResponseParser.parse(response)) {
        is SocialActionTagBlockingOutcome.RetryAfter -> {
          if (rateLimitedRetryCount >= MAX_RATE_LIMIT_RETRIES) {
            return SocialActionResult.Failed(outcome.failureMessage)
          }
          delay(outcome.delayMs)
          rateLimitedRetryCount += 1
        }

        is SocialActionTagBlockingOutcome.Result -> {
          when (val result = outcome.result) {
            is SocialActionResult.Challenge ->
                when (
                    val decision =
                        challengePolicy.decide(
                            requestUrl = TAG_BLOCKING_URL,
                            cfRay = result.cfRay,
                            retryCount = challengeRetryCount,
                        )
                ) {
                  SocialActionChallengeDecision.Retry -> challengeRetryCount += 1
                  is SocialActionChallengeDecision.Terminal -> return decision.result
                }

            else -> return result
          }
        }
      }
    }
  }

  private companion object {
    private const val MAX_RATE_LIMIT_RETRIES = 2
    private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
  }
}
