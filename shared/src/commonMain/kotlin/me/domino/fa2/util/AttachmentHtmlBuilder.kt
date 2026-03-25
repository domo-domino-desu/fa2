package me.domino.fa2.util.attachmenttext

/** 构建解析结果。 */
internal fun buildAttachmentTextDocument(
    format: AttachmentTextFormat,
    paragraphs: List<AttachmentTextParagraph>,
): AttachmentTextDocument =
    AttachmentTextDocument(
        format = format,
        html = paragraphs.joinToString(separator = "\n") { paragraph -> paragraph.html },
        paragraphs = paragraphs,
    )

/** 从纯文本构建段落。 */
internal fun buildParagraphFromPlainText(
    text: String,
    sourceLabel: String? = null,
): AttachmentTextParagraph? {
  val normalized = normalizeParagraphText(text)
  if (normalized.isBlank()) return null
  return AttachmentTextParagraph(
      html = "<p>${escapeTextAsHtml(normalized, preserveLineBreaks = true)}</p>",
      sourceLabel = sourceLabel?.trim()?.ifBlank { null },
  )
}

/** 从样式片段构建段落。 */
internal fun buildParagraphFromRuns(
    runs: List<StyledTextRun>,
    sourceLabel: String? = null,
): AttachmentTextParagraph? {
  val merged = mergeAdjacentRuns(runs).filter { run -> run.text.isNotEmpty() }
  if (merged.isEmpty()) return null
  val content = buildString {
    merged.forEach { run ->
      val escaped = escapeTextAsHtml(run.text, preserveLineBreaks = true)
      append(applyInlineStyle(escaped, run.style))
    }
  }
  val normalizedContent = content.trim()
  if (normalizedContent.isBlank()) return null
  return AttachmentTextParagraph(
      html = "<p>$normalizedContent</p>",
      sourceLabel = sourceLabel?.trim()?.ifBlank { null },
  )
}

/** 生成进度摘要片段。 */
internal fun toProgressSnippet(text: String?, maxLength: Int = 36): String? {
  val normalized = normalizeParagraphText(text.orEmpty())
  if (normalized.isBlank()) return null
  if (normalized.length <= maxLength) return normalized
  return normalized.take(maxLength).trimEnd() + "..."
}

/** 规范化段落纯文本。 */
internal fun normalizeParagraphText(text: String): String =
    text
        .replace('\u00A0', ' ')
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("[\\t\\u000B\\f ]+"), " ")
        .replace(Regex(" *\\n *"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

/** HTML 转义。 */
internal fun escapeTextAsHtml(text: String, preserveLineBreaks: Boolean): String {
  val escaped =
      buildString(text.length) {
        text.forEach { ch ->
          when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            '\n' -> append(if (preserveLineBreaks) "<br/>" else " ")
            else -> append(ch)
          }
        }
      }
  return escaped
}

/** HTML attribute 转义。 */
internal fun escapeHtmlAttribute(text: String): String =
    escapeTextAsHtml(text, preserveLineBreaks = false)

/** 将纯文本拆成段落。 */
internal fun splitPlainTextParagraphs(text: String): List<String> {
  val normalized = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').trim()
  if (normalized.isBlank()) return emptyList()
  return normalized
      .split(Regex("\\n\\s*\\n+"))
      .map { paragraph -> normalizeParagraphText(paragraph) }
      .filter { paragraph -> paragraph.isNotBlank() }
}

/** 应用内联样式。 */
internal fun applyInlineStyle(html: String, style: AttachmentInlineStyle): String {
  if (html.isEmpty()) return html
  var result = html
  if (style.strike) result = "<s>$result</s>"
  if (style.italic) result = "<i>$result</i>"
  if (style.bold) result = "<b>$result</b>"
  return result
}

/** 合并相邻同样式片段。 */
internal fun mergeAdjacentRuns(runs: List<StyledTextRun>): List<StyledTextRun> {
  if (runs.isEmpty()) return emptyList()
  val merged = mutableListOf<StyledTextRun>()
  runs.forEach { run ->
    if (run.text.isEmpty()) return@forEach
    val previous = merged.lastOrNull()
    if (previous != null && previous.style == run.style) {
      merged[merged.lastIndex] = previous.copy(text = previous.text + run.text)
    } else {
      merged += run
    }
  }
  return merged
}
