package me.domino.fa2.data.translation

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
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
import me.domino.fa2.data.settings.TranslationProvider

/** 基于 Ktor 的翻译端口实现。 */
class KtorTranslationPort(private val client: HttpClient) : TranslationPort {
  override suspend fun translate(request: TranslationRequest): String {
    val normalized = request.normalized()
    return when (normalized.provider) {
      TranslationProvider.GOOGLE -> translateByGoogle(normalized)
      TranslationProvider.MICROSOFT -> translateByMicrosoft(normalized)
      TranslationProvider.OPENAI_COMPATIBLE -> translateByOpenAiCompatible(normalized)
    }
  }

  private suspend fun translateByGoogle(request: TranslationRequest): String {
    val response =
        client.get(googleTranslateEndpoint) {
          parameter("client", "gtx")
          parameter("sl", mapGoogleSourceLanguage(request.sourceLanguageCode))
          parameter("tl", mapGoogleTargetLanguage(request.targetLanguageCode))
          parameter("dt", "t")
          parameter("strip", 1)
          parameter("nonced", 1)
          parameter("q", request.sourceText)
          accept(ContentType.Application.Json)
        }

    val body = response.bodyAsText()
    ensureSuccess(response.status.value, body, provider = "Google")

    val root = json.parseToJsonElement(body).jsonArray
    val chunks = root.getOrNull(0)?.jsonArray.orEmpty()
    val translated =
        chunks
            .mapNotNull { it as? JsonArray }
            .mapNotNull { it.getOrNull(0)?.jsonPrimitive?.contentOrNull }
            .joinToString("")
            .trim()

    return ensureNonBlank(provider = "Google", translated = translated)
  }

  private suspend fun translateByMicrosoft(request: TranslationRequest): String {
    val tokenResponse = client.get(microsoftTokenEndpoint)
    val tokenBody = tokenResponse.bodyAsText()
    ensureSuccess(tokenResponse.status.value, tokenBody, provider = "Microsoft token")
    val token = tokenBody.trim()
    if (token.isBlank()) {
      throw IllegalStateException("Microsoft token is blank")
    }

    val response =
        client.post(microsoftTranslateEndpoint) {
          parameter("api-version", "3.0")
          parameter("to", mapMicrosoftTargetLanguage(request.targetLanguageCode))
          parameter("includeSentenceLength", "true")
          parameter("textType", "html")

          val source = request.sourceLanguageCode.trim()
          if (!source.equals("auto", ignoreCase = true) && source.isNotBlank()) {
            parameter("from", source)
          }

          contentType(ContentType.Application.Json)
          accept(ContentType.Application.Json)
          header(HttpHeaders.Authorization, "Bearer $token")
          setBody(
              buildJsonArray {
                    add(buildJsonObject { put("Text", JsonPrimitive(request.sourceText)) })
                  }
                  .toString()
          )
        }

    val body = response.bodyAsText()
    ensureSuccess(response.status.value, body, provider = "Microsoft")

    val root = json.parseToJsonElement(body).jsonArray
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

    return ensureNonBlank(provider = "Microsoft", translated = translated)
  }

  private suspend fun translateByOpenAiCompatible(request: TranslationRequest): String {
    val config =
        requireNotNull(request.openAiConfig) {
          "OpenAI compatible translation requires openAiConfig"
        }

    val apiKey = config.apiKey.trim()
    if (apiKey.isBlank()) {
      throw IllegalArgumentException("OpenAI compatible apiKey is blank")
    }

    val baseUrl = normalizeBaseUrl(config.baseUrl)
    val model = config.model.trim().ifBlank { OpenAiTranslationConfig.defaultModel }
    val prompt =
        resolvePromptTemplate(
            template = config.promptTemplate,
            input = request.sourceText,
            targetLanguage = mapTargetDisplayName(request.targetLanguageCode),
        )

    val response =
        client.post("$baseUrl/chat/completions") {
          contentType(ContentType.Application.Json)
          accept(ContentType.Application.Json)
          header(HttpHeaders.Authorization, "Bearer $apiKey")
          setBody(buildChatCompletionsPayload(model = model, userPrompt = prompt).toString())
        }

    val body = response.bodyAsText()
    ensureSuccess(response.status.value, body, provider = "OpenAI compatible")

    val root = json.parseToJsonElement(body).jsonObject
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

    return ensureNonBlank(
        provider = "OpenAI compatible",
        translated = stripThinkTagIfPresent(translated),
    )
  }

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

  private fun normalizeBaseUrl(baseUrl: String): String {
    val normalized = baseUrl.trim().trimEnd('/')
    return if (normalized.isBlank()) OpenAiTranslationConfig.defaultBaseUrl else normalized
  }

  private fun resolvePromptTemplate(
      template: String,
      input: String,
      targetLanguage: String,
  ): String =
      template
          .ifBlank { OpenAiTranslationConfig.defaultPromptTemplate }
          .replace("[TARGET_LANG]", targetLanguage)
          .replace("[SEPARATOR]", SEPARATOR_MARKER)
          .replace("[INPUT]", input)

  private fun ensureSuccess(statusCode: Int, responseBody: String, provider: String) {
    if (statusCode in 200..299) return
    throw IllegalStateException(
        "$provider translation failed: $statusCode body=${responseBody.take(160)}"
    )
  }

  private fun ensureNonBlank(provider: String, translated: String): String {
    if (translated.isBlank()) {
      throw IllegalStateException("$provider translation result is blank")
    }
    return translated
  }

  private fun mapGoogleSourceLanguage(sourceLanguageCode: String): String =
      if (sourceLanguageCode.equals("auto", ignoreCase = true)) {
        "auto"
      } else {
        sourceLanguageCode
      }

  private fun mapGoogleTargetLanguage(targetLanguageCode: String): String =
      when (targetLanguageCode.lowercase()) {
        "zh-cn" -> "zh-CN"
        "zh-tw" -> "zh-TW"
        else -> targetLanguageCode
      }

  private fun mapMicrosoftTargetLanguage(targetLanguageCode: String): String =
      when (targetLanguageCode.lowercase()) {
        "zh-cn" -> "zh-Hans"
        "zh-tw" -> "zh-Hant"
        else -> targetLanguageCode
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

  companion object {
    private val json = Json { ignoreUnknownKeys = true }
    private const val googleTranslateEndpoint =
        "https://translate.googleapis.com/translate_a/single"
    private const val microsoftTokenEndpoint = "https://edge.microsoft.com/translate/auth"
    private const val microsoftTranslateEndpoint =
        "https://api-edge.cognitive.microsofttranslator.com/translate"
    private const val SEPARATOR_MARKER = "%%"
  }
}
