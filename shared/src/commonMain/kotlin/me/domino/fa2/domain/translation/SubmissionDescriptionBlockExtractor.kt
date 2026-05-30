package me.domino.fa2.domain.translation

import be.digitalia.compose.htmlconverter.htmlToString
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node

/** 从投稿描述 HTML 中提取可翻译的文本块。 */
internal class SubmissionDescriptionBlockExtractor(
    /** 用于规范化翻译文本的对齐器。 */
    private val resultAligner: SubmissionTranslationResultAligner,
) {
  /** 将描述 HTML 拆分为独立的文本块列表。 */
  fun extract(descriptionHtml: String): List<SubmissionDescriptionBlock> {
    if (descriptionHtml.isBlank()) return emptyList()

    val root = Ksoup.parseBodyFragment(descriptionHtml).body()
    val blockHtmlList = mutableListOf<String>()
    collectBlocksFromNodes(
        nodes = root.childNodes(),
        wrappers = emptyList(),
        output = blockHtmlList,
    )

    return blockHtmlList.mapNotNull { html ->
      val sourceText =
          resultAligner.normalizeTranslationText(htmlToString(html = html, compactMode = true))
      if (sourceText.isBlank()) {
        null
      } else {
        SubmissionDescriptionBlock(originalHtml = html, sourceText = sourceText)
      }
    }
  }

  /** 递归遍历节点列表，将内容按块边界收集到输出列表。 */
  private fun collectBlocksFromNodes(
      nodes: List<Node>,
      wrappers: List<Element>,
      output: MutableList<String>,
  ) {
    val current = StringBuilder()
    var pendingBreaks = 0

    fun flushCurrent() {
      val html = current.toString().trim()
      if (html.isNotBlank()) {
        output += wrapWithWrappers(innerHtml = html, wrappers = wrappers)
      }
      current.clear()
      pendingBreaks = 0
    }

    nodes.forEach { node ->
      if (node is Element) {
        val tag = node.tagName().lowercase()
        when {
          tag == "br" -> {
            appendNodeHtml(current, node)
            pendingBreaks += 1
            if (pendingBreaks >= 2) {
              flushCurrent()
            }
          }

          tag in standaloneBoundaryTags -> {
            flushCurrent()
            val standalone = node.outerHtml().trim()
            if (standalone.isNotBlank()) {
              output += wrapWithWrappers(innerHtml = standalone, wrappers = wrappers)
            }
          }

          tag in wrapperContainerTags -> {
            flushCurrent()
            collectBlocksFromNodes(
                nodes = node.childNodes(),
                wrappers = wrappers + listOf(node),
                output = output,
            )
          }

          else -> {
            appendNodeHtml(current, node)
            pendingBreaks = 0
          }
        }
      } else {
        if (pendingBreaks > 0 && node.outerHtml().isBlank()) {
          return@forEach
        }
        appendNodeHtml(current, node)
        pendingBreaks = 0
      }
    }

    flushCurrent()
  }

  /** 将节点的 HTML 追加到 StringBuilder（空白节点忽略）。 */
  private fun appendNodeHtml(builder: StringBuilder, node: Node) {
    val html = node.outerHtml()
    if (html.isNotBlank()) {
      builder.append(html)
    }
  }

  /** 用外层容器标签包裹内部 HTML。 */
  private fun wrapWithWrappers(innerHtml: String, wrappers: List<Element>): String {
    var wrapped = innerHtml
    wrappers.asReversed().forEach { wrapper ->
      val tag = wrapper.tagName()
      val openTag = extractOpenTag(wrapper)
      wrapped = "$openTag$wrapped</$tag>"
    }
    return wrapped
  }

  /** 从元素外部 HTML 中提取开始标签字符串。 */
  private fun extractOpenTag(wrapper: Element): String {
    val outer = wrapper.outerHtml()
    val openEnd = outer.indexOf('>')
    if (openEnd <= 0) return "<${wrapper.tagName()}>"
    return outer.substring(0, openEnd + 1)
  }

  private companion object {
    /** 独立块边界标签集合，遇到时立即切块。 */
    private val standaloneBoundaryTags =
        setOf("p", "blockquote", "li", "h1", "h2", "h3", "h4", "h5", "h6", "tr", "hr")
    /** 作为包裹容器递归处理的标签集合。 */
    private val wrapperContainerTags =
        setOf(
            "div",
            "section",
            "article",
            "code",
            "pre",
            "ul",
            "ol",
            "table",
            "tbody",
            "thead",
            "tfoot",
        )
  }
}
