package me.domino.fa2.data.network.endpoint

import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import me.domino.fa2.data.network.FaCookiesStorage
import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.UserAgentStorage
import me.domino.fa2.domain.challenge.ChallengeResolver
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizeUrl
import me.domino.fa2.util.toUserFacingRequestMessage

/** 社交动作端点（Fav/Watch 等）。 */
class SocialActionEndpoint
private constructor(
    private val backend: SocialActionBackend?,
) {
  private val log = FaLog.withTag("SocialActionEndpoint")

  constructor(
      dataSource: FaHtmlDataSource
  ) : this(
      backend =
          DataSourceSocialActionBackend(
              dataSource = dataSource,
              challengePolicy = SocialActionChallengePolicy(challengeResolver = null),
          )
  )

  constructor(
      client: HttpClient,
      cookiesStorage: FaCookiesStorage,
      userAgentStorage: UserAgentStorage,
      challengeResolver: ChallengeResolver,
  ) : this(
      backend =
          RawHttpSocialActionBackend(
              transport =
                  DefaultSocialActionHttpTransport(
                      client = client,
                      cookiesStorage = cookiesStorage,
                      userAgentStorage = userAgentStorage,
                  ),
              challengePolicy = SocialActionChallengePolicy(challengeResolver),
              tagBlockingResponseParser = SocialActionTagBlockingResponseParser(),
          )
  )

  /** 执行动作 URL。 */
  suspend fun execute(actionUrl: String): SocialActionResult {
    val targetUrl = actionUrl.trim()
    if (targetUrl.isBlank()) {
      log.w { "社交动作端点 -> 执行失败(空URL)" }
      return SocialActionResult.Failed(message = "Empty social action url")
    }
    log.i { "社交动作端点 -> 执行(url=${summarizeUrl(targetUrl)})" }
    return try {
      (backend?.execute(targetUrl)
              ?: SocialActionResult.Failed("No HTTP backend for social action"))
          .also { result -> log.i { "社交动作端点 -> ${summarizeSocialActionResult(result)}" } }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Throwable) {
      SocialActionResult.Failed(error.toUserFacingRequestMessage()).also { result ->
        log.e(error) { "社交动作端点 -> 执行异常(${summarizeSocialActionResult(result)})" }
      }
    }
  }

  /** 屏蔽/取消屏蔽标签（POST /route/tag_blocking）。 */
  suspend fun updateTagBlocklist(
      tagName: String,
      nonce: String,
      toAdd: Boolean = true,
  ): SocialActionResult {
    val normalizedTagName = tagName.trim()
    val normalizedNonce = nonce.trim()
    if (normalizedTagName.isBlank()) {
      log.w { "社交动作端点 -> 标签屏蔽失败(空tagName)" }
      return SocialActionResult.Failed(message = "Empty tag name for tag blocking")
    }
    if (normalizedNonce.isBlank()) {
      log.w { "社交动作端点 -> 标签屏蔽失败(缺少nonce,tag=$normalizedTagName)" }
      return SocialActionResult.Failed(message = "Missing tag block nonce")
    }
    log.i { "社交动作端点 -> 标签屏蔽(tag=$normalizedTagName,toAdd=$toAdd)" }

    return try {
      backend
          ?.updateTagBlocklist(
              tagName = normalizedTagName,
              nonce = normalizedNonce,
              toAdd = toAdd,
          )
          ?.also { result -> log.i { "社交动作端点 -> 标签屏蔽${summarizeSocialActionResult(result)}" } }
          ?: SocialActionResult.Failed("No HTTP backend for tag blocking").also { result ->
            log.w { "社交动作端点 -> 标签屏蔽${summarizeSocialActionResult(result)}" }
          }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Throwable) {
      SocialActionResult.Failed(error.toUserFacingRequestMessage()).also { result ->
        log.e(error) { "社交动作端点 -> 标签屏蔽异常(${summarizeSocialActionResult(result)})" }
      }
    }
  }
}

/** 社交动作执行结果。 */
sealed interface SocialActionResult {
  data class Completed(val redirected: Boolean) : SocialActionResult

  data class Challenge(val cfRay: String?) : SocialActionResult

  data class Blocked(val reason: String) : SocialActionResult

  data class Failed(val message: String) : SocialActionResult
}

internal fun summarizeSocialActionResult(result: SocialActionResult): String =
    when (result) {
      is SocialActionResult.Completed -> "成功(redirected=${result.redirected})"
      is SocialActionResult.Challenge -> "Challenge(cf-ray=${result.cfRay ?: "-"})"
      is SocialActionResult.Blocked -> "受限(${result.reason})"
      is SocialActionResult.Failed -> "失败(${result.message})"
    }
