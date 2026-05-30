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

/** 基于 ML Kit 的图像文字识别端口实现。 */
class MlKitImageTextRecognitionPort : ImageTextRecognitionPort {
  /** 延迟初始化的 ML Kit 文字识别器。 */
  private val recognizer by lazy {
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  }

  /** 对给定图像字节数组执行 OCR 识别并返回结果。 */
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

/** 将 ML Kit Task 转换为挂起函数，支持取消。 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
  addOnSuccessListener { result -> continuation.resume(result) }
  addOnFailureListener { error -> continuation.resumeWithException(error) }
  addOnCanceledListener { continuation.cancel() }
}

/** 将 ML Kit 文字块转换为归一化的识别文字块，若内容为空则返回 null。 */
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

/** 将角点数组转换为归一化坐标列表，数组为 null 或点数不足时返回 null。 */
private fun Array<Point>?.toNormalizedPoints(
    width: Int,
    height: Int,
): List<NormalizedImagePoint>? {
  val rawPoints = this ?: return null
  if (rawPoints.size < 4) return null
  return rawPoints.map { point -> point.toNormalizedPoint(width, height) }
}

/** 将边界矩形转换为四角归一化坐标列表，矩形为 null 时返回空列表。 */
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

/** 将像素坐标点转换为 [0, 1] 范围内的归一化图像坐标。 */
private fun Point.toNormalizedPoint(width: Int, height: Int): NormalizedImagePoint =
    NormalizedImagePoint(
        x = (x.toFloat() / width.toFloat()).coerceIn(0f, 1f),
        y = (y.toFloat() / height.toFloat()).coerceIn(0f, 1f),
    )
