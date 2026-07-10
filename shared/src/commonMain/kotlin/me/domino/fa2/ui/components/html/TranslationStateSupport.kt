package me.domino.fa2.ui.components.html

import me.domino.fa2.domain.translation.SubmissionDescriptionBlock
import me.domino.fa2.domain.translation.SubmissionDescriptionBlockResult
import me.domino.fa2.domain.translation.SubmissionDescriptionTranslationService

internal fun resolveTranslationState(
    sourceHtml: String,
    sourceFileName: String?,
    previous: TranslationUiState?,
    translationService: SubmissionDescriptionTranslationService,
): TranslationUiState {
  val rawSourceBlocks = translationService.extractBlocks(sourceHtml)
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

internal fun List<SubmissionDescriptionBlock>.toIdleVariant(
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
    result: SubmissionDescriptionBlockResult,
): TranslationVariantUiState =
    copy(
        blocks =
            blocks.mapIndexed { currentIndex, block ->
              if (currentIndex != index) {
                block
              } else {
                when (result) {
                  is SubmissionDescriptionBlockResult.Success ->
                      block.copy(
                          translated = result.translatedText,
                          status = SubmissionDescriptionTranslationStatus.SUCCESS,
                      )

                  SubmissionDescriptionBlockResult.EmptyResult ->
                      block.copy(
                          translated = null,
                          status = SubmissionDescriptionTranslationStatus.EMPTY,
                      )

                  is SubmissionDescriptionBlockResult.Failure ->
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

internal fun List<SubmissionDescriptionBlock>.toWrappedSourceBlocks():
    List<SubmissionDescriptionBlock> = map { block ->
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
