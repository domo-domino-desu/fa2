package me.domino.fa2.data.attachmenttext

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import me.domino.fa2.application.attachmenttext.AttachmentTextProgressReporter
import me.domino.fa2.domain.attachmenttext.*

/** Android 读取压缩包文本条目。 */
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

/** ZIP 流字节计数器。 */
private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
  private var bytesRead: Long = 0L

  /** 返回已读字节比例。 */
  fun fraction(totalBytes: Long): Float {
    if (totalBytes <= 0L) return 0f
    return (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
  }

  override fun read(): Int {
    val value = super.read()
    if (value >= 0) bytesRead += 1
    return value
  }

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    val count = super.read(buffer, offset, length)
    if (count > 0) bytesRead += count.toLong()
    return count
  }
}
