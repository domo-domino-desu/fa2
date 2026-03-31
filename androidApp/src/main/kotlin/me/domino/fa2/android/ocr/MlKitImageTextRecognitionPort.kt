package me.domino.fa2.android.ocr

import android.graphics.BitmapFactory
import android.graphics.Point
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import me.domino.fa2.domain.ocr.ImageOcrResult
import me.domino.fa2.domain.ocr.ImageTextRecognitionPort
import me.domino.fa2.domain.ocr.NormalizedImagePoint
import me.domino.fa2.domain.ocr.RecognizedTextBlock

class MlKitImageTextRecognitionPort : ImageTextRecognitionPort {
  private val recognizer by lazy {
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  }

  override suspend fun recognize(imageBytes: ByteArray): ImageOcrResult {
    val bitmap =
        checkNotNull(BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)) {
          "Unable to decode image for OCR"
        }
    val width = bitmap.width
    val height = bitmap.height
    check(width > 0 && height > 0) { "Invalid image size for OCR: ${width}x$height" }

    val image = InputImage.fromBitmap(bitmap, 0)
    val result = recognizer.process(image).await()
    val blocks =
        result.textBlocks.mapNotNull { block -> block.toRecognizedTextBlock(width, height) }
    return ImageOcrResult(blocks = blocks)
  }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
  addOnSuccessListener { result -> continuation.resume(result) }
  addOnFailureListener { error -> continuation.resumeWithException(error) }
  addOnCanceledListener { continuation.cancel() }
}

private fun Text.TextBlock.toRecognizedTextBlock(
    width: Int,
    height: Int,
): RecognizedTextBlock? {
  val textValue =
      lines
          .map { line -> line.text.trim() }
          .filter { it.isNotBlank() }
          .joinToString(separator = " ")
          .ifBlank { text.trim() }
  if (textValue.isBlank()) return null
  val points =
      cornerPoints.toNormalizedPoints(width, height)
          ?: boundingBox.toNormalizedPoints(width, height)
  if (points.size < 4) return null
  return RecognizedTextBlock(text = textValue, points = points, confidence = null)
}

private fun Array<Point>?.toNormalizedPoints(
    width: Int,
    height: Int,
): List<NormalizedImagePoint>? {
  val rawPoints = this ?: return null
  if (rawPoints.size < 4) return null
  return rawPoints.map { point -> point.toNormalizedPoint(width, height) }
}

private fun Rect?.toNormalizedPoints(width: Int, height: Int): List<NormalizedImagePoint> {
  val box = this ?: return emptyList()
  return listOf(
          Point(box.left, box.top),
          Point(box.right, box.top),
          Point(box.right, box.bottom),
          Point(box.left, box.bottom),
      )
      .map { point -> point.toNormalizedPoint(width, height) }
}

private fun Point.toNormalizedPoint(width: Int, height: Int): NormalizedImagePoint =
    NormalizedImagePoint(
        x = (x.toFloat() / width.toFloat()).coerceIn(0f, 1f),
        y = (y.toFloat() / height.toFloat()).coerceIn(0f, 1f),
    )
