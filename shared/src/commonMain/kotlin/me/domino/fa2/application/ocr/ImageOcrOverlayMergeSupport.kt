package me.domino.fa2.application.ocr

import me.domino.fa2.domain.ocr.NormalizedImagePoint
import me.domino.fa2.domain.ocr.RecognizedTextBlock
import me.domino.fa2.util.logging.FaLog

private const val overlayRowCenterThresholdFactor = 0.65f
private val imageOcrOverlayMergeLog = FaLog.withTag("ImageOcrOverlayMerge")

internal data class NormalizedImageRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
  val width: Float
    get() = (right - left).coerceAtLeast(0f)

  val height: Float
    get() = (bottom - top).coerceAtLeast(0f)

  val centerY: Float
    get() = (top + bottom) / 2f

  fun intersects(other: NormalizedImageRect): Boolean =
      left <= other.right && right >= other.left && top <= other.bottom && bottom >= other.top

  fun toPoints(): List<NormalizedImagePoint> =
      listOf(
          NormalizedImagePoint(left, top),
          NormalizedImagePoint(right, top),
          NormalizedImagePoint(right, bottom),
          NormalizedImagePoint(left, bottom),
      )

  companion object {
    fun from(points: List<NormalizedImagePoint>): NormalizedImageRect? {
      if (points.isEmpty()) return null
      val left = points.minOf { it.x.coerceIn(0f, 1f) }
      val top = points.minOf { it.y.coerceIn(0f, 1f) }
      val right = points.maxOf { it.x.coerceIn(0f, 1f) }
      val bottom = points.maxOf { it.y.coerceIn(0f, 1f) }
      if (right <= left || bottom <= top) return null
      return NormalizedImageRect(left = left, top = top, right = right, bottom = bottom)
    }
  }
}

internal fun collectRecognizedTextBlocksIntersectingRegion(
    blocks: List<RecognizedTextBlock>,
    regionPoints: List<NormalizedImagePoint>,
): List<RecognizedTextBlock> {
  val region = NormalizedImageRect.from(regionPoints)
  if (region == null) {
    imageOcrOverlayMergeLog.w { "按区域收集 OCR 框失败 -> 选区为空或非法" }
    return emptyList()
  }
  val selectedBlocks =
      blocks.filter { block ->
        val bounds = block.overlayBoundsRect() ?: return@filter false
        bounds.intersects(region)
      }
  imageOcrOverlayMergeLog.i {
    "按区域收集 OCR 框 -> 选区=${region.toLogString()}, 输入=${blocks.size}, 命中=${selectedBlocks.size}"
  }
  return selectedBlocks
}

internal fun mergeRecognizedTextBlocksForOverlay(
    blocks: List<RecognizedTextBlock>
): RecognizedTextBlock? {
  if (blocks.isEmpty()) {
    imageOcrOverlayMergeLog.w { "合并 OCR 框失败 -> 输入为空" }
    return null
  }
  if (blocks.size == 1) {
    imageOcrOverlayMergeLog.i { "合并 OCR 框跳过 -> 仅有一个框" }
    return blocks.single()
  }
  val measuredBlocks = blocks.mapNotNull { block -> block.toOverlayMeasuredOcrBlock() }
  if (measuredBlocks.isEmpty()) {
    imageOcrOverlayMergeLog.w { "合并 OCR 框失败 -> 无可用边界" }
    return null
  }
  val mergedText =
      measuredBlocks
          .sortedByReadingOrder()
          .map { it.block.text.trim() }
          .filter { it.isNotBlank() }
          .joinToString(separator = " ")
  if (mergedText.isBlank()) {
    imageOcrOverlayMergeLog.w { "合并 OCR 框失败 -> 合并文本为空" }
    return null
  }
  val unionRect =
      NormalizedImageRect(
          left = measuredBlocks.minOf { it.rect.left },
          top = measuredBlocks.minOf { it.rect.top },
          right = measuredBlocks.maxOf { it.rect.right },
          bottom = measuredBlocks.maxOf { it.rect.bottom },
      )
  return RecognizedTextBlock(
          text = mergedText,
          points = unionRect.toPoints(),
          confidence =
              measuredBlocks
                  .mapNotNull { measuredBlock -> measuredBlock.block.confidence }
                  .averageOrNull(),
      )
      .also { mergedBlock ->
        imageOcrOverlayMergeLog.i {
          "合并 OCR 框成功 -> 输入=${blocks.size}, 边界=${unionRect.toLogString()}, 文本=${mergedBlock.text.take(80)}"
        }
      }
}

internal fun RecognizedTextBlock.overlayBoundsRect(): NormalizedImageRect? =
    points.takeIf { it.isNotEmpty() }?.let { NormalizedImageRect.from(it) }

private data class OverlayMeasuredOcrBlock(
    val block: RecognizedTextBlock,
    val rect: NormalizedImageRect,
)

private fun RecognizedTextBlock.toOverlayMeasuredOcrBlock(): OverlayMeasuredOcrBlock? =
    overlayBoundsRect()?.let { rect -> OverlayMeasuredOcrBlock(block = this, rect = rect) }

private fun List<OverlayMeasuredOcrBlock>.sortedByReadingOrder(): List<OverlayMeasuredOcrBlock> {
  if (isEmpty()) return emptyList()
  val heightsMedian = map { it.rect.height }.median()
  val rowThreshold = (heightsMedian * overlayRowCenterThresholdFactor).coerceAtLeast(0.0001f)
  val rows = mutableListOf<MutableList<OverlayMeasuredOcrBlock>>()

  for (block in sortedBy { it.rect.centerY }) {
    val existingRow =
        rows.firstOrNull { row ->
          kotlin.math.abs(row.averageCenterY() - block.rect.centerY) < rowThreshold
        }
    if (existingRow == null) {
      rows += mutableListOf(block)
    } else {
      existingRow += block
    }
  }

  return rows
      .sortedBy { row -> row.minOf { it.rect.top } }
      .flatMap { row -> row.sortedBy { it.rect.left } }
}

private fun List<OverlayMeasuredOcrBlock>.averageCenterY(): Float =
    map { it.rect.centerY }.average().toFloat()

private fun List<Float>.median(): Float {
  if (isEmpty()) return 0f
  val sorted = sorted()
  val middleIndex = sorted.size / 2
  return if (sorted.size % 2 == 0) {
    (sorted[middleIndex - 1] + sorted[middleIndex]) / 2f
  } else {
    sorted[middleIndex]
  }
}

private fun List<Float>.averageOrNull(): Float? = if (isEmpty()) null else average().toFloat()

private fun NormalizedImageRect.toLogString(): String =
    "[${left.formatForLog()}, ${top.formatForLog()}]-[${right.formatForLog()}, ${bottom.formatForLog()}]"

private fun Float.formatForLog(): String = ((this * 1000).toInt() / 1000f).toString()
