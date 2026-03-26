package me.domino.fa2.application.translation

import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.TranslationProvider
import me.domino.fa2.domain.translation.SubmissionDescriptionBlockResult
import me.domino.fa2.domain.translation.SubmissionTranslationChunk
import me.domino.fa2.domain.translation.SubmissionTranslationResultAligner
import me.domino.fa2.domain.translation.TranslationPort
import me.domino.fa2.domain.translation.TranslationRequest

internal class SubmissionTranslationChunkTranslator(
    private val translationPort: TranslationPort,
    private val resultAligner: SubmissionTranslationResultAligner,
) {
  suspend fun translate(
      chunk: SubmissionTranslationChunk,
      settings: AppSettings,
  ): List<SubmissionDescriptionBlockResult> {
    if (chunk.sourceTexts.all { it.isBlank() }) {
      return List(chunk.sourceTexts.size) { SubmissionDescriptionBlockResult.EmptyResult }
    }

    val payload = chunk.sourceTexts.joinToString(SubmissionTranslationResultAligner.batchSeparator)
    val translatedLines =
        runCatching {
              translationPort
                  .translate(
                      TranslationRequest(
                          provider = settings.translationProvider,
                          sourceText = payload,
                          targetLanguageCode = settings.translationTargetLanguage.languageCode,
                          openAiConfig =
                              settings.openAiTranslationConfig.takeIf {
                                settings.translationProvider ==
                                    TranslationProvider.OPENAI_COMPATIBLE
                              },
                      )
                  )
                  .trim()
            }
            .fold(
                onSuccess = { translated ->
                  if (translated.isBlank()) {
                    List(chunk.sourceTexts.size) { "" }
                  } else {
                    resultAligner.parseChunkTranslation(
                        translated = translated,
                        expectedBlockCount = chunk.sourceTexts.size,
                    )
                  }
                },
                onFailure = { error ->
                  return List(chunk.sourceTexts.size) {
                    SubmissionDescriptionBlockResult.Failure(error)
                  }
                },
            )

    return translatedLines.map { translatedLine ->
      val cleanedTranslation = resultAligner.sanitizeTranslatedBlockText(translatedLine)
      if (cleanedTranslation.isBlank()) {
        SubmissionDescriptionBlockResult.EmptyResult
      } else {
        SubmissionDescriptionBlockResult.Success(cleanedTranslation)
      }
    }
  }
}
