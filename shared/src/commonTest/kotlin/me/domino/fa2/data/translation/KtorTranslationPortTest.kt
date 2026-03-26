package me.domino.fa2.data.translation

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
            transport =
                FakeTranslationHttpTransport(
                    getResponses =
                        ArrayDeque(
                            listOf(
                                TranslationHttpResponse(
                                    statusCode = 200,
                                    body = """[[["你好","hello",null,null,1]],null,"en"]""",
                                )
                            )
                        )
                )
        )

    val translated =
        client.translate(
            TranslationRequest(
                provider = TranslationProvider.GOOGLE,
                sourceText = "hello",
            )
        )

    assertEquals("你好", translated)
  }

  @Test
  fun microsoftClientRequestsTokenThenTranslation() = runTest {
    val transport =
        FakeTranslationHttpTransport(
            getResponses = ArrayDeque(listOf(TranslationHttpResponse(200, "token-123"))),
            postResponses =
                ArrayDeque(
                    listOf(
                        TranslationHttpResponse(
                            statusCode = 200,
                            body = """[{"translations":[{"text":"你好"}]}]""",
                        )
                    )
                ),
        )
    val client = MicrosoftTranslationClient(transport)

    val translated =
        client.translate(
            TranslationRequest(
                provider = TranslationProvider.MICROSOFT,
                sourceText = "hello",
            )
        )

    assertEquals("你好", translated)
    assertEquals("https://edge.microsoft.com/translate/auth", transport.getRequests.single().first)
    assertEquals(
        "https://api-edge.cognitive.microsofttranslator.com/translate",
        transport.postRequests.single().first,
    )
  }

  @Test
  fun openAiClientFlattensArrayContentAndStripsThinkTag() = runTest {
    val client =
        OpenAiCompatibleTranslationClient(
            transport =
                FakeTranslationHttpTransport(
                    postResponses =
                        ArrayDeque(
                            listOf(
                                TranslationHttpResponse(
                                    statusCode = 200,
                                    body =
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
                                )
                            )
                        )
                )
        )

    val translated =
        client.translate(
            TranslationRequest(
                provider = TranslationProvider.OPENAI_COMPATIBLE,
                sourceText = "hello",
                openAiConfig = OpenAiTranslationConfig(apiKey = "key"),
            )
        )

    assertEquals("译文", translated)
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
            TranslationRequest(
                provider = TranslationProvider.GOOGLE,
                sourceText = "hello",
            )
        ),
    )
    assertEquals(
        "microsoft",
        port.translate(
            TranslationRequest(
                provider = TranslationProvider.MICROSOFT,
                sourceText = "hello",
            )
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

private class FakeTranslationHttpTransport(
    val getResponses: ArrayDeque<TranslationHttpResponse> = ArrayDeque(),
    val postResponses: ArrayDeque<TranslationHttpResponse> = ArrayDeque(),
) : TranslationHttpTransport {
  val getRequests = mutableListOf<Pair<String, TranslationHttpRequest>>()
  val postRequests = mutableListOf<Pair<String, TranslationHttpRequest>>()

  override suspend fun get(
      url: String,
      request: TranslationHttpRequest,
  ): TranslationHttpResponse {
    getRequests += url to request
    return getResponses.removeFirst()
  }

  override suspend fun post(
      url: String,
      request: TranslationHttpRequest,
  ): TranslationHttpResponse {
    postRequests += url to request
    return postResponses.removeFirst()
  }
}

private class FakeProviderClient(private val result: String) : TranslationProviderClient {
  override suspend fun translate(request: TranslationRequest): String = result
}
