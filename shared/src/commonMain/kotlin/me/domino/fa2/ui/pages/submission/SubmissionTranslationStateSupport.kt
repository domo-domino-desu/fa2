package me.domino.fa2.ui.pages.submission

import me.domino.fa2.application.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.domain.translation.SubmissionDescriptionBlock
import me.domino.fa2.domain.translation.SubmissionDescriptionBlockResult
import me.domino.fa2.ui.state.SubmissionDescriptionTranslationStatus

internal fun resolveTranslationState(
    sourceHtml: String,
    sourceFileName: String?,
    previous: SubmissionTranslationUiState?,
    translationService: SubmissionDescriptionTranslationService,
): SubmissionTranslationUiState {
  val rawSourceBlocks = translationService.extractBlocks(sourceHtml)
  val wrappedSourceBlocks = rawSourceBlocks.toWrappedSourceBlocks()
  val sourceKey =
      buildTranslationSourceKey(sourceHtml = sourceHtml, sourceFileName = sourceFileName)
  if (previous?.sourceKey == sourceKey) return previous

  return SubmissionTranslationUiState(
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
): SubmissionTranslationVariantUiState =
    SubmissionTranslationVariantUiState(
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

internal fun SubmissionTranslationVariantUiState.toPendingState(
    sourceMode: SubmissionTranslationSourceMode
): SubmissionTranslationVariantUiState =
    copy(
        blocks =
            sourceBlocks.map { block ->
              block.toDisplayBlock(
                  translated = null,
                  status = SubmissionDescriptionTranslationStatus.PENDING,
                  useWrappedOriginalText = sourceMode == SubmissionTranslationSourceMode.WRAPPED,
              )
            },
        translating = true,
        hasTriggered = true,
    )

internal fun SubmissionTranslationVariantUiState.withBlockResult(
    index: Int,
    result: SubmissionDescriptionBlockResult,
): SubmissionTranslationVariantUiState =
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

internal fun SubmissionTranslationVariantUiState.markPendingBlocksAsFailed():
    SubmissionTranslationVariantUiState =
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
