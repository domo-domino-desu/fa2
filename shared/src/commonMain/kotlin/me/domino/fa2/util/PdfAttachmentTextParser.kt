package me.domino.fa2.util.attachmenttext

/** PDF 附件文本解析器。 */
internal object PdfAttachmentTextParser : AttachmentTextParser {
  /** 解析器格式。 */
  override val format: AttachmentTextFormat = AttachmentTextFormat.PDF

  /** 是否支持 PDF。 */
  override fun supports(fileName: String): Boolean = fileName.extensionEquals("pdf")

  /** 解析 PDF。 */
  override suspend fun parse(
      fileName: String,
      bytes: ByteArray,
      reporter: AttachmentTextProgressReporter,
  ): AttachmentTextDocument {
    val lines = extractPdfLines(bytes = bytes, reporter = reporter)
    reporter.report(
        stageId = "merge_paragraphs",
        stageFraction = 0f,
        message = "正在合并 PDF 段落",
        currentItemLabel = "${lines.size} 行",
    )
    val paragraphTexts = mergeLinesIntoParagraphs(lines)
    paragraphTexts.forEachIndexed { index, paragraph ->
      reporter.report(
          stageId = "merge_paragraphs",
          stageFraction = (index + 1) / paragraphTexts.size.coerceAtLeast(1).toFloat(),
          message = "正在合并第 ${index + 1}/${paragraphTexts.size.coerceAtLeast(1)} 段",
          currentItemLabel = toProgressSnippet(paragraph),
      )
    }
    reporter.report(
        stageId = "build_html",
        stageFraction = 0f,
        message = "正在构建 HTML",
    )
    val paragraphs =
        paragraphTexts.mapIndexedNotNull { index, paragraphText ->
          buildParagraphFromPlainText(
              text = paragraphText,
              sourceLabel = "段落 ${index + 1}",
          )
        }
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

/** 平台 PDF 提取。 */
internal expect fun extractPdfLines(
    bytes: ByteArray,
    reporter: AttachmentTextProgressReporter,
): List<PdfLine>
