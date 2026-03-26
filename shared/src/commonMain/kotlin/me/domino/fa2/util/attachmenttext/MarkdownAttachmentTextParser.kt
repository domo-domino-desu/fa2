package me.domino.fa2.util.attachmenttext

/** Markdown 附件解析。 */
object MarkdownAttachmentTextParser : AttachmentTextParser {
  override val format: AttachmentTextFormat = AttachmentTextFormat.MARKDOWN

  override fun supports(fileName: String): Boolean = fileName.extensionEquals("md")

  override suspend fun parse(
      fileName: String,
      bytes: ByteArray,
      reporter: AttachmentTextProgressReporter,
  ): AttachmentTextDocument {
    reporter.report("decode_bytes", 0f, "准备解码 Markdown", currentItemLabel = fileName)
    val decoded = bytes.decodeToString().removePrefix("\uFEFF")
    reporter.report(
        "decode_bytes",
        1f,
        "Markdown 解码完成",
        currentItemLabel = toProgressSnippet(decoded),
    )

    reporter.report("split_paragraphs", 0f, "正在整理段落")
    val paragraphTexts = splitPlainTextParagraphs(decoded)
    reporter.report(
        "split_paragraphs",
        1f,
        "段落整理完成",
        currentItemLabel = paragraphTexts.firstOrNull()?.let(::toProgressSnippet),
    )

    val paragraphs =
        paragraphTexts.mapIndexedNotNull { index, paragraphText ->
          reporter.report(
              "interpret_inline_markdown",
              (index + 1).toFloat() / paragraphTexts.size.coerceAtLeast(1),
              "正在解析行内 Markdown",
              currentItemLabel = toProgressSnippet(paragraphText),
          )
          buildMarkdownParagraph(paragraphText)
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

private fun buildMarkdownParagraph(text: String): AttachmentTextParagraph? {
  val normalized = normalizeParagraphText(text)
  if (normalized.isBlank()) return null
  val html = parseMarkdownInline(normalized)
  if (html.isBlank()) return null
  return AttachmentTextParagraph(html = "<p>$html</p>")
}

private fun parseMarkdownInline(text: String): String {
  val output = StringBuilder()
  var index = 0
  while (index < text.length) {
    when {
      text.startsWith("![", index) -> {
        val consumed = consumeMarkdownImage(text, index)
        if (consumed > index) {
          index = consumed
        } else {
          output.append(escapeTextAsHtml(text[index].toString(), preserveLineBreaks = false))
          index += 1
        }
      }

      text.startsWith("[", index) -> {
        val parsed = parseMarkdownLink(text, index)
        if (parsed != null) {
          output.append(parsed.first)
          index = parsed.second
        } else {
          output.append(escapeTextAsHtml(text[index].toString(), preserveLineBreaks = false))
          index += 1
        }
      }

      text.startsWith("**", index) || text.startsWith("__", index) -> {
        val delimiter = text.substring(index, index + 2)
        val parsed = parseMarkdownStyledSpan(text, index, delimiter, tagName = "b")
        if (parsed != null) {
          output.append(parsed.first)
          index = parsed.second
        } else {
          output.append(escapeTextAsHtml(delimiter, preserveLineBreaks = false))
          index += 2
        }
      }

      text.startsWith("~~", index) -> {
        val parsed = parseMarkdownStyledSpan(text, index, "~~", tagName = "s")
        if (parsed != null) {
          output.append(parsed.first)
          index = parsed.second
        } else {
          output.append(escapeTextAsHtml("~~", preserveLineBreaks = false))
          index += 2
        }
      }

      text[index] == '*' || text[index] == '_' -> {
        val delimiter = text[index].toString()
        val parsed = parseMarkdownStyledSpan(text, index, delimiter, tagName = "i")
        if (parsed != null) {
          output.append(parsed.first)
          index = parsed.second
        } else {
          output.append(escapeTextAsHtml(delimiter, preserveLineBreaks = false))
          index += 1
        }
      }

      text[index] == '\n' -> {
        output.append("<br/>")
        index += 1
      }

      else -> {
        output.append(escapeTextAsHtml(text[index].toString(), preserveLineBreaks = false))
        index += 1
      }
    }
  }
  return output.toString().trim()
}

private fun parseMarkdownLink(text: String, startIndex: Int): Pair<String, Int>? {
  val labelEnd = text.indexOf(']', startIndex + 1)
  if (labelEnd <= startIndex + 1) return null
  if (labelEnd + 1 >= text.length || text[labelEnd + 1] != '(') return null
  val hrefEnd = text.indexOf(')', labelEnd + 2)
  if (hrefEnd <= labelEnd + 2) return null

  val label = text.substring(startIndex + 1, labelEnd)
  val href = text.substring(labelEnd + 2, hrefEnd).trim()
  if (href.isBlank()) return null

  val renderedLabel = parseMarkdownInline(label).ifBlank { escapeTextAsHtml(label, false) }
  val safeHref = escapeHtmlAttribute(href)
  return """<a href="$safeHref">$renderedLabel</a>""" to (hrefEnd + 1)
}

private fun parseMarkdownStyledSpan(
    text: String,
    startIndex: Int,
    delimiter: String,
    tagName: String,
): Pair<String, Int>? {
  val endIndex = text.indexOf(delimiter, startIndex + delimiter.length)
  if (endIndex <= startIndex + delimiter.length) return null
  val content = text.substring(startIndex + delimiter.length, endIndex)
  if (content.isBlank()) return null
  val renderedContent = parseMarkdownInline(content).ifBlank { escapeTextAsHtml(content, false) }
  return "<$tagName>$renderedContent</$tagName>" to (endIndex + delimiter.length)
}

private fun consumeMarkdownImage(text: String, startIndex: Int): Int {
  val labelEnd = text.indexOf(']', startIndex + 2)
  if (labelEnd < 0) return startIndex
  if (labelEnd + 1 >= text.length || text[labelEnd + 1] != '(') return startIndex
  val hrefEnd = text.indexOf(')', labelEnd + 2)
  if (hrefEnd < 0) return startIndex
  return hrefEnd + 1
}
