package me.domino.fa2.ui.components.html

import me.domino.fa2.data.remote.translation.SubmissionTextTranslationEngine
import me.domino.fa2.data.remote.translation.TextTranslationResult
import me.domino.fa2.utils.html.HtmlTextBlock

internal fun resolveTranslationState(
    sourceHtml: String,
    sourceFileName: String?,
    previous: TranslationUiState?,
    translationService: SubmissionTextTranslationEngine,
): TranslationUiState {
  val rawSourceBlocks = translationService.extractDescriptionBlocks(sourceHtml)
  val wrappedSourceBlocks = rawSourceBlocks.toWrappedSourceBlocks()
  val sourceKey =
      buildTranslationSourceKey(sourceHtml = sourceHtml, sourceFileName = sourceFileName)
  if (previous?.sourceKey == sourceKey) return previous

  return TranslationUiState(
      sourceKey = sourceKey,
      sourceHtml = sourceHtml,
      sourceFileName = sourceFileName,
      rawVariant = rawSourceBlocks.toIdleVariant(useWrappedOriginalText = false),
      wrappedVariant = wrappedSourceBlocks.toIdleVariant(useWrappedOriginalText = true),
  )
}

internal fun buildTranslationSourceKey(sourceHtml: String, sourceFileName: String?): String =
    listOfNotNull(sourceFileName?.trim()?.takeIf { it.isNotBlank() }, sourceHtml)
        .joinToString("\n@@\n")

internal fun List<HtmlTextBlock>.toIdleVariant(
    useWrappedOriginalText: Boolean
): TranslationVariantUiState =
    TranslationVariantUiState(
        sourceBlocks = this,
        blocks =
            map { block ->
              block.toDisplayBlock(
                  translated = null,
                  status = SubmissionDescriptionTranslationStatus.IDLE,
                  useWrappedOriginalText = useWrappedOriginalText,
              )
            },
    )

internal fun TranslationVariantUiState.toPendingState(
    sourceMode: TranslationSourceMode
): TranslationVariantUiState =
    copy(
        blocks =
            sourceBlocks.map { block ->
              block.toDisplayBlock(
                  translated = null,
                  status = SubmissionDescriptionTranslationStatus.PENDING,
                  useWrappedOriginalText = sourceMode == TranslationSourceMode.WRAPPED,
              )
            },
        translating = true,
        hasTriggered = true,
    )

internal fun TranslationVariantUiState.withBlockResult(
    index: Int,
    result: TextTranslationResult,
): TranslationVariantUiState =
    copy(
        blocks =
            blocks.mapIndexed { currentIndex, block ->
              if (currentIndex != index) {
                block
              } else {
                when (result) {
                  is TextTranslationResult.Success ->
                      block.copy(
                          translated = result.translatedText,
                          status = SubmissionDescriptionTranslationStatus.SUCCESS,
                      )

                  TextTranslationResult.Empty ->
                      block.copy(
                          translated = null,
                          status = SubmissionDescriptionTranslationStatus.EMPTY,
                      )

                  is TextTranslationResult.Failure ->
                      block.copy(
                          translated = null,
                          status = SubmissionDescriptionTranslationStatus.FAILURE,
                      )
                }
              }
            }
    )

internal fun TranslationVariantUiState.markPendingBlocksAsFailed(): TranslationVariantUiState =
    copy(
        blocks =
            blocks.map { block ->
              if (block.status == SubmissionDescriptionTranslationStatus.PENDING) {
                block.copy(status = SubmissionDescriptionTranslationStatus.FAILURE)
              } else {
                block
              }
            },
        translating = false,
    )

internal fun List<HtmlTextBlock>.toWrappedSourceBlocks(): List<HtmlTextBlock> = map { block ->
  block.copy(sourceText = wrapBlockSourceText(block.sourceText))
}

internal fun wrapBlockSourceText(text: String): String {
  val paragraphSeparatorPlaceholder = "\u0000"
  return text
      .replace("\n\n", paragraphSeparatorPlaceholder)
      .replace('\n', ' ')
      .replace(Regex(" {2,}"), " ")
      .replace(paragraphSeparatorPlaceholder, "\n\n")
      .trim()
}
