package me.domino.fa2.util.attachmenttext

/** 合并 PDF 行为段落。 */
internal fun mergeLinesIntoParagraphs(lines: List<PdfLine>): List<String> {
  if (lines.isEmpty()) return emptyList()

  val paragraphs = mutableListOf<String>()
  val thresholdsByPage =
      lines
          .groupBy { line -> line.pageIndex }
          .mapValues { (_, pageLines) ->
            val widths = pageLines.map { line -> line.width }.sortedDescending()
            if (widths.isEmpty()) {
              0.0
            } else {
              val keepCount = (widths.size * 0.8).toInt().coerceAtLeast(1)
              widths.take(keepCount).average() * 0.85
            }
          }

  var currentParagraph = StringBuilder()

  lines.forEach { line ->
    val text = normalizeParagraphText(line.text)
    if (text.isBlank()) {
      if (line.isEndOfPage && currentParagraph.isNotEmpty()) {
        paragraphs += currentParagraph.toString().trim()
        currentParagraph = StringBuilder()
      }
      return@forEach
    }

    currentParagraph.append(text)

    val lastChar = text.last()
    val isShortLine = line.width < (thresholdsByPage[line.pageIndex] ?: 0.0)
    val endsWithPunctuation = lastChar in endingPunctuation

    if (isShortLine || endsWithPunctuation || line.isEndOfPage) {
      paragraphs += currentParagraph.toString().trim()
      currentParagraph = StringBuilder()
    } else if (text.endsWith("-")) {
      currentParagraph.deleteCharAt(currentParagraph.lastIndex)
    } else {
      currentParagraph.append(' ')
    }
  }

  if (currentParagraph.isNotEmpty()) {
    paragraphs += currentParagraph.toString().trim()
  }
  return paragraphs.filter { paragraph -> paragraph.isNotBlank() }
}

/** 句末标点。 */
private val endingPunctuation = setOf('.', '!', '?', '。', '！', '？')
