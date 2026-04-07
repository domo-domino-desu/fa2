package me.domino.fa2.application.translation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.domain.translation.SubmissionDescriptionBlockResult
import me.domino.fa2.domain.translation.SubmissionTranslationChunk
import me.domino.fa2.util.logging.FaLog

internal class SubmissionTranslationChunkExecutor(
    private val chunkTranslator: SubmissionTranslationChunkTranslator,
) {
  private val log = FaLog.withTag("TranslationChunkExecutor")

  suspend fun translate(
      chunks: List<SubmissionTranslationChunk>,
      settings: AppSettings,
      onChunkResult: (startIndex: Int, results: List<SubmissionDescriptionBlockResult>) -> Unit,
  ) {
    if (chunks.isEmpty()) {
      log.d { "翻译Chunk执行 -> 跳过(空chunks)" }
      return
    }
    log.d {
      "翻译Chunk执行 -> 开始(chunks=${chunks.size},concurrency=${settings.translationMaxConcurrency})"
    }

    coroutineScope {
      val resultChannel =
          Channel<Pair<Int, List<SubmissionDescriptionBlockResult>>>(
              capacity = chunks.size.coerceAtLeast(1)
          )
      val semaphore = Semaphore(settings.translationMaxConcurrency)

      chunks.forEach { chunk ->
        launch {
          semaphore.withPermit {
            log.d { "翻译Chunk执行 -> 提交(start=${chunk.startIndex},size=${chunk.sourceTexts.size})" }
            resultChannel.send(chunk.startIndex to chunkTranslator.translate(chunk, settings))
          }
        }
      }

      repeat(chunks.size) {
        val (startIndex, results) = resultChannel.receive()
        log.d { "翻译Chunk执行 -> 收到结果(start=$startIndex,count=${results.size})" }
        onChunkResult(startIndex, results)
      }
      resultChannel.close()
    }
    log.d { "翻译Chunk执行 -> 完成(chunks=${chunks.size})" }
  }
}
