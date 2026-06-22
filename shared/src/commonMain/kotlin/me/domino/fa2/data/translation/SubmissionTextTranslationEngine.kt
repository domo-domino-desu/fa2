package me.domino.fa2.data.translation

import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.utils.html.HtmlTextBlock
import me.domino.fa2.utils.html.HtmlTextBlockExtractor
import me.domino.fa2.utils.logging.FaLog

sealed interface TextTranslationResult {
  data class Success(val translatedText: String) : TextTranslationResult

  data object Empty : TextTranslationResult

  data class Failure(val cause: Throwable) : TextTranslationResult
}

class SubmissionTextTranslationEngine(
    translationPort: TranslationPort,
) {
  private val log = FaLog.withTag("SubmissionTextTranslationEngine")
  private val resultAligner = SubmissionTranslationResultAligner()
  private val blockExtractor =
      HtmlTextBlockExtractor(normalizeText = resultAligner::normalizeTranslationText)
  private val chunkPlanner = SubmissionTranslationChunkPlanner()
  private val chunkExecutor =
      SubmissionTranslationChunkExecutor(
          chunkTranslator =
              SubmissionTranslationChunkTranslator(
                  translationPort = translationPort,
                  resultAligner = resultAligner,
              )
      )

  fun extractDescriptionBlocks(descriptionHtml: String): List<HtmlTextBlock> =
      blockExtractor.extract(descriptionHtml)

  suspend fun translateTexts(
      sourceTexts: List<String>,
      settings: AppSettings,
      logLabel: String,
      onTextResult: (index: Int, result: TextTranslationResult) -> Unit,
  ) {
    if (sourceTexts.isEmpty()) {
      log.d { "$logLabel -> 跳过(空文本列表)" }
      return
    }
    val chunks =
        chunkPlanner.buildChunks(
            sourceTexts = sourceTexts,
            chunkWordLimit = settings.translationChunkWordLimit,
        )
    log.i {
      "$logLabel -> 开始(texts=${sourceTexts.size},chunks=${chunks.size},concurrency=${settings.translationMaxConcurrency},provider=${settings.translationProvider})"
    }
    chunkExecutor.translate(chunks = chunks, settings = settings) { startIndex, results ->
      log.d { "$logLabel -> 区块结果(start=$startIndex,count=${results.size})" }
      results.forEachIndexed { offset, result -> onTextResult(startIndex + offset, result) }
    }
    log.i { "$logLabel -> 完成(texts=${sourceTexts.size},chunks=${chunks.size})" }
  }
}
