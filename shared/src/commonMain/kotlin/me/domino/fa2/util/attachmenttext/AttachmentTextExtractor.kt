package me.domino.fa2.util.attachmenttext

/** 附件文本统一入口。 */
object AttachmentTextExtractor {
  /** 解析器列表。 */
  private val parsers: List<AttachmentTextParser> =
      listOf(
          DocxAttachmentTextParser,
          OdtAttachmentTextParser,
          RtfAttachmentTextParser,
          PdfAttachmentTextParser,
          MarkdownAttachmentTextParser,
          PlainTextAttachmentTextParser,
          HtmlAttachmentTextParser,
      )

  /** 是否支持当前文件。 */
  fun isSupported(fileName: String): Boolean = parsers.any { parser -> parser.supports(fileName) }

  /** 解析附件文本。 */
  suspend fun parse(
      fileName: String,
      bytes: ByteArray,
      onProgress: (AttachmentTextProgress) -> Unit = {},
  ): AttachmentTextDocument {
    val parser =
        parsers.firstOrNull { candidate -> candidate.supports(fileName) }
            ?: throw UnsupportedAttachmentTextFormatException(fileName)
    val reporter =
        AttachmentTextProgressReporter.create(
            format = parser.format,
            onProgress = onProgress,
        )
    reporter.start(
        message = "准备解析附件",
        currentItemLabel = fileName.trim().ifBlank { null },
    )
    val document = parser.parse(fileName = fileName, bytes = bytes, reporter = reporter)
    reporter.complete(
        message = "附件解析完成",
        currentItemLabel = toProgressSnippet(document.paragraphs.firstOrNull()?.html),
    )
    return document
  }
}
