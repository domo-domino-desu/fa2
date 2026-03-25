package me.domino.fa2.util.attachmenttext

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

/** ODT 附件文本解析器。 */
internal object OdtAttachmentTextParser : AttachmentTextParser {
  /** 解析器格式。 */
  override val format: AttachmentTextFormat = AttachmentTextFormat.ODT

  /** 是否支持 ODT。 */
  override fun supports(fileName: String): Boolean = fileName.extensionEquals("odt")

  /** 解析 ODT。 */
  override suspend fun parse(
      fileName: String,
      bytes: ByteArray,
      reporter: AttachmentTextProgressReporter,
  ): AttachmentTextDocument {
    reporter.report(
        stageId = "detect_format",
        stageFraction = 1f,
        message = "已识别为 ODT",
        currentItemLabel = fileName.trim(),
    )
    reporter.report(
        stageId = "load_document_xml",
        stageFraction = 0f,
        message = "正在读取 content.xml",
        currentItemLabel = "content.xml",
    )
    val xml = readArchiveTextEntry(bytes = bytes, entryPath = "content.xml", reporter = reporter)
    val document = Ksoup.parseXml(xml, "")
    reporter.report(
        stageId = "load_document_xml",
        stageFraction = 1f,
        message = "已读取 content.xml",
        currentItemLabel = "content.xml",
    )

    val styleMap = extractOdtInlineStyleMap(document)
    val body =
        findFirstDescendant(document, "office:text")
            ?: throw IllegalStateException("ODT 文档缺少 office:text")
    val paragraphElements = mutableListOf<Element>()
    collectMatchingDescendants(
        node = body,
        tagNames = setOf("text:p", "text:h"),
        output = paragraphElements,
    )

    val paragraphs = mutableListOf<AttachmentTextParagraph>()
    val total = paragraphElements.size.coerceAtLeast(1)
    paragraphElements.forEachIndexed { index, paragraphElement ->
      val runs = mutableListOf<StyledTextRun>()
      paragraphElement.childNodes().forEach { child ->
        collectOdtRuns(
            node = child,
            inheritedStyle = AttachmentInlineStyle(),
            styleMap = styleMap,
            output = runs,
        )
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

/** 提取 ODT 文本样式映射。 */
private fun extractOdtInlineStyleMap(document: Document): Map<String, AttachmentInlineStyle> {
  val styleNodes = mutableListOf<Element>()
  collectMatchingDescendants(
      node = document,
      tagNames = setOf("style:style"),
      output = styleNodes,
  )
  return buildMap {
    styleNodes.forEach { styleNode ->
      val styleName = styleNode.attr("style:name").trim().ifBlank { styleNode.attr("name").trim() }
      if (styleName.isBlank()) return@forEach
      val textProperties =
          styleNode.childNodes().firstOrNull { child ->
            child is Element && child.tagName() == "style:text-properties"
          } as? Element ?: return@forEach
      put(
          styleName,
          AttachmentInlineStyle(
              bold = textProperties.attrEqualsIgnoreCase("fo:font-weight", "bold"),
              italic = textProperties.attrEqualsIgnoreCase("fo:font-style", "italic"),
              strike =
                  textProperties.attr("style:text-line-through-style").isNotBlank() &&
                      !textProperties.attrEqualsIgnoreCase("style:text-line-through-style", "none"),
          ),
      )
    }
  }
}

/** 收集 ODT 文本片段。 */
private fun collectOdtRuns(
    node: Node,
    inheritedStyle: AttachmentInlineStyle,
    styleMap: Map<String, AttachmentInlineStyle>,
    output: MutableList<StyledTextRun>,
) {
  when (node) {
    is TextNode -> appendStyledRun(output, node.text(), inheritedStyle)
    is Element -> {
      when (node.tagName()) {
        "text:span" -> {
          val styleName =
              node.attr("text:style-name").trim().ifBlank { node.attr("style-name").trim() }
          val resolvedStyle = inheritedStyle.mergeWith(styleMap[styleName])
          node.childNodes().forEach { child ->
            collectOdtRuns(
                node = child,
                inheritedStyle = resolvedStyle,
                styleMap = styleMap,
                output = output,
            )
          }
        }

        "text:s" -> {
          val count = node.attr("text:c").trim().toIntOrNull()?.coerceAtLeast(1) ?: 1
          appendStyledRun(output, " ".repeat(count), inheritedStyle)
        }

        "text:tab" -> appendStyledRun(output, "    ", inheritedStyle)
        "text:line-break" -> appendStyledRun(output, "\n", inheritedStyle)
        else ->
            node.childNodes().forEach { child ->
              collectOdtRuns(
                  node = child,
                  inheritedStyle = inheritedStyle,
                  styleMap = styleMap,
                  output = output,
              )
            }
      }
    }
  }
}

/** 样式合并。 */
private fun AttachmentInlineStyle.mergeWith(other: AttachmentInlineStyle?): AttachmentInlineStyle =
    if (other == null) {
      this
    } else {
      copy(
          bold = bold || other.bold,
          italic = italic || other.italic,
          strike = strike || other.strike,
      )
    }

/** 属性忽略大小写比较。 */
private fun Element.attrEqualsIgnoreCase(name: String, expected: String): Boolean =
    attr(name).trim().equals(expected, ignoreCase = true)

/** 收集多个标签名。 */
internal fun collectMatchingDescendants(
    node: Node,
    tagNames: Set<String>,
    output: MutableList<Element>,
) {
  if (node is Element && node.tagName() in tagNames) {
    output.add(node)
  }
  node.childNodes().forEach { child -> collectMatchingDescendants(child, tagNames, output) }
}
