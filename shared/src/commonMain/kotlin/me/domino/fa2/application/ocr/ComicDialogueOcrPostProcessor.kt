package me.domino.fa2.application.ocr

import kotlin.math.hypot
import me.domino.fa2.domain.ocr.ImageOcrResult
import me.domino.fa2.domain.ocr.NormalizedImagePoint
import me.domino.fa2.domain.ocr.RecognizedTextBlock

private const val horizontalExpansionFactor = 0.9f
private const val verticalExpansionFactor = 1.4f
private const val maxCenterDistanceFactor = 3.2f
private const val minHeightRatio = 0.45f
private const val maxHeightRatio = 2.2f
private const val minWidthRatio = 0.2f
private const val maxWidthRatio = 5.0f
private const val maxVerticalGapWithoutHorizontalOverlapFactor = 1.8f
private const val maxHorizontalGapWithoutVerticalOverlapFactor = 1.6f
private const val maxBoundingAreaFactor = 4.5f
private const val rowCenterThresholdFactor = 0.65f

object ComicDialogueOcrBlockMerger {
  fun merge(result: ImageOcrResult): ImageOcrResult = result.copy(blocks = merge(result.blocks))

  fun merge(blocks: List<RecognizedTextBlock>): List<RecognizedTextBlock> {
    if (blocks.size <= 1) return blocks

    val measuredBlocks = blocks.mapNotNull { block -> MeasuredOcrBlock.from(block) }
    if (measuredBlocks.size <= 1) return measuredBlocks.map { it.block }

    val medianHeight = measuredBlocks.map { it.rect.height }.median()
    if (medianHeight <= 0f) return measuredBlocks.map { it.block }

    val adjacency = Array(measuredBlocks.size) { mutableListOf<Int>() }
    for (leftIndex in 0 until measuredBlocks.lastIndex) {
      for (rightIndex in leftIndex + 1 until measuredBlocks.size) {
        if (shouldConnect(measuredBlocks[leftIndex], measuredBlocks[rightIndex], medianHeight)) {
          adjacency[leftIndex] += rightIndex
          adjacency[rightIndex] += leftIndex
        }
      }
    }

    val visited = BooleanArray(measuredBlocks.size)
    val mergedBlocks = mutableListOf<RecognizedTextBlock>()
    for (index in measuredBlocks.indices) {
      if (visited[index]) continue
      val component =
          collectConnectedComponent(index, adjacency, visited).map { measuredBlocks[it] }
      mergedBlocks += mergeConnectedComponent(component)
    }

    return mergedBlocks.sortedWith(compareBy({ it.boundsRect().top }, { it.boundsRect().left }))
  }
}

fun ImageOcrResult.mergeComicDialogueBlocks(): ImageOcrResult =
    ComicDialogueOcrBlockMerger.merge(this)

fun mergeComicDialogueBlocks(blocks: List<RecognizedTextBlock>): List<RecognizedTextBlock> =
    ComicDialogueOcrBlockMerger.merge(blocks)

private fun collectConnectedComponent(
    startIndex: Int,
    adjacency: Array<MutableList<Int>>,
    visited: BooleanArray,
): List<Int> {
  val stack = ArrayDeque<Int>()
  val component = mutableListOf<Int>()
  stack.addLast(startIndex)
  visited[startIndex] = true
  while (stack.isNotEmpty()) {
    val index = stack.removeLast()
    component += index
    for (nextIndex in adjacency[index]) {
      if (visited[nextIndex]) continue
      visited[nextIndex] = true
      stack.addLast(nextIndex)
    }
  }
  return component
}

private fun shouldConnect(
    left: MeasuredOcrBlock,
    right: MeasuredOcrBlock,
    scaleHeight: Float,
): Boolean {
  if (
      !left.rect
          .expand(horizontalExpansionFactor * scaleHeight, verticalExpansionFactor * scaleHeight)
          .intersects(
              right.rect.expand(
                  horizontalExpansionFactor * scaleHeight,
                  verticalExpansionFactor * scaleHeight,
              )
          )
  ) {
    return false
  }
  if (left.rect.centerDistanceTo(right.rect) > maxCenterDistanceFactor * scaleHeight) {
    return false
  }
  if (!left.rect.height.ratioWithin(right.rect.height, minHeightRatio, maxHeightRatio)) {
    return false
  }
  if (!left.rect.width.ratioWithin(right.rect.width, minWidthRatio, maxWidthRatio)) {
    return false
  }

  val hasHorizontalOverlap = left.rect.horizontalOverlap(right.rect) > 0f
  val hasVerticalOverlap = left.rect.verticalOverlap(right.rect) > 0f
  if (
      !hasHorizontalOverlap &&
          left.rect.verticalGap(right.rect) >
              maxVerticalGapWithoutHorizontalOverlapFactor * scaleHeight
  ) {
    return false
  }
  if (
      !hasVerticalOverlap &&
          left.rect.horizontalGap(right.rect) >
              maxHorizontalGapWithoutVerticalOverlapFactor * scaleHeight
  ) {
    return false
  }
  return true
}

private fun mergeConnectedComponent(component: List<MeasuredOcrBlock>): List<RecognizedTextBlock> {
  if (component.isEmpty()) return emptyList()
  if (component.size == 1) return listOf(component.single().block)

  val componentUnionRect = component.unionRect()
  val originalAreaSum = component.sumOf { it.rect.area.toDouble() }.toFloat()
  if (originalAreaSum <= 0f) {
    return component.sortedByReadingOrder().map { it.block }
  }
  if (componentUnionRect.area > originalAreaSum * maxBoundingAreaFactor) {
    return component.sortedByReadingOrder().map { it.block }
  }

  val mergedText = component.toMergedDialogueText()
  if (mergedText.isBlank()) {
    return component.sortedByReadingOrder().map { it.block }
  }

  return listOf(
      RecognizedTextBlock(
          text = mergedText,
          points = componentUnionRect.toRectPoints(),
          confidence = component.mapNotNull { it.block.confidence }.averageOrNull(),
      )
  )
}

private fun List<MeasuredOcrBlock>.toMergedDialogueText(): String {
  val heightsMedian = map { it.rect.height }.median()
  val rowThreshold = (heightsMedian * rowCenterThresholdFactor).coerceAtLeast(0.0001f)
  val rows = mutableListOf<MutableList<MeasuredOcrBlock>>()

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
      .map { it.block.text.trim() }
      .filter { it.isNotBlank() }
      .joinToString(separator = " ")
}

private fun List<MeasuredOcrBlock>.sortedByReadingOrder(): List<MeasuredOcrBlock> {
  if (isEmpty()) return emptyList()
  val heightsMedian = map { it.rect.height }.median()
  val rowThreshold = (heightsMedian * rowCenterThresholdFactor).coerceAtLeast(0.0001f)
  val rows = mutableListOf<MutableList<MeasuredOcrBlock>>()

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

private fun List<MeasuredOcrBlock>.averageCenterY(): Float =
    map { it.rect.centerY }.average().toFloat()

private fun List<MeasuredOcrBlock>.unionRect(): NormalizedRect =
    NormalizedRect(
        left = minOf { it.rect.left },
        top = minOf { it.rect.top },
        right = maxOf { it.rect.right },
        bottom = maxOf { it.rect.bottom },
    )

private fun RecognizedTextBlock.boundsRect(): NormalizedRect =
    MeasuredOcrBlock.from(this)?.rect ?: NormalizedRect.Zero

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

private fun Float.ratioWithin(other: Float, min: Float, max: Float): Boolean {
  if (this <= 0f || other <= 0f) return false
  val ratio = this / other
  return ratio in min..max
}

private data class MeasuredOcrBlock(
    val block: RecognizedTextBlock,
    val rect: NormalizedRect,
) {
  companion object {
    fun from(block: RecognizedTextBlock): MeasuredOcrBlock? {
      if (block.points.isEmpty()) return null
      val left = block.points.minOf { it.x.coerceIn(0f, 1f) }
      val top = block.points.minOf { it.y.coerceIn(0f, 1f) }
      val right = block.points.maxOf { it.x.coerceIn(0f, 1f) }
      val bottom = block.points.maxOf { it.y.coerceIn(0f, 1f) }
      if (right <= left || bottom <= top) return null
      return MeasuredOcrBlock(
          block = block,
          rect = NormalizedRect(left = left, top = top, right = right, bottom = bottom),
      )
    }
  }
}

private data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
  val width: Float
    get() = (right - left).coerceAtLeast(0f)

  val height: Float
    get() = (bottom - top).coerceAtLeast(0f)

  val centerX: Float
    get() = (left + right) / 2f

  val centerY: Float
    get() = (top + bottom) / 2f

  val area: Float
    get() = width * height

  fun expand(horizontalInset: Float, verticalInset: Float): NormalizedRect =
      NormalizedRect(
          left = left - horizontalInset,
          top = top - verticalInset,
          right = right + horizontalInset,
          bottom = bottom + verticalInset,
      )

  fun intersects(other: NormalizedRect): Boolean =
      left <= other.right && right >= other.left && top <= other.bottom && bottom >= other.top

  fun centerDistanceTo(other: NormalizedRect): Float =
      hypot(centerX - other.centerX, centerY - other.centerY)

  fun horizontalOverlap(other: NormalizedRect): Float =
      (minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0f)

  fun verticalOverlap(other: NormalizedRect): Float =
      (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0f)

  fun horizontalGap(other: NormalizedRect): Float =
      (maxOf(left, other.left) - minOf(right, other.right)).coerceAtLeast(0f)

  fun verticalGap(other: NormalizedRect): Float =
      (maxOf(top, other.top) - minOf(bottom, other.bottom)).coerceAtLeast(0f)

  fun toRectPoints(): List<NormalizedImagePoint> =
      listOf(
          NormalizedImagePoint(left, top),
          NormalizedImagePoint(right, top),
          NormalizedImagePoint(right, bottom),
          NormalizedImagePoint(left, bottom),
      )

  companion object {
    val Zero = NormalizedRect(0f, 0f, 0f, 0f)
  }
}
