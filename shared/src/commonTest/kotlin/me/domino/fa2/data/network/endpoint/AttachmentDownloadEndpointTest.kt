package me.domino.fa2.data.fa.media

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.fa.core.InMemoryCookiePersistence
import me.domino.fa2.data.fa.core.createTestUserAgentStorage
import me.domino.fa2.data.fa.session.CfChallengeSignal
import me.domino.fa2.data.fa.session.ChallengeResolver
import me.domino.fa2.data.fa.session.FaCookiesStorage

class AttachmentDownloadEndpointTest {
  @Test
  fun downloadsBinaryAttachmentWithSharedClient() = runTest {
    var capturedAccept: String? = null
    val client =
        createCachedDownloadHttpClient(
            engine =
                MockEngine { request ->
                  capturedAccept = request.headers[HttpHeaders.Accept]
                  respond(
                      content = byteArrayOf(4, 5, 6),
                      status = HttpStatusCode.OK,
                      headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"),
                  )
                },
            progressTracker = ImageProgressTracker(),
            cookiesStorage = FaCookiesStorage(InMemoryCookiePersistence()),
            userAgentStorage = createTestUserAgentStorage(),
        )
    val endpoint =
        AttachmentDownloadEndpoint(
            client = client,
            challengeResolver = AttachmentDownloadChallengeResolver(true),
        )

    val result = endpoint.fetch("https://www.furaffinity.net/download/file.bin", "file.bin")

    val success = assertIs<AttachmentDownloadResult.Success>(result)
    assertEquals(listOf<Byte>(4, 5, 6), success.payload.bytes.toList())
    assertEquals("application/octet-stream", success.payload.contentType)
    assertEquals("*/*", capturedAccept)
  }

  @Test
  fun resolvesCloudflareChallengeAndRetriesDownload() = runTest {
    var requestCount = 0
    val resolver = AttachmentDownloadChallengeResolver(true)
    val client =
        createCachedDownloadHttpClient(
            engine =
                MockEngine {
                  requestCount += 1
                  if (requestCount == 1) {
                    respond(
                        content =
                            "<html><head><title>Just a moment...</title></head><body>challenge</body></html>",
                        status = HttpStatusCode.Forbidden,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf("text/html"),
                                "Server" to listOf("cloudflare"),
                                "CF-Ray" to listOf("ray-1"),
                            ),
                    )
                  } else {
                    respond(content = byteArrayOf(7, 8), status = HttpStatusCode.OK)
                  }
                },
            progressTracker = ImageProgressTracker(),
            cookiesStorage = FaCookiesStorage(InMemoryCookiePersistence()),
            userAgentStorage = createTestUserAgentStorage(),
        )
    val endpoint =
        AttachmentDownloadEndpoint(
            client = client,
            challengeResolver = resolver,
        )

    val result = endpoint.fetch("https://www.furaffinity.net/download/file.bin", "file.bin")

    val success = assertIs<AttachmentDownloadResult.Success>(result)
    assertEquals(listOf<Byte>(7, 8), success.payload.bytes.toList())
    assertEquals(1, resolver.signals.size)
    assertEquals("ray-1", resolver.signals.single().cfRay)
    assertEquals(2, requestCount)
  }
}

private class AttachmentDownloadChallengeResolver(vararg decisions: Boolean) : ChallengeResolver {
  private val decisions = ArrayDeque(decisions.toList())
  val signals = mutableListOf<CfChallengeSignal>()

  override suspend fun awaitResolution(challenge: CfChallengeSignal): Boolean {
    signals += challenge
    return decisions.removeFirstOrNull() ?: false
  }
}
