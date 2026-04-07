package me.domino.fa2.data.network.endpoint

import kotlinx.coroutines.delay
import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizeHtmlResult
import me.domino.fa2.util.logging.summarizeUrl

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
  private val log = FaLog.withTag("DataSourceSocialActionBackend")

  override suspend fun execute(actionUrl: String): SocialActionResult {
    log.i { "社交动作后端(DataSource) -> 开始(url=${summarizeUrl(actionUrl)})" }
    var challengeRetryCount = 0
    while (true) {
      when (val response = dataSource.get(actionUrl)) {
        is HtmlResponseResult.Success ->
            return SocialActionResult.Completed(redirected = false).also {
              log.i { "社交动作后端(DataSource) -> 成功(url=${summarizeUrl(actionUrl)})" }
            }
        is HtmlResponseResult.AuthRequired ->
            return SocialActionResult.Failed(response.message).also {
              log.w { "社交动作后端(DataSource) -> 需要登录(url=${summarizeUrl(actionUrl)})" }
            }
        is HtmlResponseResult.MatureBlocked ->
            return SocialActionResult.Blocked(response.reason).also {
              log.w {
                "社交动作后端(DataSource) -> 受限(url=${summarizeUrl(actionUrl)},reason=${response.reason})"
              }
            }
        is HtmlResponseResult.Error ->
            return SocialActionResult.Failed(response.message).also {
              log.w {
                "社交动作后端(DataSource) -> 失败(url=${summarizeUrl(actionUrl)},message=${response.message})"
              }
            }
        is HtmlResponseResult.CfChallenge ->
            when (
                val decision =
                    challengePolicy.decide(
                        requestUrl = actionUrl,
                        cfRay = response.cfRay,
                        retryCount = challengeRetryCount,
                    )
            ) {
              SocialActionChallengeDecision.Retry -> {
                challengeRetryCount += 1
                log.w {
                  "社交动作后端(DataSource) -> Challenge重试(url=${summarizeUrl(actionUrl)},retry=$challengeRetryCount)"
                }
              }
              is SocialActionChallengeDecision.Terminal ->
                  return decision.result.also {
                    log.w {
                      "社交动作后端(DataSource) -> Challenge终止(url=${summarizeUrl(actionUrl)},result=${summarizeSocialActionResult(it)})"
                    }
                  }
            }
        HtmlResponseResult.ChallengeAborted ->
            return SocialActionResult.Failed("Cloudflare challenge aborted").also {
              log.w { "社交动作后端(DataSource) -> Challenge已取消(url=${summarizeUrl(actionUrl)})" }
            }
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
  private val log = FaLog.withTag("RawHttpSocialActionBackend")

  override suspend fun execute(actionUrl: String): SocialActionResult {
    log.i { "社交动作后端(RawHttp) -> 开始(url=${summarizeUrl(actionUrl)})" }
    var challengeRetryCount = 0
    while (true) {
      val response = transport.get(actionUrl)
      if (response.statusCode in REDIRECT_STATUS_CODES) {
        return SocialActionResult.Completed(redirected = true).also {
          log.i { "社交动作后端(RawHttp) -> 重定向成功(url=${summarizeUrl(actionUrl)})" }
        }
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
        is HtmlResponseResult.Success ->
            return SocialActionResult.Completed(redirected = false).also {
              log.i {
                "社交动作后端(RawHttp) -> 成功(url=${summarizeUrl(actionUrl)},result=${summarizeHtmlResult(classified)})"
              }
            }
        is HtmlResponseResult.AuthRequired ->
            return SocialActionResult.Failed(classified.message).also {
              log.w { "社交动作后端(RawHttp) -> 需要登录(url=${summarizeUrl(actionUrl)})" }
            }
        is HtmlResponseResult.MatureBlocked ->
            return SocialActionResult.Blocked(classified.reason).also {
              log.w {
                "社交动作后端(RawHttp) -> 受限(url=${summarizeUrl(actionUrl)},reason=${classified.reason})"
              }
            }
        is HtmlResponseResult.Error ->
            return SocialActionResult.Failed(classified.message).also {
              log.w {
                "社交动作后端(RawHttp) -> 失败(url=${summarizeUrl(actionUrl)},message=${classified.message})"
              }
            }
        is HtmlResponseResult.CfChallenge ->
            when (
                val decision =
                    challengePolicy.decide(
                        requestUrl = actionUrl,
                        cfRay = classified.cfRay,
                        retryCount = challengeRetryCount,
                    )
            ) {
              SocialActionChallengeDecision.Retry -> {
                challengeRetryCount += 1
                log.w {
                  "社交动作后端(RawHttp) -> Challenge重试(url=${summarizeUrl(actionUrl)},retry=$challengeRetryCount)"
                }
              }
              is SocialActionChallengeDecision.Terminal ->
                  return decision.result.also {
                    log.w {
                      "社交动作后端(RawHttp) -> Challenge终止(url=${summarizeUrl(actionUrl)},result=${summarizeSocialActionResult(it)})"
                    }
                  }
            }
        HtmlResponseResult.ChallengeAborted ->
            return SocialActionResult.Failed("Cloudflare challenge aborted").also {
              log.w { "社交动作后端(RawHttp) -> Challenge已取消(url=${summarizeUrl(actionUrl)})" }
            }
      }
    }
  }

  override suspend fun updateTagBlocklist(
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): SocialActionResult {
    log.i { "社交动作后端(RawHttp) -> 标签屏蔽开始(tag=$tagName,toAdd=$toAdd)" }
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
            return SocialActionResult.Failed(outcome.failureMessage).also {
              log.w {
                "社交动作后端(RawHttp) -> 标签屏蔽限流终止(tag=$tagName,retry=$rateLimitedRetryCount,message=${outcome.failureMessage})"
              }
            }
          }
          log.w {
            "社交动作后端(RawHttp) -> 标签屏蔽限流重试(tag=$tagName,retry=${rateLimitedRetryCount + 1},delayMs=${outcome.delayMs})"
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
                  SocialActionChallengeDecision.Retry -> {
                    challengeRetryCount += 1
                    log.w {
                      "社交动作后端(RawHttp) -> 标签屏蔽Challenge重试(tag=$tagName,retry=$challengeRetryCount)"
                    }
                  }
                  is SocialActionChallengeDecision.Terminal ->
                      return decision.result.also {
                        log.w {
                          "社交动作后端(RawHttp) -> 标签屏蔽Challenge终止(tag=$tagName,result=${summarizeSocialActionResult(it)})"
                        }
                      }
                }

            else ->
                return result.also {
                  log.i {
                    "社交动作后端(RawHttp) -> 标签屏蔽完成(tag=$tagName,result=${summarizeSocialActionResult(it)})"
                  }
                }
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
