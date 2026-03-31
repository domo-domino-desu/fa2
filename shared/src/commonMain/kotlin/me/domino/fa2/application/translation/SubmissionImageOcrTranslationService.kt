package me.domino.fa2.application.translation

import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.domain.translation.SubmissionDescriptionBlockResult
import me.domino.fa2.domain.translation.SubmissionTranslationChunkPlanner
import me.domino.fa2.domain.translation.SubmissionTranslationResultAligner
import me.domino.fa2.domain.translation.TranslationPort

class SubmissionImageOcrTranslationService(
    translationPort: TranslationPort,
    private val settingsService: AppSettingsService,
) {
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
    if (sourceTexts.isEmpty()) return emptyList()

    settingsService.ensureLoaded()
    val settings = settingsService.settings.value
    val chunks =
        chunkPlanner.buildChunks(
            sourceTexts = sourceTexts,
            chunkWordLimit = settings.translationChunkWordLimit,
        )
    val results =
        MutableList<SubmissionImageOcrTranslationResult>(sourceTexts.size) {
          SubmissionImageOcrTranslationResult.Empty
        }

    chunkExecutor.translate(chunks = chunks, settings = settings) { startIndex, chunkResults ->
      chunkResults.forEachIndexed { offset, result ->
        results[startIndex + offset] = result.toImageOcrTranslationResult()
      }
    }
    return results.toList()
  }

  suspend fun translateText(sourceText: String): SubmissionImageOcrTranslationResult {
    if (sourceText.isBlank()) return SubmissionImageOcrTranslationResult.Empty
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
