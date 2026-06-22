package me.domino.fa2.domain.ocr

import me.domino.fa2.data.fa.media.ImageBytesSource
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl

fun interface SubmissionImageOcrService {
  suspend fun recognize(imageUrl: String): ImageOcrResult
}

class DefaultSubmissionImageOcrService(
    private val imageBytesSource: ImageBytesSource,
    private val recognitionPort: ImageTextRecognitionPort,
) : SubmissionImageOcrService {
  private val log = FaLog.withTag("SubmissionImageOcrService")

  override suspend fun recognize(imageUrl: String): ImageOcrResult {
    log.i { "图片OCR -> 开始(url=${summarizeUrl(imageUrl)})" }
    val imageBytes = imageBytesSource.fetch(imageUrl)
    return recognitionPort.recognize(imageBytes).also {
      log.i { "图片OCR -> 成功(bytes=${imageBytes.size})" }
    }
  }
}
