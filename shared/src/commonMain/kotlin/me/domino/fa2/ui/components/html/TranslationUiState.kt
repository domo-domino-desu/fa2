package me.domino.fa2.ui.components.html

import me.domino.fa2.domain.translation.SubmissionDescriptionBlock

data class TranslationUiState(
    val sourceKey: String,
    val sourceHtml: String,
    val sourceFileName: String? = null,
    val sourceMode: TranslationSourceMode = TranslationSourceMode.RAW,
    val showTranslation: Boolean = false,
    val rawVariant: TranslationVariantUiState,
    val wrappedVariant: TranslationVariantUiState,
) {
  val isWrapped: Boolean
    get() = sourceMode == TranslationSourceMode.WRAPPED

  val sourceBlocks: List<SubmissionDescriptionBlock>
    get() = variantOf(sourceMode).sourceBlocks

  val blocks: List<SubmissionDescriptionDisplayBlock>
    get() =
        if (showTranslation) {
          variantOf(sourceMode).blocks
        } else {
          variantOf(sourceMode)
              .toIdleDisplayBlocks(
                  useWrappedOriginalText = sourceMode == TranslationSourceMode.WRAPPED
              )
        }

  val translating: Boolean
    get() = variantOf(sourceMode).translating

  val hasTriggered: Boolean
    get() = variantOf(sourceMode).hasTriggered
}

enum class TranslationSourceMode {
  RAW,
  WRAPPED,
}

data class TranslationVariantUiState(
    val sourceBlocks: List<SubmissionDescriptionBlock>,
    val blocks: List<SubmissionDescriptionDisplayBlock>,
    val translating: Boolean = false,
    val hasTriggered: Boolean = false,
)

internal fun TranslationVariantUiState.canReuseTranslationResult(): Boolean =
    hasTriggered &&
        blocks.none { block -> block.status == SubmissionDescriptionTranslationStatus.FAILURE }

internal fun TranslationUiState.variantOf(mode: TranslationSourceMode): TranslationVariantUiState =
    when (mode) {
      TranslationSourceMode.RAW -> rawVariant
      TranslationSourceMode.WRAPPED -> wrappedVariant
    }

internal fun TranslationUiState.withVariant(
    mode: TranslationSourceMode,
    variant: TranslationVariantUiState,
): TranslationUiState =
    when (mode) {
      TranslationSourceMode.RAW -> copy(rawVariant = variant)
      TranslationSourceMode.WRAPPED -> copy(wrappedVariant = variant)
    }

internal fun TranslationVariantUiState.toIdleDisplayBlocks(
    useWrappedOriginalText: Boolean
): List<SubmissionDescriptionDisplayBlock> =
    sourceBlocks.map { block ->
      block.toDisplayBlock(
          translated = null,
          status = SubmissionDescriptionTranslationStatus.IDLE,
          useWrappedOriginalText = useWrappedOriginalText,
      )
    }

internal fun SubmissionDescriptionBlock.toDisplayBlock(
    translated: String?,
    status: SubmissionDescriptionTranslationStatus,
    useWrappedOriginalText: Boolean,
): SubmissionDescriptionDisplayBlock =
    SubmissionDescriptionDisplayBlock(
        originalHtml = originalHtml,
        originalText = sourceText.takeIf { useWrappedOriginalText },
        translated = translated,
        status = status,
    )
