package me.domino.fa2.domain.ocr

data class NormalizedImagePoint(
    val x: Float,
    val y: Float,
)

data class RecognizedTextBlock(
    val text: String,
    val points: List<NormalizedImagePoint>,
    val confidence: Float? = null,
)

data class ImageOcrResult(
    val blocks: List<RecognizedTextBlock>,
)

interface ImageTextRecognitionPort {
  suspend fun recognize(imageBytes: ByteArray): ImageOcrResult
}
