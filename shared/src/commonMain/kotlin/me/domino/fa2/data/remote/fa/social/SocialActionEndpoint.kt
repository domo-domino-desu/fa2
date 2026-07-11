package me.domino.fa2.data.remote.fa.social

import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import me.domino.fa2.data.remote.fa.core.FaOriginPolicy
import me.domino.fa2.data.remote.fa.core.toUserFacingRequestMessage
import me.domino.fa2.data.remote.fa.session.ChallengeResolver
import me.domino.fa2.data.remote.fa.session.FaCookiesStorage
import me.domino.fa2.data.remote.fa.session.UserAgentStorage
import me.domino.fa2.utils.logging.FaLog

/** 社交动作端点（Fav/Watch 等）。 */
interface SocialActionSource {
  suspend fun execute(actionUrl: String): SocialActionResult

  suspend fun updateTagBlocklist(
      tagName: String,
      nonce: String,
      toAdd: Boolean = true,
  ): SocialActionResult
}

class SocialActionEndpoint private constructor(private val backend: SocialActionBackend) :
    SocialActionSource {
  private val log = FaLog.withTag("SocialActionEndpoint")

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
  override suspend fun execute(actionUrl: String): SocialActionResult {
    val targetUrl = actionUrl.trim()
    if (targetUrl.isBlank()) {
      log.w { "社交动作端点 -> 执行失败(空URL)" }
      return SocialActionResult.Failed(message = "Empty social action url")
    }
    val trustedUrl =
        FaOriginPolicy.trustedActionUrlOrNull(
            targetUrl,
            allowedPathPrefixes = setOf("/fav/", "/unfav/", "/watch/", "/unwatch/"),
        )
    if (trustedUrl == null) {
      log.w { "社交动作端点 -> 拒绝不受信任地址(url=${targetUrl.trim()})" }
      return SocialActionResult.Failed(message = "Untrusted FA action URL")
    }
    log.i { "社交动作端点 -> 执行(url=${targetUrl.trim()})" }
    return try {
      backend.execute(trustedUrl).also { result ->
        log.i { "社交动作端点 -> ${summarizeSocialActionResult(result)}" }
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Throwable) {
      SocialActionResult.Failed(error.toUserFacingRequestMessage()).also { result ->
        log.e(error) { "社交动作端点 -> 执行异常(${summarizeSocialActionResult(result)})" }
      }
    }
  }

  /** 屏蔽/取消屏蔽标签（POST /route/tag_blocking）。 */
  override suspend fun updateTagBlocklist(
      tagName: String,
      nonce: String,
      toAdd: Boolean,
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
          .updateTagBlocklist(tagName = normalizedTagName, nonce = normalizedNonce, toAdd = toAdd)
          .also { result -> log.i { "社交动作端点 -> 标签屏蔽${summarizeSocialActionResult(result)}" } }
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
