package me.domino.fa2.application.ocr

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import me.domino.fa2.domain.ocr.ImageOcrResult
import me.domino.fa2.domain.ocr.ImageTextRecognitionPort

fun interface SubmissionImageOcrService {
  suspend fun recognize(imageUrl: String): ImageOcrResult
}

class RemoteSubmissionImageOcrService(
    private val client: HttpClient,
    private val recognitionPort: ImageTextRecognitionPort,
) : SubmissionImageOcrService {
  override suspend fun recognize(imageUrl: String): ImageOcrResult {
    val response = client.get(imageUrl)
    if (!response.status.isSuccess()) {
      throw IllegalStateException("Image OCR failed to download image: ${response.status}")
    }
    val imageBytes = response.body<ByteArray>()
    if (imageBytes.isEmpty()) {
      throw IllegalStateException("Image OCR downloaded empty image payload")
    }
    return recognitionPort.recognize(imageBytes)
  }
}
