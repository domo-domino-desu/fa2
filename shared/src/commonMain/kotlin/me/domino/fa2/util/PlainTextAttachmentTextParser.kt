package me.domino.fa2.util.attachmenttext

/** 纯文本附件解析。 */
object PlainTextAttachmentTextParser : AttachmentTextParser {
  override val format: AttachmentTextFormat = AttachmentTextFormat.TEXT

  override fun supports(fileName: String): Boolean = fileName.extensionEquals("txt")

  override suspend fun parse(
      fileName: String,
      bytes: ByteArray,
      reporter: AttachmentTextProgressReporter,
  ): AttachmentTextDocument {
    reporter.report("decode_bytes", 0f, "准备解码文本", currentItemLabel = fileName)
    val decoded = bytes.decodeToString().removePrefix("\uFEFF")
    reporter.report(
        "decode_bytes",
        1f,
        "文本解码完成",
        currentItemLabel = toProgressSnippet(decoded),
    )

    reporter.report("split_paragraphs", 0f, "正在整理段落")
    val paragraphTexts = splitPlainTextParagraphs(decoded)
    val paragraphs =
        paragraphTexts.mapIndexedNotNull { index, paragraphText ->
          reporter.report(
              "split_paragraphs",
              (index + 1).toFloat() / paragraphTexts.size.coerceAtLeast(1),
              "正在整理段落",
              currentItemLabel = toProgressSnippet(paragraphText),
          )
          buildParagraphFromPlainText(paragraphText)
        }

    reporter.report("build_html", 0f, "正在构建 HTML")
    val result = buildAttachmentTextDocument(format = format, paragraphs = paragraphs)
    reporter.report(
        "build_html",
        1f,
        "HTML 构建完成",
        currentItemLabel = toProgressSnippet(result.html),
    )
    return result
  }
}
