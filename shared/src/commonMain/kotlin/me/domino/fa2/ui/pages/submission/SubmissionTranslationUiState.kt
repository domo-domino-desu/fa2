package me.domino.fa2.ui.pages.submission

import me.domino.fa2.domain.translation.SubmissionDescriptionBlock
import me.domino.fa2.ui.state.SubmissionDescriptionDisplayBlock
import me.domino.fa2.ui.state.SubmissionDescriptionTranslationStatus

data class SubmissionTranslationUiState(
    val sourceKey: String,
    val sourceHtml: String,
    val sourceFileName: String? = null,
    val sourceMode: SubmissionTranslationSourceMode = SubmissionTranslationSourceMode.RAW,
    val showTranslation: Boolean = false,
    val rawVariant: SubmissionTranslationVariantUiState,
    val wrappedVariant: SubmissionTranslationVariantUiState,
) {
  val isWrapped: Boolean
    get() = sourceMode == SubmissionTranslationSourceMode.WRAPPED

  val sourceBlocks: List<SubmissionDescriptionBlock>
    get() = variantOf(sourceMode).sourceBlocks

  val blocks: List<SubmissionDescriptionDisplayBlock>
    get() =
        if (showTranslation) {
          variantOf(sourceMode).blocks
        } else {
          variantOf(sourceMode)
              .toIdleDisplayBlocks(
                  useWrappedOriginalText = sourceMode == SubmissionTranslationSourceMode.WRAPPED
              )
        }

  val translating: Boolean
    get() = variantOf(sourceMode).translating

  val hasTriggered: Boolean
    get() = variantOf(sourceMode).hasTriggered
}

enum class SubmissionTranslationSourceMode {
  RAW,
  WRAPPED,
}

data class SubmissionTranslationVariantUiState(
    val sourceBlocks: List<SubmissionDescriptionBlock>,
    val blocks: List<SubmissionDescriptionDisplayBlock>,
    val translating: Boolean = false,
    val hasTriggered: Boolean = false,
)

internal fun SubmissionTranslationUiState.variantOf(
    mode: SubmissionTranslationSourceMode
): SubmissionTranslationVariantUiState =
    when (mode) {
      SubmissionTranslationSourceMode.RAW -> rawVariant
      SubmissionTranslationSourceMode.WRAPPED -> wrappedVariant
    }

internal fun SubmissionTranslationUiState.withVariant(
    mode: SubmissionTranslationSourceMode,
    variant: SubmissionTranslationVariantUiState,
): SubmissionTranslationUiState =
    when (mode) {
      SubmissionTranslationSourceMode.RAW -> copy(rawVariant = variant)
      SubmissionTranslationSourceMode.WRAPPED -> copy(wrappedVariant = variant)
    }

internal fun SubmissionTranslationVariantUiState.toIdleDisplayBlocks(
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
