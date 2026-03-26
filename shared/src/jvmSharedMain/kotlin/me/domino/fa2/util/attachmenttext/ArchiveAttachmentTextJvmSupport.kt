package me.domino.fa2.util.attachmenttext

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

internal fun readArchiveTextEntryFromZip(
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

private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
  private var bytesRead: Long = 0L

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
