package me.domino.fa2.util.attachmenttext

import com.fleeksoft.ksoup.Ksoup

/** HTML 附件解析。 */
object HtmlAttachmentTextParser : AttachmentTextParser {
  override val format: AttachmentTextFormat = AttachmentTextFormat.HTML

  override fun supports(fileName: String): Boolean = fileName.extensionIn("htm", "html")

  override suspend fun parse(
      fileName: String,
      bytes: ByteArray,
      reporter: AttachmentTextProgressReporter,
  ): AttachmentTextDocument {
    reporter.report("decode_bytes", 0f, "准备解码 HTML", currentItemLabel = fileName)
    val decoded = bytes.decodeToString().removePrefix("\uFEFF")
    reporter.report(
        "decode_bytes",
        1f,
        "HTML 解码完成",
        currentItemLabel = toProgressSnippet(decoded),
    )

    reporter.report("parse_html", 0f, "正在解析 HTML")
    val document = Ksoup.parse(decoded)
    reporter.report("parse_html", 1f, "HTML 解析完成")

    reporter.report("sanitize_html", 0f, "正在清理 HTML")
    document.select("script,style,head,meta,link,title").forEach { node -> node.remove() }
    val body = document.body()
    val sanitizedHtml = body.html().trim().ifBlank { decoded.trim() }
    reporter.report(
        "sanitize_html",
        1f,
        "HTML 清理完成",
        currentItemLabel = toProgressSnippet(sanitizedHtml),
    )

    reporter.report("build_html", 0f, "正在整理 HTML")
    val paragraph =
        AttachmentTextParagraph(
            html = sanitizedHtml.ifBlank { "<p>（空 HTML）</p>" },
        )
    val result = buildAttachmentTextDocument(format = format, paragraphs = listOf(paragraph))
    reporter.report(
        "build_html",
        1f,
        "HTML 整理完成",
        currentItemLabel = toProgressSnippet(result.html),
    )
    return result
  }
}
