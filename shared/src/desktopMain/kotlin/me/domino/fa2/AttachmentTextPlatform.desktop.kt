package me.domino.fa2.util.attachmenttext

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition

/** Desktop 读取压缩包文本条目。 */
internal actual fun readArchiveTextEntry(
    bytes: ByteArray,
    entryPath: String,
    reporter: AttachmentTextProgressReporter,
): String {
  val normalizedEntryPath = entryPath.trim().removePrefix("/")
  reporter.report(
      stageId = "open_archive",
      stageFraction = 0f,
      message = "正在打开压缩包",
      currentItemLabel = normalizedEntryPath,
  )
  val countingInput = CountingInputStream(ByteArrayInputStream(bytes))
  ZipInputStream(countingInput).use { zipInput ->
    while (true) {
      val entry = zipInput.nextEntry ?: break
      val stageFraction = countingInput.fraction(totalBytes = bytes.size.toLong())
      reporter.report(
          stageId = "open_archive",
          stageFraction = stageFraction,
          message = "正在扫描压缩条目",
          currentItemLabel = entry.name,
      )
      if (!entry.isDirectory && entry.name == normalizedEntryPath) {
        val entryBytes = zipInput.readBytes()
        reporter.report(
            stageId = "open_archive",
            stageFraction = 1f,
            message = "已定位压缩条目",
            currentItemLabel = entry.name,
        )
        return entryBytes.decodeToString()
      }
      zipInput.closeEntry()
    }
  }
  throw IllegalStateException("压缩包缺少条目：$normalizedEntryPath")
}

/** Desktop 提取 PDF 行。 */
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
        DesktopPdfLineCollector(
            pageCount = document.numberOfPages.coerceAtLeast(1),
            reporter = reporter,
        )
    stripper.sortByPosition = true
    stripper.getText(document)
    return stripper.lines
  }
}

/** Desktop PDF 行收集器。 */
private class DesktopPdfLineCollector(
    /** 页数。 */
    private val pageCount: Int,
    /** 进度回调。 */
    private val reporter: AttachmentTextProgressReporter,
) : PDFTextStripper() {
  /** 已收集行。 */
  val lines: MutableList<PdfLine> = mutableListOf()

  /** 当前行文本。 */
  private val currentLineText = StringBuilder()

  /** 当前行最左侧。 */
  private var currentLineMinX: Double = Double.POSITIVE_INFINITY

  /** 当前行最右侧。 */
  private var currentLineMaxX: Double = Double.NEGATIVE_INFINITY

  /** 当前页序号。 */
  private var currentPageIndex: Int = 0

  /** 进入页面。 */
  override fun startPage(page: PDPage?) {
    super.startPage(page)
    currentPageIndex = currentPageNo - 1
    reporter.report(
        stageId = "extract_pages",
        stageFraction = currentPageIndex / pageCount.toFloat(),
        message = "正在解析第 ${currentPageIndex + 1}/$pageCount 页",
        currentItemLabel = "第 ${currentPageIndex + 1} 页",
    )
  }

  /** 收集文本片段。 */
  override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
    if (text.isNullOrEmpty() || textPositions.isNullOrEmpty()) return
    currentLineText.append(text)
    textPositions.forEach { position ->
      val start = position.xDirAdj.toDouble()
      val end = start + position.widthDirAdj.toDouble()
      currentLineMinX = minOf(currentLineMinX, start)
      currentLineMaxX = maxOf(currentLineMaxX, end)
    }
  }

  /** 行结束。 */
  override fun writeLineSeparator() {
    flushCurrentLine(isEndOfPage = false)
    super.writeLineSeparator()
  }

  /** 页面结束。 */
  override fun endPage(page: PDPage?) {
    flushCurrentLine(isEndOfPage = true)
    reporter.report(
        stageId = "extract_pages",
        stageFraction = (currentPageIndex + 1) / pageCount.toFloat(),
        message = "已解析第 ${currentPageIndex + 1}/$pageCount 页",
        currentItemLabel = "第 ${currentPageIndex + 1} 页",
    )
    super.endPage(page)
  }

  /** 刷新当前行。 */
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

/** 计数输入流。 */
private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
  /** 已读取字节数。 */
  var bytesRead: Long = 0L
    private set

  /** 当前读取比例。 */
  fun fraction(totalBytes: Long): Float {
    if (totalBytes <= 0L) return 0f
    return (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
  }

  /** 读取单字节。 */
  override fun read(): Int {
    val value = super.read()
    if (value >= 0) bytesRead += 1
    return value
  }

  /** 读取字节数组。 */
  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    val count = super.read(buffer, offset, length)
    if (count > 0) bytesRead += count.toLong()
    return count
  }
}
