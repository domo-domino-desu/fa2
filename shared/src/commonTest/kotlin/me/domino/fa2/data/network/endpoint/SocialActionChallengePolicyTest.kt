package me.domino.fa2.data.network.endpoint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import me.domino.fa2.application.challenge.port.CfChallengeSignal
import me.domino.fa2.application.challenge.port.ChallengeResolver

class SocialActionChallengePolicyTest {
  @Test
  fun returnsChallengeWhenResolverMissing() = runTest {
    val decision =
        SocialActionChallengePolicy(challengeResolver = null)
            .decide(
                requestUrl = "https://example.com/action",
                cfRay = "ray-1",
                retryCount = 0,
            )

    val terminal = assertIs<SocialActionChallengeDecision.Terminal>(decision)
    assertEquals(SocialActionResult.Challenge(cfRay = "ray-1"), terminal.result)
  }

  @Test
  fun returnsFailureWhenResolverCannotResolve() = runTest {
    val resolver = RecordingChallengeResolver(false)

    val decision =
        SocialActionChallengePolicy(challengeResolver = resolver)
            .decide(
                requestUrl = "https://example.com/action",
                cfRay = "ray-2",
                retryCount = 0,
            )

    val terminal = assertIs<SocialActionChallengeDecision.Terminal>(decision)
    assertEquals(
        SocialActionResult.Failed("Cloudflare challenge unresolved"),
        terminal.result,
    )
    assertEquals(
        listOf(CfChallengeSignal(requestUrl = "https://example.com/action", cfRay = "ray-2")),
        resolver.requests,
    )
  }

  @Test
  fun retriesWhenResolverSucceedsAndRetryBudgetRemains() = runTest {
    val decision =
        SocialActionChallengePolicy(challengeResolver = RecordingChallengeResolver(true))
            .decide(
                requestUrl = "https://example.com/action",
                cfRay = "ray-3",
                retryCount = 0,
            )

    assertEquals(SocialActionChallengeDecision.Retry, decision)
  }

  @Test
  fun returnsChallengeWhenRetryBudgetIsExhausted() = runTest {
    val decision =
        SocialActionChallengePolicy(challengeResolver = RecordingChallengeResolver(true))
            .decide(
                requestUrl = "https://example.com/action",
                cfRay = "ray-4",
                retryCount = 1,
            )

    val terminal = assertIs<SocialActionChallengeDecision.Terminal>(decision)
    assertEquals(SocialActionResult.Challenge(cfRay = "ray-4"), terminal.result)
  }
}

private class RecordingChallengeResolver(vararg decisions: Boolean) : ChallengeResolver {
  private val queue = ArrayDeque(decisions.toList())
  val requests = mutableListOf<CfChallengeSignal>()

  override suspend fun awaitResolution(challenge: CfChallengeSignal): Boolean {
    requests += challenge
    return queue.removeFirstOrNull() ?: false
  }
}
