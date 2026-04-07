package me.domino.fa2.application.ocr

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import me.domino.fa2.domain.ocr.ImageOcrResult
import me.domino.fa2.domain.ocr.ImageTextRecognitionPort
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizeUrl

fun interface SubmissionImageOcrService {
  suspend fun recognize(imageUrl: String): ImageOcrResult
}

class RemoteSubmissionImageOcrService(
    private val client: HttpClient,
    private val recognitionPort: ImageTextRecognitionPort,
) : SubmissionImageOcrService {
  private val log = FaLog.withTag("SubmissionImageOcrService")

  override suspend fun recognize(imageUrl: String): ImageOcrResult {
    log.i { "图片OCR -> 开始(url=${summarizeUrl(imageUrl)})" }
    val response = client.get(imageUrl)
    if (!response.status.isSuccess()) {
      log.w { "图片OCR -> 下载失败(status=${response.status})" }
      throw IllegalStateException("Image OCR failed to download image: ${response.status}")
    }
    val imageBytes = response.body<ByteArray>()
    if (imageBytes.isEmpty()) {
      log.w { "图片OCR -> 下载空内容" }
      throw IllegalStateException("Image OCR downloaded empty image payload")
    }
    return recognitionPort.recognize(imageBytes).also {
      log.i { "图片OCR -> 成功(bytes=${imageBytes.size})" }
    }
  }
}
