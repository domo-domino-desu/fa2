package me.domino.fa2.util.attachmenttext

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/** Android 读取压缩包文本条目。 */
internal actual fun readArchiveTextEntry(
    bytes: ByteArray,
    entryPath: String,
    reporter: AttachmentTextProgressReporter,
): String = readArchiveTextEntryFromZip(bytes = bytes, entryPath = entryPath, reporter = reporter)

/** Android 提取 PDF 行。 */
internal actual fun extractPdfLines(
    bytes: ByteArray,
    reporter: AttachmentTextProgressReporter,
): List<PdfLine> {
  reporter.report(
      stageId = "open_document",
      stageFraction = 0f,
      message = "正在打开 PDF",
  )
  PDDocument.load(bytes).use { document ->
    reporter.report(
        stageId = "open_document",
        stageFraction = 1f,
        message = "PDF 已打开",
        currentItemLabel = "${document.numberOfPages} 页",
    )
    val stripper =
        AndroidPdfLineCollector(
            pageCount = document.numberOfPages.coerceAtLeast(1),
            reporter = reporter,
        )
    stripper.sortByPosition = true
    stripper.getText(document)
    return stripper.lines
  }
}

/** Android PDF 行收集器。 */
private class AndroidPdfLineCollector(
    /** 页数。 */
    private val pageCount: Int,
    /** 进度回调。 */
    private val reporter: AttachmentTextProgressReporter,
) : PDFTextStripper() {
  val lines: List<PdfLine>
    get() = accumulator.lines

  private val accumulator = PdfLineAccumulator(pageCount = pageCount, reporter = reporter)

  /** 进入页面。 */
  override fun startPage(page: PDPage?) {
    super.startPage(page)
    accumulator.onStartPage(pageIndex = currentPageNo - 1)
  }

  /** 收集文本片段。 */
  override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
    accumulator.onTextChunk(
        text = text,
        chunkBounds =
            textPositions?.map { position ->
              val start = position.xDirAdj.toDouble()
              PdfChunkBounds(startX = start, endX = start + position.widthDirAdj.toDouble())
            },
    )
  }

  /** 行结束。 */
  override fun writeLineSeparator() {
    accumulator.onLineSeparator()
    super.writeLineSeparator()
  }

  /** 页面结束。 */
  override fun endPage(page: PDPage?) {
    accumulator.onEndPage()
    super.endPage(page)
  }
}
