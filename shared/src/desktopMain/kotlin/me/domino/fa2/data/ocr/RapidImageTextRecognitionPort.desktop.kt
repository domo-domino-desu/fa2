package me.domino.fa2.data.ocr

import io.github.hzkitty.RapidOCR
import io.github.hzkitty.entity.RecResult
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import me.domino.fa2.domain.ocr.ImageOcrResult
import me.domino.fa2.domain.ocr.ImageTextRecognitionPort
import me.domino.fa2.domain.ocr.NormalizedImagePoint
import me.domino.fa2.domain.ocr.RecognizedTextBlock
import org.opencv.core.Point

class RapidImageTextRecognitionPort : ImageTextRecognitionPort {
  private val engine: RapidOCR by lazy { RapidOCR.create() }

  override suspend fun recognize(imageBytes: ByteArray): ImageOcrResult {
    val sourceImage =
        checkNotNull(ImageIO.read(ByteArrayInputStream(imageBytes))) {
          "Unable to decode image for OCR"
        }
    val width = sourceImage.width
    val height = sourceImage.height
    check(width > 0 && height > 0) { "Invalid image size for OCR: ${width}x$height" }

    val result = engine.run(imageBytes)
    val blocks =
        result.recRes.orEmpty().mapNotNull { record -> record.toRecognizedTextBlock(width, height) }
    return ImageOcrResult(blocks)
  }
}

private fun RecResult.toRecognizedTextBlock(
    width: Int,
    height: Int,
): RecognizedTextBlock? {
  val textValue = text.trim()
  val quad = dtBoxes?.toNormalizedPoints(width, height).orEmpty()
  if (textValue.isBlank() || quad.size < 4) return null
  return RecognizedTextBlock(
      text = textValue,
      points = quad,
      confidence = confidence,
  )
}

private fun Array<Point>.toNormalizedPoints(width: Int, height: Int): List<NormalizedImagePoint> =
    map { point ->
      NormalizedImagePoint(
          x = (point.x / width.toDouble()).toFloat().coerceIn(0f, 1f),
          y = (point.y / height.toDouble()).toFloat().coerceIn(0f, 1f),
      )
    }
