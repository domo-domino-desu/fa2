package me.domino.fa2.application.translation

import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.domain.translation.SubmissionDescriptionBlock
import me.domino.fa2.domain.translation.SubmissionDescriptionBlockExtractor
import me.domino.fa2.domain.translation.SubmissionDescriptionBlockResult
import me.domino.fa2.domain.translation.SubmissionTranslationChunkPlanner
import me.domino.fa2.domain.translation.SubmissionTranslationResultAligner
import me.domino.fa2.domain.translation.TranslationPort

/** submission 描述翻译编排服务。 */
class SubmissionDescriptionTranslationService(
    private val translationPort: TranslationPort,
    private val settingsService: AppSettingsService,
) {
  private val resultAligner = SubmissionTranslationResultAligner()
  private val blockExtractor = SubmissionDescriptionBlockExtractor(resultAligner)
  private val chunkPlanner = SubmissionTranslationChunkPlanner()
  private val chunkExecutor =
      SubmissionTranslationChunkExecutor(
          chunkTranslator =
              SubmissionTranslationChunkTranslator(
                  translationPort = translationPort,
                  resultAligner = resultAligner,
              )
      )

  /** 从 description HTML 中提取可翻译段。 */
  fun extractBlocks(descriptionHtml: String): List<SubmissionDescriptionBlock> =
      blockExtractor.extract(descriptionHtml)

  /** 按当前设置分块翻译并回传每段状态。 */
  suspend fun translateBlocks(
      blocks: List<SubmissionDescriptionBlock>,
      onBlockResult: (index: Int, result: SubmissionDescriptionBlockResult) -> Unit,
  ) {
    if (blocks.isEmpty()) return

    settingsService.ensureLoaded()
    val settings = settingsService.settings.value
    val chunks =
        chunkPlanner.buildChunks(
            sourceTexts = blocks.map { block -> block.sourceText },
            chunkWordLimit = settings.translationChunkWordLimit,
        )

    chunkExecutor.translate(chunks = chunks, settings = settings) { startIndex, results ->
      results.forEachIndexed { offset, result -> onBlockResult(startIndex + offset, result) }
    }
  }
}
