package me.domino.fa2.data.translation

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.domino.fa2.domain.translation.TranslationRequest

internal class MicrosoftTranslationClient(
    private val transport: TranslationHttpTransport,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TranslationProviderClient {
  override suspend fun translate(request: TranslationRequest): String {
    val tokenResponse = transport.get(microsoftTokenEndpoint)
    ensureTranslationSuccess(
        tokenResponse.statusCode,
        tokenResponse.body,
        provider = "Microsoft token",
    )
    val token = tokenResponse.body.trim()
    if (token.isBlank()) {
      throw IllegalStateException("Microsoft token is blank")
    }

    val parameters = buildList {
      add("api-version" to "3.0")
      add("to" to mapTargetLanguage(request.targetLanguageCode))
      add("includeSentenceLength" to "true")
      add("textType" to "html")

      val source = request.sourceLanguageCode.trim()
      if (!source.equals("auto", ignoreCase = true) && source.isNotBlank()) {
        add("from" to source)
      }
    }

    val response =
        transport.post(
            url = microsoftTranslateEndpoint,
            request =
                TranslationHttpRequest(
                    parameters = parameters,
                    headers = listOf(HttpHeaders.Authorization to "Bearer $token"),
                    accept = ContentType.Application.Json,
                    contentType = ContentType.Application.Json,
                    body =
                        buildJsonArray {
                              add(
                                  buildJsonObject { put("Text", JsonPrimitive(request.sourceText)) }
                              )
                            }
                            .toString(),
                ),
        )
    ensureTranslationSuccess(response.statusCode, response.body, provider = "Microsoft")

    val root = json.parseToJsonElement(response.body).jsonArray
    val translated =
        root
            .firstOrNull()
            ?.jsonObject
            ?.get("translations")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            .orEmpty()

    return ensureTranslatedNonBlank(provider = "Microsoft", translated = translated)
  }

  private fun mapTargetLanguage(targetLanguageCode: String): String =
      when (targetLanguageCode.lowercase()) {
        "zh-cn" -> "zh-Hans"
        "zh-tw" -> "zh-Hant"
        else -> targetLanguageCode
      }

  private companion object {
    private const val microsoftTokenEndpoint = "https://edge.microsoft.com/translate/auth"
    private const val microsoftTranslateEndpoint =
        "https://api-edge.cognitive.microsofttranslator.com/translate"
  }
}
