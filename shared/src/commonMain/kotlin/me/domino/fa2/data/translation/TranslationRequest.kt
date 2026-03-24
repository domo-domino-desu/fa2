package me.domino.fa2.data.translation

import me.domino.fa2.data.settings.OpenAiTranslationConfig
import me.domino.fa2.data.settings.TranslationProvider

/** 翻译请求。 */
data class TranslationRequest(
  val provider: TranslationProvider,
  val sourceText: String,
  val sourceLanguageCode: String = defaultSourceLanguageCode,
  val targetLanguageCode: String = defaultTargetLanguageCode,
  val openAiConfig: OpenAiTranslationConfig? = null,
) {
  init {
    require(sourceText.isNotBlank()) { "Translation source text must not be blank." }
    require(sourceLanguageCode.isNotBlank()) {
      "Translation source language code must not be blank."
    }
    require(targetLanguageCode.isNotBlank()) {
      "Translation target language code must not be blank."
    }
    if (provider == TranslationProvider.OPENAI_COMPATIBLE) {
      require(openAiConfig != null) { "OpenAI compatible translation requires openAiConfig." }
    }
  }

  fun normalized(): TranslationRequest =
    copy(
      sourceLanguageCode = sourceLanguageCode.trim(),
      targetLanguageCode = targetLanguageCode.trim(),
    )

  companion object {
    const val defaultSourceLanguageCode: String = "auto"
    const val defaultTargetLanguageCode: String = "zh-CN"
  }
}

/** 翻译端口。 */
interface TranslationPort {
  suspend fun translate(request: TranslationRequest): String
}
