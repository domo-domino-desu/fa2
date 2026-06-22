package me.domino.fa2.domain.translation

import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.translation.SubmissionTextTranslationEngine
import me.domino.fa2.data.translation.TextTranslationResult
import me.domino.fa2.utils.logging.FaLog

class SubmissionImageOcrTranslationService(
    private val translationEngine: SubmissionTextTranslationEngine,
    private val settingsService: AppSettingsService,
) {
  private val log = FaLog.withTag("SubmissionImageOcrTranslation")

  suspend fun translateTexts(sourceTexts: List<String>): List<SubmissionImageOcrTranslationResult> {
    if (sourceTexts.isEmpty()) {
      log.d { "图片OCR翻译 -> 跳过(空文本列表)" }
      return emptyList()
    }

    settingsService.ensureLoaded()
    val settings = settingsService.settings.value
    val results =
        MutableList<SubmissionImageOcrTranslationResult>(sourceTexts.size) {
          SubmissionImageOcrTranslationResult.Empty
        }

    translationEngine.translateTexts(
        sourceTexts = sourceTexts,
        settings = settings,
        logLabel = "图片OCR翻译",
    ) { index, result ->
      results[index] = result.toImageOcrTranslationResult()
    }
    return results.toList()
  }

  suspend fun translateText(sourceText: String): SubmissionImageOcrTranslationResult {
    if (sourceText.isBlank()) {
      log.d { "图片OCR翻译 -> 跳过(空文本)" }
      return SubmissionImageOcrTranslationResult.Empty
    }
    return translateTexts(listOf(sourceText)).singleOrNull()
        ?: SubmissionImageOcrTranslationResult.Empty
  }
}

sealed interface SubmissionImageOcrTranslationResult {
  data class Success(
      val translatedText: String,
  ) : SubmissionImageOcrTranslationResult

  data object Empty : SubmissionImageOcrTranslationResult

  data class Failure(
      val message: String,
  ) : SubmissionImageOcrTranslationResult
}

private fun TextTranslationResult.toImageOcrTranslationResult():
    SubmissionImageOcrTranslationResult =
    when (this) {
      is TextTranslationResult.Success ->
          SubmissionImageOcrTranslationResult.Success(translatedText)
      TextTranslationResult.Empty -> SubmissionImageOcrTranslationResult.Empty
      is TextTranslationResult.Failure ->
          SubmissionImageOcrTranslationResult.Failure(
              cause.message?.takeIf { it.isNotBlank() } ?: cause.toString()
          )
    }
