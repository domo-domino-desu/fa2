package me.domino.fa2.domain.translation

import be.digitalia.compose.htmlconverter.htmlToString
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node

internal class SubmissionDescriptionBlockExtractor(
    private val resultAligner: SubmissionTranslationResultAligner,
) {
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

  private fun appendNodeHtml(builder: StringBuilder, node: Node) {
    val html = node.outerHtml()
    if (html.isNotBlank()) {
      builder.append(html)
    }
  }

  private fun wrapWithWrappers(innerHtml: String, wrappers: List<Element>): String {
    var wrapped = innerHtml
    wrappers.asReversed().forEach { wrapper ->
      val tag = wrapper.tagName()
      val openTag = extractOpenTag(wrapper)
      wrapped = "$openTag$wrapped</$tag>"
    }
    return wrapped
  }

  private fun extractOpenTag(wrapper: Element): String {
    val outer = wrapper.outerHtml()
    val openEnd = outer.indexOf('>')
    if (openEnd <= 0) return "<${wrapper.tagName()}>"
    return outer.substring(0, openEnd + 1)
  }

  private companion object {
    private val standaloneBoundaryTags =
        setOf("p", "blockquote", "li", "h1", "h2", "h3", "h4", "h5", "h6", "tr", "hr")
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
