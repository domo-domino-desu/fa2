package me.domino.fa2.util.attachmenttext

internal data class PdfChunkBounds(val startX: Double, val endX: Double)

internal class PdfLineAccumulator(
    private val pageCount: Int,
    private val reporter: AttachmentTextProgressReporter,
) {
  val lines: MutableList<PdfLine> = mutableListOf()

  private val currentLineText = StringBuilder()
  private var currentLineMinX: Double = Double.POSITIVE_INFINITY
  private var currentLineMaxX: Double = Double.NEGATIVE_INFINITY
  private var currentPageIndex: Int = 0

  fun onStartPage(pageIndex: Int) {
    currentPageIndex = pageIndex
    reporter.report(
        stageId = "extract_pages",
        stageFraction = currentPageIndex / pageCount.toFloat(),
        message = "正在解析第 ${currentPageIndex + 1}/$pageCount 页",
        currentItemLabel = "第 ${currentPageIndex + 1} 页",
    )
  }

  fun onTextChunk(text: String?, chunkBounds: Iterable<PdfChunkBounds>?) {
    if (text.isNullOrEmpty() || chunkBounds == null) return
    currentLineText.append(text)
    chunkBounds.forEach { bounds ->
      currentLineMinX = minOf(currentLineMinX, bounds.startX)
      currentLineMaxX = maxOf(currentLineMaxX, bounds.endX)
    }
  }

  fun onLineSeparator() {
    flushCurrentLine(isEndOfPage = false)
  }

  fun onEndPage() {
    flushCurrentLine(isEndOfPage = true)
    reporter.report(
        stageId = "extract_pages",
        stageFraction = (currentPageIndex + 1) / pageCount.toFloat(),
        message = "已解析第 ${currentPageIndex + 1}/$pageCount 页",
        currentItemLabel = "第 ${currentPageIndex + 1} 页",
    )
  }

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
