package me.domino.fa2.data.translation

import io.ktor.http.ContentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import me.domino.fa2.domain.translation.TranslationRequest

internal class GoogleTranslationClient(
    private val transport: TranslationHttpTransport,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TranslationProviderClient {
  override suspend fun translate(request: TranslationRequest): String {
    val response =
        transport.get(
            url = googleTranslateEndpoint,
            request =
                TranslationHttpRequest(
                    parameters =
                        listOf(
                            "client" to "gtx",
                            "sl" to mapSourceLanguage(request.sourceLanguageCode),
                            "tl" to mapTargetLanguage(request.targetLanguageCode),
                            "dt" to "t",
                            "strip" to "1",
                            "nonced" to "1",
                            "q" to request.sourceText,
                        ),
                    accept = ContentType.Application.Json,
                ),
        )
    ensureTranslationSuccess(response.statusCode, response.body, provider = "Google")

    val root = json.parseToJsonElement(response.body).jsonArray
    val chunks = root.getOrNull(0)?.jsonArray.orEmpty()
    val translated =
        chunks
            .mapNotNull { it as? JsonArray }
            .mapNotNull { it.getOrNull(0)?.jsonPrimitive?.contentOrNull }
            .joinToString("")
            .trim()

    return ensureTranslatedNonBlank(provider = "Google", translated = translated)
  }

  private fun mapSourceLanguage(sourceLanguageCode: String): String =
      if (sourceLanguageCode.equals("auto", ignoreCase = true)) {
        "auto"
      } else {
        sourceLanguageCode
      }

  private fun mapTargetLanguage(targetLanguageCode: String): String =
      when (targetLanguageCode.lowercase()) {
        "zh-cn" -> "zh-CN"
        "zh-tw" -> "zh-TW"
        else -> targetLanguageCode
      }

  private companion object {
    private const val googleTranslateEndpoint =
        "https://translate.googleapis.com/translate_a/single"
  }
}
