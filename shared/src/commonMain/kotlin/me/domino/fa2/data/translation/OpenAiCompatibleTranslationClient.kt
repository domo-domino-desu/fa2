package me.domino.fa2.data.translation

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.domino.fa2.data.settings.OpenAiTranslationConfig
import me.domino.fa2.domain.translation.TranslationRequest
import me.domino.fa2.util.renderBraceTemplate

internal class OpenAiCompatibleTranslationClient(
    private val transport: TranslationHttpTransport,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TranslationProviderClient {
  override suspend fun translate(request: TranslationRequest): String {
    val config =
        requireNotNull(request.openAiConfig) {
          "OpenAI compatible translation requires openAiConfig"
        }
    val apiKey = config.apiKey.trim()
    if (apiKey.isBlank()) {
      throw IllegalArgumentException("OpenAI compatible apiKey is blank")
    }

    val response =
        transport.post(
            url = "${normalizeBaseUrl(config.baseUrl)}/chat/completions",
            request =
                TranslationHttpRequest(
                    headers = listOf(HttpHeaders.Authorization to "Bearer $apiKey"),
                    accept = ContentType.Application.Json,
                    contentType = ContentType.Application.Json,
                    body =
                        buildChatCompletionsPayload(
                                model =
                                    config.model.trim().ifBlank {
                                      OpenAiTranslationConfig.defaultModel
                                    },
                                userPrompt =
                                    resolvePromptTemplate(
                                        template = config.promptTemplate,
                                        input = request.sourceText,
                                        targetLanguage =
                                            mapTargetDisplayName(request.targetLanguageCode),
                                    ),
                            )
                            .toString(),
                ),
        )
    ensureTranslationSuccess(
        response.statusCode,
        response.body,
        provider = "OpenAI compatible",
    )

    val root = json.parseToJsonElement(response.body).jsonObject
    val contentElement =
        root["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
    val translated =
        when (contentElement) {
              is JsonPrimitive -> contentElement.contentOrNull
              is JsonArray -> flattenMessageContent(contentElement)
              else -> null
            }
            ?.trim()
            .orEmpty()

    return ensureTranslatedNonBlank(
        provider = "OpenAI compatible",
        translated = stripThinkTagIfPresent(translated),
    )
  }

  private fun normalizeBaseUrl(baseUrl: String): String {
    val normalized = baseUrl.trim().trimEnd('/')
    return if (normalized.isBlank()) OpenAiTranslationConfig.defaultBaseUrl else normalized
  }

  private fun resolvePromptTemplate(
      template: String,
      input: String,
      targetLanguage: String,
  ): String =
      renderBraceTemplate(
          template = template.ifBlank { OpenAiTranslationConfig.defaultPromptTemplate },
          values =
              mapOf(
                  "TARGET_LANG" to targetLanguage,
                  "SEPARATOR" to separatorMarker,
                  "INPUT" to input,
              ),
      )

  private fun flattenMessageContent(contentArray: JsonArray): String =
      contentArray
          .mapNotNull { block -> (block as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
          .joinToString(separator = "")

  private fun stripThinkTagIfPresent(text: String): String {
    val trimmed = text.trimStart()
    if (!trimmed.startsWith("<think>", ignoreCase = true)) return text
    val closeIndex = trimmed.indexOf("</think>", ignoreCase = true)
    if (closeIndex < 0) return text
    return trimmed.substring(closeIndex + "</think>".length).trim().ifBlank { text }
  }

  private fun mapTargetDisplayName(targetLanguageCode: String): String =
      when (targetLanguageCode.lowercase()) {
        "zh-cn" -> "简体中文"
        "zh-tw" -> "繁體中文"
        "ja" -> "日本語"
        "en" -> "English"
        else -> targetLanguageCode
      }

  private fun buildChatCompletionsPayload(model: String, userPrompt: String) = buildJsonObject {
    put("model", JsonPrimitive(model))
    put(
        "messages",
        buildJsonArray {
          add(
              buildJsonObject {
                put("role", JsonPrimitive("system"))
                put(
                    "content",
                    JsonPrimitive(
                        "You are a professional translator. Return translated text only."
                    ),
                )
              }
          )
          add(
              buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", JsonPrimitive(userPrompt))
              }
          )
        },
    )
    put("temperature", JsonPrimitive(0.2))
  }

  private companion object {
    private const val separatorMarker = "%%"
  }
}
