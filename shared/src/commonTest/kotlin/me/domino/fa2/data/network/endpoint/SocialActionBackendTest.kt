package me.domino.fa2.data.fa.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class SocialActionBackendTest {
  @Test
  fun rawHttpBackendRetriesGetAfterChallengeResolution() = runTest {
    val resolver = BackendChallengeResolver(true)
    val transport =
        FakeSocialActionHttpTransport(
            getResponses =
                ArrayDeque(
                    listOf(
                        challengeResponse(cfRay = "ray-2"),
                        SocialActionHttpResponse(
                            statusCode = 200,
                            headers = emptyMap(),
                            body = "<html>ok</html>",
                        ),
                    )
                )
        )
    val backend =
        RawHttpSocialActionBackend(
            transport = transport,
            challengePolicy = SocialActionChallengePolicy(resolver),
            tagBlockingResponseParser = SocialActionTagBlockingResponseParser(),
        )

    val result = backend.execute("https://example.com/b")

    assertEquals(SocialActionResult.Completed(redirected = false), result)
    assertEquals(2, transport.getCallCount)
    assertEquals(1, resolver.requests.size)
  }

  @Test
  fun rawHttpBackendRetriesTagBlockingAfterRateLimit() = runTest {
    val transport =
        FakeSocialActionHttpTransport(
            postResponses =
                ArrayDeque(
                    listOf(
                        SocialActionHttpResponse(
                            statusCode = 200,
                            headers = emptyMap(),
                            body =
                                """{"error":"rate-limited","message":"Too fast","time-left":"0"}""",
                        ),
                        SocialActionHttpResponse(
                            statusCode = 200,
                            headers = emptyMap(),
                            body = """{"success":true}""",
                        ),
                    )
                )
        )
    val backend =
        RawHttpSocialActionBackend(
            transport = transport,
            challengePolicy = SocialActionChallengePolicy(challengeResolver = null),
            tagBlockingResponseParser = SocialActionTagBlockingResponseParser(),
        )

    val result = backend.updateTagBlocklist(tagName = "wolf", nonce = "nonce", toAdd = true)

    assertEquals(SocialActionResult.Completed(redirected = false), result)
    assertEquals(2, transport.postCallCount)
  }
}

private class BackendChallengeResolver(vararg decisions: Boolean) :
    me.domino.fa2.data.fa.session.ChallengeResolver {
  private val queue = ArrayDeque(decisions.toList())
  val requests = mutableListOf<me.domino.fa2.data.fa.session.CfChallengeSignal>()

  override suspend fun awaitResolution(
      challenge: me.domino.fa2.data.fa.session.CfChallengeSignal
  ): Boolean {
    requests += challenge
    return queue.removeFirstOrNull() ?: false
  }
}

private class FakeSocialActionHttpTransport(
    val getResponses: ArrayDeque<SocialActionHttpResponse> = ArrayDeque(),
    val postResponses: ArrayDeque<SocialActionHttpResponse> = ArrayDeque(),
) : SocialActionHttpTransport {
  var getCallCount: Int = 0
    private set

  var postCallCount: Int = 0
    private set

  override suspend fun get(url: String): SocialActionHttpResponse {
    getCallCount += 1
    return getResponses.removeFirstOrNull()
        ?: SocialActionHttpResponse(
            statusCode = 500,
            headers = emptyMap(),
            body = "missing get response for $url",
        )
  }

  override suspend fun postTagBlocklist(
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): SocialActionHttpResponse {
    postCallCount += 1
    return postResponses.removeFirstOrNull()
        ?: SocialActionHttpResponse(
            statusCode = 500,
            headers = emptyMap(),
            body = "missing post response for $tagName",
        )
  }
}

private fun challengeResponse(cfRay: String): SocialActionHttpResponse =
    SocialActionHttpResponse(
        statusCode = 403,
        headers = mapOf("cf-ray" to listOf(cfRay), "server" to listOf("cloudflare")),
        body = "<html><title>Just a moment</title><body id=\"challenge-running\"></body></html>",
    )
