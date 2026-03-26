package me.domino.fa2.data.attachmenttext

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import me.domino.fa2.application.attachmenttext.AttachmentTextProgressReporter
import me.domino.fa2.domain.attachmenttext.*

/** DOCX 附件文本解析器。 */
internal object DocxAttachmentTextParser : AttachmentTextParser {
  /** 解析器格式。 */
  override val format: AttachmentTextFormat = AttachmentTextFormat.DOCX

  /** 是否支持 DOCX。 */
  override fun supports(fileName: String): Boolean = fileName.extensionEquals("docx")

  /** 解析 DOCX。 */
  override suspend fun parse(
      fileName: String,
      bytes: ByteArray,
      reporter: AttachmentTextProgressReporter,
  ): AttachmentTextDocument {
    reporter.report(
        stageId = "detect_format",
        stageFraction = 1f,
        message = "已识别为 DOCX",
        currentItemLabel = fileName.trim(),
    )
    reporter.report(
        stageId = "load_document_xml",
        stageFraction = 0f,
        message = "正在读取 word/document.xml",
        currentItemLabel = "word/document.xml",
    )
    val xml =
        readArchiveTextEntry(bytes = bytes, entryPath = "word/document.xml", reporter = reporter)
    val document = Ksoup.parseXml(xml, "")
    reporter.report(
        stageId = "load_document_xml",
        stageFraction = 1f,
        message = "已读取 word/document.xml",
        currentItemLabel = "word/document.xml",
    )

    val body =
        findFirstDescendant(document, "w:body") ?: throw IllegalStateException("DOCX 文档缺少 w:body")
    val paragraphElements = mutableListOf<Element>()
    collectDescendants(body, "w:p", paragraphElements)
    val paragraphs = mutableListOf<AttachmentTextParagraph>()
    val total = paragraphElements.size.coerceAtLeast(1)
    paragraphElements.forEachIndexed { index, paragraphElement ->
      val runs = mutableListOf<StyledTextRun>()
      paragraphElement.childNodes().forEach { child ->
        collectDocxRuns(child, AttachmentInlineStyle(), runs)
      }
      buildParagraphFromRuns(
              runs = runs,
              sourceLabel = "段落 ${index + 1}",
          )
          ?.let(paragraphs::add)
      reporter.report(
          stageId = "walk_blocks",
          stageFraction = (index + 1) / total.toFloat(),
          message = "正在提取第 ${index + 1}/$total 段",
          currentItemLabel =
              toProgressSnippet(runs.joinToString(separator = "") { run -> run.text }),
      )
    }

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

/** 收集 DOCX 文本片段。 */
private fun collectDocxRuns(
    node: Node,
    inheritedStyle: AttachmentInlineStyle,
    output: MutableList<StyledTextRun>,
) {
  when (node) {
    is Element -> {
      when (node.tagName()) {
        "w:r" -> {
          val style = resolveDocxRunStyle(node = node, inheritedStyle = inheritedStyle)
          node.childNodes().forEach { child ->
            if (child is Element && child.tagName() == "w:rPr") return@forEach
            collectDocxRuns(child, style, output)
          }
        }

        "w:t" -> appendStyledRun(output, node.wholeText(), inheritedStyle)
        "w:tab" -> appendStyledRun(output, "    ", inheritedStyle)
        "w:br",
        "w:cr" -> appendStyledRun(output, "\n", inheritedStyle)
        "w:noBreakHyphen",
        "w:softHyphen" -> appendStyledRun(output, "-", inheritedStyle)
        "w:delText" -> appendStyledRun(output, node.wholeText(), inheritedStyle.copy(strike = true))
        else ->
            node.childNodes().forEach { child -> collectDocxRuns(child, inheritedStyle, output) }
      }
    }
  }
}

/** 解析 DOCX 片段样式。 */
private fun resolveDocxRunStyle(
    node: Element,
    inheritedStyle: AttachmentInlineStyle,
): AttachmentInlineStyle {
  val properties =
      node.childNodes().firstOrNull { child -> child is Element && child.tagName() == "w:rPr" }
          as? Element ?: return inheritedStyle
  return inheritedStyle.copy(
      bold = inheritedStyle.bold || properties.hasEnabledDocxStyle("w:b"),
      italic = inheritedStyle.italic || properties.hasEnabledDocxStyle("w:i"),
      strike =
          inheritedStyle.strike ||
              properties.hasEnabledDocxStyle("w:strike") ||
              properties.hasEnabledDocxStyle("w:dstrike"),
  )
}

/** 当前属性节点是否启用了某个样式。 */
private fun Element.hasEnabledDocxStyle(tagName: String): Boolean {
  val node =
      childNodes().firstOrNull { child -> child is Element && child.tagName() == tagName }
          as? Element ?: return false
  val raw = node.attr("w:val").trim().lowercase()
  return raw.isBlank() || (raw != "0" && raw != "false" && raw != "off")
}

/** 追加片段。 */
internal fun appendStyledRun(
    output: MutableList<StyledTextRun>,
    text: String,
    style: AttachmentInlineStyle,
) {
  if (text.isEmpty()) return
  output += StyledTextRun(text = text, style = style)
}

/** 查找首个后代节点。 */
internal fun findFirstDescendant(node: Node, tagName: String): Element? {
  if (node is Element && node.tagName() == tagName) return node
  node.childNodes().forEach { child ->
    val matched = findFirstDescendant(child, tagName)
    if (matched != null) return matched
  }
  return null
}

/** 收集指定标签的后代节点。 */
internal fun collectDescendants(node: Node, tagName: String, output: MutableList<Element>) {
  if (node is Element && node.tagName() == tagName) {
    output.add(node)
  }
  node.childNodes().forEach { child -> collectDescendants(child, tagName, output) }
}
