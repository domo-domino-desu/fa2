package me.domino.fa2.data.translation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.domino.fa2.data.settings.AppSettings

internal class SubmissionTranslationChunkExecutor(
    private val chunkTranslator: SubmissionTranslationChunkTranslator,
) {
  suspend fun translate(
      chunks: List<SubmissionTranslationChunk>,
      settings: AppSettings,
      onChunkResult: (startIndex: Int, results: List<SubmissionDescriptionBlockResult>) -> Unit,
  ) {
    if (chunks.isEmpty()) return

    coroutineScope {
      val resultChannel =
          Channel<Pair<Int, List<SubmissionDescriptionBlockResult>>>(
              capacity = chunks.size.coerceAtLeast(1)
          )
      val semaphore = Semaphore(settings.translationMaxConcurrency)

      chunks.forEach { chunk ->
        launch {
          semaphore.withPermit {
            resultChannel.send(chunk.startIndex to chunkTranslator.translate(chunk, settings))
          }
        }
      }

      repeat(chunks.size) {
        val (startIndex, results) = resultChannel.receive()
        onChunkResult(startIndex, results)
      }
      resultChannel.close()
    }
  }
}
