package me.domino.fa2.data.network.endpoint

import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import me.domino.fa2.application.challenge.port.ChallengeResolver
import me.domino.fa2.data.network.FaCookiesStorage
import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.UserAgentStorage
import me.domino.fa2.util.toUserFacingRequestMessage

/** 社交动作端点（Fav/Watch 等）。 */
class SocialActionEndpoint
private constructor(
    private val backend: SocialActionBackend?,
) {
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
      return SocialActionResult.Failed(message = "Empty social action url")
    }
    return try {
      backend?.execute(targetUrl) ?: SocialActionResult.Failed("No HTTP backend for social action")
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Throwable) {
      SocialActionResult.Failed(error.toUserFacingRequestMessage())
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
      return SocialActionResult.Failed(message = "Empty tag name for tag blocking")
    }
    if (normalizedNonce.isBlank()) {
      return SocialActionResult.Failed(message = "Missing tag block nonce")
    }

    return try {
      backend?.updateTagBlocklist(
          tagName = normalizedTagName,
          nonce = normalizedNonce,
          toAdd = toAdd,
      ) ?: SocialActionResult.Failed("No HTTP backend for tag blocking")
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Throwable) {
      SocialActionResult.Failed(error.toUserFacingRequestMessage())
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
