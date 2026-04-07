package me.domino.fa2.application.translation

import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.domain.translation.SubmissionDescriptionBlockResult
import me.domino.fa2.domain.translation.SubmissionTranslationChunkPlanner
import me.domino.fa2.domain.translation.SubmissionTranslationResultAligner
import me.domino.fa2.domain.translation.TranslationPort
import me.domino.fa2.util.logging.FaLog

class SubmissionImageOcrTranslationService(
    translationPort: TranslationPort,
    private val settingsService: AppSettingsService,
) {
  private val log = FaLog.withTag("SubmissionImageOcrTranslation")
  private val chunkPlanner = SubmissionTranslationChunkPlanner()
  private val resultAligner = SubmissionTranslationResultAligner()
  private val chunkExecutor =
      SubmissionTranslationChunkExecutor(
          chunkTranslator =
              SubmissionTranslationChunkTranslator(
                  translationPort = translationPort,
                  resultAligner = resultAligner,
              )
      )

  suspend fun translateTexts(sourceTexts: List<String>): List<SubmissionImageOcrTranslationResult> {
    if (sourceTexts.isEmpty()) {
      log.d { "图片OCR翻译 -> 跳过(空文本列表)" }
      return emptyList()
    }

    settingsService.ensureLoaded()
    val settings = settingsService.settings.value
    val chunks =
        chunkPlanner.buildChunks(
            sourceTexts = sourceTexts,
            chunkWordLimit = settings.translationChunkWordLimit,
        )
    log.i {
      "图片OCR翻译 -> 开始(texts=${sourceTexts.size},chunks=${chunks.size},concurrency=${settings.translationMaxConcurrency},provider=${settings.translationProvider})"
    }
    val results =
        MutableList<SubmissionImageOcrTranslationResult>(sourceTexts.size) {
          SubmissionImageOcrTranslationResult.Empty
        }

    chunkExecutor.translate(chunks = chunks, settings = settings) { startIndex, chunkResults ->
      log.d { "图片OCR翻译 -> 区块结果(start=$startIndex,count=${chunkResults.size})" }
      chunkResults.forEachIndexed { offset, result ->
        results[startIndex + offset] = result.toImageOcrTranslationResult()
      }
    }
    log.i { "图片OCR翻译 -> 完成(texts=${sourceTexts.size},chunks=${chunks.size})" }
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

private fun SubmissionDescriptionBlockResult.toImageOcrTranslationResult():
    SubmissionImageOcrTranslationResult =
    when (this) {
      is SubmissionDescriptionBlockResult.Success ->
          SubmissionImageOcrTranslationResult.Success(translatedText)
      SubmissionDescriptionBlockResult.EmptyResult -> SubmissionImageOcrTranslationResult.Empty
      is SubmissionDescriptionBlockResult.Failure ->
          SubmissionImageOcrTranslationResult.Failure(
              cause.message?.takeIf { it.isNotBlank() } ?: cause.toString()
          )
    }
