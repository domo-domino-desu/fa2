package me.domino.fa2.data.network.endpoint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult

class SocialActionBackendTest {
  @Test
  fun dataSourceBackendRetriesResolvedChallenge() = runTest {
    val resolver = BackendChallengeResolver(true)
    val backend =
        DataSourceSocialActionBackend(
            dataSource =
                ScriptedHtmlDataSource(
                    HtmlResponseResult.CfChallenge(cfRay = "ray-1"),
                    HtmlResponseResult.Success(
                        body = "<html>ok</html>",
                        url = "https://example.com/a",
                    ),
                ),
            challengePolicy = SocialActionChallengePolicy(resolver),
        )

    val result = backend.execute("https://example.com/a")

    assertEquals(SocialActionResult.Completed(redirected = false), result)
    assertEquals(1, resolver.requests.size)
  }

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
                ),
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
                ),
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

private class ScriptedHtmlDataSource(vararg responses: HtmlResponseResult) : FaHtmlDataSource {
  private val queue = ArrayDeque(responses.toList())

  override suspend fun get(url: String): HtmlResponseResult =
      queue.removeFirstOrNull()
          ?: HtmlResponseResult.Error(statusCode = 500, message = "No scripted response for $url")
}

private class BackendChallengeResolver(vararg decisions: Boolean) :
    me.domino.fa2.domain.challenge.ChallengeResolver {
  private val queue = ArrayDeque(decisions.toList())
  val requests = mutableListOf<me.domino.fa2.domain.challenge.CfChallengeSignal>()

  override suspend fun awaitResolution(
      challenge: me.domino.fa2.domain.challenge.CfChallengeSignal
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
