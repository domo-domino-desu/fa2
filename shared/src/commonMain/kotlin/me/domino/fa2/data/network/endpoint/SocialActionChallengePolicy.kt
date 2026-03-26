package me.domino.fa2.data.network.endpoint

import me.domino.fa2.data.network.challenge.CfChallengeSignal
import me.domino.fa2.data.network.challenge.ChallengeResolver

internal sealed interface SocialActionChallengeDecision {
  data object Retry : SocialActionChallengeDecision

  data class Terminal(val result: SocialActionResult) : SocialActionChallengeDecision
}

internal class SocialActionChallengePolicy(
    private val challengeResolver: ChallengeResolver?,
) {
  suspend fun decide(
      requestUrl: String,
      cfRay: String?,
      retryCount: Int,
      maxRetries: Int = 1,
  ): SocialActionChallengeDecision {
    val resolver = challengeResolver ?: return terminalChallenge(cfRay)
    val resolved =
        resolver.awaitResolution(
            challenge = CfChallengeSignal(requestUrl = requestUrl, cfRay = cfRay)
        )
    if (!resolved) {
      return SocialActionChallengeDecision.Terminal(
          SocialActionResult.Failed("Cloudflare challenge unresolved")
      )
    }
    if (retryCount >= maxRetries) {
      return terminalChallenge(cfRay)
    }
    return SocialActionChallengeDecision.Retry
  }

  private fun terminalChallenge(cfRay: String?): SocialActionChallengeDecision.Terminal =
      SocialActionChallengeDecision.Terminal(SocialActionResult.Challenge(cfRay = cfRay))
}
