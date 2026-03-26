package me.domino.fa2.data.attachmenttext

import me.domino.fa2.application.attachmenttext.AttachmentTextProgressReporter
import me.domino.fa2.domain.attachmenttext.*

/** RTF 附件文本解析器。 */
internal object RtfAttachmentTextParser : AttachmentTextParser {
  /** 解析器格式。 */
  override val format: AttachmentTextFormat = AttachmentTextFormat.RTF

  /** 是否支持 RTF。 */
  override fun supports(fileName: String): Boolean = fileName.extensionEquals("rtf")

  /** 解析 RTF。 */
  override suspend fun parse(
      fileName: String,
      bytes: ByteArray,
      reporter: AttachmentTextProgressReporter,
  ): AttachmentTextDocument {
    reporter.report(
        stageId = "decode_bytes",
        stageFraction = 0f,
        message = "正在解码 RTF 字节",
        currentItemLabel = fileName.trim(),
    )
    val raw = bytes.decodeIso88591()
    reporter.report(
        stageId = "decode_bytes",
        stageFraction = 1f,
        message = "RTF 字节解码完成",
        currentItemLabel = "${raw.length} 字符",
    )

    val tokens = tokenizeRtf(raw = raw, reporter = reporter)
    val parsedParagraphs = interpretRtfTokens(tokens = tokens, reporter = reporter)
    reporter.report(
        stageId = "normalize_paragraphs",
        stageFraction = 0f,
        message = "正在整理段落",
    )
    val paragraphs =
        parsedParagraphs.mapIndexedNotNull { index, runs ->
          buildParagraphFromRuns(runs = runs, sourceLabel = "段落 ${index + 1}")
        }
    reporter.report(
        stageId = "normalize_paragraphs",
        stageFraction = 1f,
        message = "段落整理完成",
        currentItemLabel = "${paragraphs.size} 段",
    )
    reporter.report(
        stageId = "build_html",
        stageFraction = 0f,
        message = "正在构建 HTML",
    )
    val result = buildAttachmentTextDocument(format = format, paragraphs = paragraphs)
    reporter.report(
        stageId = "build_html",
        stageFraction = 1f,
        message = "HTML 构建完成",
        currentItemLabel = "${result.paragraphs.size} 段",
    )
    return result
  }
}
