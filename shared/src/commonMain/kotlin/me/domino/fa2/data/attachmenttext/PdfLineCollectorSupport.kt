package me.domino.fa2.data.attachmenttext

import me.domino.fa2.application.attachmenttext.AttachmentTextProgressReporter
import me.domino.fa2.domain.attachmenttext.*

/** PDF 文本块的水平边界（起始 X 与终止 X）。 */
internal data class PdfChunkBounds(
    /** 文本块起始 X 坐标。 */
    val startX: Double,
    /** 文本块终止 X 坐标。 */
    val endX: Double,
)

/** 按页逐行累积 PDF 文本块，并上报解析进度。 */
internal class PdfLineAccumulator(
    /** PDF 总页数，用于计算进度分数。 */
    private val pageCount: Int,
    /** 进度上报器。 */
    private val reporter: AttachmentTextProgressReporter,
) {
  /** 已累积的行列表。 */
  val lines: MutableList<PdfLine> = mutableListOf()

  /** 当前行累积文本缓冲。 */
  private val currentLineText = StringBuilder()
  /** 当前行内所有文本块的最小 X 坐标。 */
  private var currentLineMinX: Double = Double.POSITIVE_INFINITY
  /** 当前行内所有文本块的最大 X 坐标。 */
  private var currentLineMaxX: Double = Double.NEGATIVE_INFINITY
  /** 当前正在处理的页面索引（从 0 起）。 */
  private var currentPageIndex: Int = 0

  /** 开始处理新页面，更新进度。 */
  fun onStartPage(pageIndex: Int) {
    currentPageIndex = pageIndex
    reporter.report(
        stageId = "extract_pages",
        stageFraction = currentPageIndex / pageCount.toFloat(),
        message = "正在解析第 ${currentPageIndex + 1}/$pageCount 页",
        currentItemLabel = "第 ${currentPageIndex + 1} 页",
    )
  }

  /** 追加一个文本块及其边界到当前行缓冲。 */
  fun onTextChunk(text: String?, chunkBounds: Iterable<PdfChunkBounds>?) {
    if (text.isNullOrEmpty() || chunkBounds == null) return
    currentLineText.append(text)
    chunkBounds.forEach { bounds ->
      currentLineMinX = minOf(currentLineMinX, bounds.startX)
      currentLineMaxX = maxOf(currentLineMaxX, bounds.endX)
    }
  }

  /** 触发行分隔符，将当前行缓冲刷入 lines 列表。 */
  fun onLineSeparator() {
    flushCurrentLine(isEndOfPage = false)
  }

  /** 页面结束，刷新当前行并上报该页完成进度。 */
  fun onEndPage() {
    flushCurrentLine(isEndOfPage = true)
    reporter.report(
        stageId = "extract_pages",
        stageFraction = (currentPageIndex + 1) / pageCount.toFloat(),
        message = "已解析第 ${currentPageIndex + 1}/$pageCount 页",
        currentItemLabel = "第 ${currentPageIndex + 1} 页",
    )
  }

  /** 将当前行缓冲内容刷入 lines 列表并重置缓冲状态。 */
  private fun flushCurrentLine(isEndOfPage: Boolean) {
    val text = currentLineText.toString().trim()
    if (text.isNotBlank()) {
      lines +=
          PdfLine(
              text = text,
              width =
                  if (currentLineMinX.isFinite() && currentLineMaxX.isFinite()) {
                    (currentLineMaxX - currentLineMinX).coerceAtLeast(0.0)
                  } else {
                    text.length.toDouble()
                  },
              pageIndex = currentPageIndex,
              isEndOfPage = isEndOfPage,
          )
    } else if (isEndOfPage && lines.lastOrNull()?.pageIndex == currentPageIndex) {
      val lastLine = lines.last()
      if (!lastLine.isEndOfPage) {
        lines[lines.lastIndex] = lastLine.copy(isEndOfPage = true)
      }
    }
    currentLineText.clear()
    currentLineMinX = Double.POSITIVE_INFINITY
    currentLineMaxX = Double.NEGATIVE_INFINITY
  }
}
