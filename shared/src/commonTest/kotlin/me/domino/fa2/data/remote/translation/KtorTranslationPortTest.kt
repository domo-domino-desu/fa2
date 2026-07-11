package me.domino.fa2.data.remote.translation

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.settings.OpenAiTranslationConfig
import me.domino.fa2.data.settings.TranslationProvider

class KtorTranslationPortTest {
  @Test
  fun googleClientParsesJoinedSegments() = runTest {
    val client =
        GoogleTranslationClient(
            transport = mockTransport("""[[["你好","hello",null,null,1]],null,"en"]""")
        )

    val translated =
        client.translate(
            TranslationRequest(provider = TranslationProvider.GOOGLE, sourceText = "hello")
        )

    assertEquals("你好", translated)
  }

  @Test
  fun microsoftClientRequestsTokenThenTranslation() = runTest {
    val requests = mutableListOf<HttpRequestData>()
    val transport =
        mockTransport("token-123", """[{"translations":[{"text":"你好"}]}]""", requests = requests)
    val client = MicrosoftTranslationClient(transport)

    val translated =
        client.translate(
            TranslationRequest(provider = TranslationProvider.MICROSOFT, sourceText = "hello")
        )

    assertEquals("你好", translated)
    assertEquals("https://edge.microsoft.com/translate/auth", requests[0].url.toString())
    assertEquals(
        "https://api-edge.cognitive.microsofttranslator.com/translate",
        requests[1].url.toString().substringBefore('?'),
    )
  }

  @Test
  fun openAiClientFlattensArrayContentAndStripsThinkTag() = runTest {
    val requests = mutableListOf<HttpRequestData>()
    val transport =
        mockTransport(
            """
            {
              "choices": [
                {
                  "message": {
                    "content": [
                      {"text": "<think>ignore</think>"},
                      {"text": "译文"}
                    ]
                  }
                }
              ]
            }
            """
                .trimIndent(),
            requests = requests,
        )
    val client = OpenAiCompatibleTranslationClient(transport = transport)

    val translated =
        client.translate(
            TranslationRequest(
                provider = TranslationProvider.OPENAI_COMPATIBLE,
                sourceText = "hello",
                openAiConfig = OpenAiTranslationConfig(apiKey = "key"),
            )
        )

    assertEquals("译文", translated)
    val requestBody = (requests.single().body as TextContent).text
    assertEquals(true, requestBody.contains("hello"))
    assertEquals(true, requestBody.contains("简体中文"))
    assertEquals(true, requestBody.contains("%%"))
    assertEquals(false, requestBody.contains("{INPUT}"))
    assertEquals(false, requestBody.contains("{TARGET_LANG}"))
    assertEquals(false, requestBody.contains("{SEPARATOR}"))
  }

  @Test
  fun portDispatchesByProvider() = runTest {
    val port =
        KtorTranslationPort(
            googleClient = FakeProviderClient("google"),
            microsoftClient = FakeProviderClient("microsoft"),
            openAiClient = FakeProviderClient("openai"),
        )

    assertEquals(
        "google",
        port.translate(
            TranslationRequest(provider = TranslationProvider.GOOGLE, sourceText = "hello")
        ),
    )
    assertEquals(
        "microsoft",
        port.translate(
            TranslationRequest(provider = TranslationProvider.MICROSOFT, sourceText = "hello")
        ),
    )
    assertEquals(
        "openai",
        port.translate(
            TranslationRequest(
                provider = TranslationProvider.OPENAI_COMPATIBLE,
                sourceText = "hello",
                openAiConfig = OpenAiTranslationConfig(apiKey = "key"),
            )
        ),
    )
  }
}

private fun mockTransport(
    vararg bodies: String,
    requests: MutableList<HttpRequestData> = mutableListOf(),
): TranslationHttpTransport {
  val responses = ArrayDeque(bodies.toList())
  val client =
      HttpClient(
          MockEngine { request ->
            requests += request
            respond(
                content = responses.removeFirst(),
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
          }
      )
  return TranslationHttpTransport(client)
}

private class FakeProviderClient(private val result: String) : TranslationProviderClient {
  override suspend fun translate(request: TranslationRequest): String = result
}
