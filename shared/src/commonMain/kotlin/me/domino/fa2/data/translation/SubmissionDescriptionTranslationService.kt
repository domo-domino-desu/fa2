package me.domino.fa2.data.translation

import me.domino.fa2.data.settings.AppSettingsService

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

/** 描述块。 */
data class SubmissionDescriptionBlock(val originalHtml: String, val sourceText: String)

sealed interface SubmissionDescriptionBlockResult {
  data class Success(val translatedText: String) : SubmissionDescriptionBlockResult

  data object EmptyResult : SubmissionDescriptionBlockResult

  data class Failure(val cause: Throwable) : SubmissionDescriptionBlockResult
}
