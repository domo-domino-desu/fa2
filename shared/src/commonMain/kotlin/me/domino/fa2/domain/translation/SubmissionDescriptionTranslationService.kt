package me.domino.fa2.domain.translation

import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.translation.SubmissionTextTranslationEngine
import me.domino.fa2.data.translation.TextTranslationResult
import me.domino.fa2.utils.html.HtmlTextBlock
import me.domino.fa2.utils.logging.FaLog

/** submission 描述翻译编排服务。 */
class SubmissionDescriptionTranslationService(
    private val translationEngine: SubmissionTextTranslationEngine,
    private val settingsService: AppSettingsService,
) {
  private val log = FaLog.withTag("SubmissionDescriptionTranslation")

  /** 从 description HTML 中提取可翻译段。 */
  fun extractBlocks(descriptionHtml: String): List<SubmissionDescriptionBlock> =
      translationEngine
          .extractDescriptionBlocks(descriptionHtml)
          .map(HtmlTextBlock::toDomainBlock)
          .also { blocks -> log.d { "描述翻译 -> 提取区块(count=${blocks.size})" } }

  /** 按当前设置分块翻译并回传每段状态。 */
  suspend fun translateBlocks(
      blocks: List<SubmissionDescriptionBlock>,
      onBlockResult: (index: Int, result: SubmissionDescriptionBlockResult) -> Unit,
  ) {
    if (blocks.isEmpty()) {
      log.d { "描述翻译 -> 跳过(空区块)" }
      return
    }

    settingsService.ensureLoaded()
    val settings = settingsService.settings.value

    translationEngine.translateTexts(
        sourceTexts = blocks.map { block -> block.sourceText },
        settings = settings,
        logLabel = "描述翻译",
    ) { index, result ->
      onBlockResult(index, result.toDomainResult())
    }
  }
}

private fun HtmlTextBlock.toDomainBlock(): SubmissionDescriptionBlock =
    SubmissionDescriptionBlock(originalHtml = originalHtml, sourceText = sourceText)

private fun TextTranslationResult.toDomainResult(): SubmissionDescriptionBlockResult =
    when (this) {
      is TextTranslationResult.Success -> SubmissionDescriptionBlockResult.Success(translatedText)
      TextTranslationResult.Empty -> SubmissionDescriptionBlockResult.EmptyResult
      is TextTranslationResult.Failure -> SubmissionDescriptionBlockResult.Failure(cause)
    }
