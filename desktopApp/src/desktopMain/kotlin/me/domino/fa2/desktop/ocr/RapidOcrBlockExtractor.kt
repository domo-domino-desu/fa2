package me.domino.fa2.desktop.ocr

import io.github.hzkitty.RapidOCR
import io.github.hzkitty.entity.RecResult
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import me.domino.fa2.domain.ocr.NormalizedImagePoint
import me.domino.fa2.domain.ocr.RecognizedTextBlock
import org.opencv.core.Point

/** 基于 RapidOCR 的桌面端文字块提取器实现。 */
internal class RapidOcrBlockExtractor : DesktopOcrBlockExtractor {
  /** 延迟初始化的 RapidOCR 引擎实例。 */
  private val engine: RapidOCR by lazy { RapidOCR.create() }

  /** 对给定图像字节数组运行 OCR 并返回识别文字块列表。 */
  override suspend fun extract(imageBytes: ByteArray): List<RecognizedTextBlock> {
    val sourceImage =
        checkNotNull(ImageIO.read(ByteArrayInputStream(imageBytes))) {
          "Unable to decode image for OCR"
        }
    val width = sourceImage.width
    val height = sourceImage.height
    check(width > 0 && height > 0) { "Invalid image size for OCR: ${width}x$height" }

    val result = engine.run(imageBytes)
    return rapidOcrResultToBlocks(result.recRes.orEmpty(), width, height)
  }
}

/** 将 RapidOCR 识别结果列表转换为归一化文字块列表。 */
internal fun rapidOcrResultToBlocks(
    records: List<RecResult>,
    width: Int,
    height: Int,
): List<RecognizedTextBlock> {
  return records.mapNotNull { record -> record.toRecognizedTextFragment(width, height) }
}

/** 将单条 RapidOCR 识别记录转换为归一化文字块，内容为空或点数不足时返回 null。 */
private fun RecResult.toRecognizedTextFragment(
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

/** 将 OpenCV Point 数组转换为 [0, 1] 范围内的归一化坐标列表。 */
private fun Array<Point>.toNormalizedPoints(width: Int, height: Int): List<NormalizedImagePoint> =
    map { point ->
      NormalizedImagePoint(
          x = (point.x / width.toDouble()).toFloat().coerceIn(0f, 1f),
          y = (point.y / height.toDouble()).toFloat().coerceIn(0f, 1f),
      )
    }
