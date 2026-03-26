package me.domino.fa2.data.translation

import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.TranslationProvider

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
