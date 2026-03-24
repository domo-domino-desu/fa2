package me.domino.fa2.data.translation

import be.digitalia.compose.htmlconverter.htmlToString
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.settings.TranslationProvider

/** submission 描述翻译编排服务。 */
class SubmissionDescriptionTranslationService(
  private val translationPort: TranslationPort,
  private val settingsService: AppSettingsService,
) {
  /** 从 description HTML 中提取可翻译段。 */
  fun extractBlocks(descriptionHtml: String): List<SubmissionDescriptionBlock> {
    if (descriptionHtml.isBlank()) return emptyList()

    val root = Ksoup.parseBodyFragment(descriptionHtml).body()
    val blockHtmlList = mutableListOf<String>()
    collectBlocksFromNodes(
      nodes = root.childNodes(),
      wrappers = emptyList(),
      output = blockHtmlList,
    )

    val blocks = blockHtmlList.mapNotNull { html ->
      val sourceText = normalizeTranslationText(htmlToString(html = html, compactMode = true))
      if (sourceText.isBlank()) {
        null
      } else {
        SubmissionDescriptionBlock(originalHtml = html, sourceText = sourceText)
      }
    }
    return blocks
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

  /** 按当前设置分块翻译并回传每段状态。 */
  suspend fun translateBlocks(
    blocks: List<SubmissionDescriptionBlock>,
    onBlockResult: (index: Int, result: SubmissionDescriptionBlockResult) -> Unit,
  ) {
    if (blocks.isEmpty()) return

    settingsService.ensureLoaded()
    val settings = settingsService.settings.value

    val sourceTexts = blocks.map { block -> block.sourceText }
    val chunks =
      buildChunks(sourceTexts = sourceTexts, chunkWordLimit = settings.translationChunkWordLimit)

    coroutineScope {
      val resultChannel =
        Channel<Pair<Int, List<SubmissionDescriptionBlockResult>>>(
          capacity = chunks.size.coerceAtLeast(1)
        )
      val semaphore = Semaphore(settings.translationMaxConcurrency)

      chunks.forEach { chunk ->
        launch {
          semaphore.withPermit {
            resultChannel.send(chunk.startIndex to translateChunk(chunk, settings))
          }
        }
      }

      repeat(chunks.size) {
        val (startIndex, results) = resultChannel.receive()
        results.forEachIndexed { offset, result -> onBlockResult(startIndex + offset, result) }
      }
      resultChannel.close()
    }
  }

  private suspend fun translateChunk(
    chunk: TranslationChunk,
    settings: me.domino.fa2.data.settings.AppSettings,
  ): List<SubmissionDescriptionBlockResult> {
    if (chunk.sourceTexts.all { it.isBlank() }) {
      return List(chunk.sourceTexts.size) { SubmissionDescriptionBlockResult.EmptyResult }
    }

    val payload = chunk.sourceTexts.joinToString(BATCH_SEPARATOR)
    val translatedLines =
      runCatching {
          translationPort
            .translate(
              TranslationRequest(
                provider = settings.translationProvider,
                sourceText = payload,
                openAiConfig =
                  settings.openAiTranslationConfig.takeIf {
                    settings.translationProvider == TranslationProvider.OPENAI_COMPATIBLE
                  },
              )
            )
            .trim()
        }
        .fold(
          onSuccess = { translated ->
            if (translated.isBlank()) {
              List(chunk.sourceTexts.size) { "" }
            } else {
              parseChunkTranslation(
                translated = translated,
                expectedBlockCount = chunk.sourceTexts.size,
              )
            }
          },
          onFailure = { error ->
            return List(chunk.sourceTexts.size) { SubmissionDescriptionBlockResult.Failure(error) }
          },
        )

    return translatedLines.map { translatedLine ->
      if (translatedLine.isBlank()) {
        SubmissionDescriptionBlockResult.EmptyResult
      } else {
        SubmissionDescriptionBlockResult.Success(translatedLine)
      }
    }
  }

  private fun buildChunks(sourceTexts: List<String>, chunkWordLimit: Int): List<TranslationChunk> {
    val normalizedWordLimit = chunkWordLimit.coerceAtLeast(1)
    val chunks = mutableListOf<TranslationChunk>()

    var startIndex = 0
    val current = mutableListOf<String>()
    var currentWords = 0

    sourceTexts.forEachIndexed { index, sourceText ->
      val words = estimateWordCount(sourceText)
      val nextWords = currentWords + if (current.isEmpty()) words else words + separatorWordCost
      val shouldFlush = current.isNotEmpty() && nextWords > normalizedWordLimit

      if (shouldFlush) {
        chunks += TranslationChunk(startIndex = startIndex, sourceTexts = current.toList())
        current.clear()
        currentWords = 0
        startIndex = index
      }

      current += sourceText
      currentWords += if (current.size == 1) words else words + separatorWordCost
    }

    if (current.isNotEmpty()) {
      chunks += TranslationChunk(startIndex = startIndex, sourceTexts = current.toList())
    }

    return chunks
  }

  private fun parseChunkTranslation(translated: String, expectedBlockCount: Int): List<String> {
    val bySeparator = translated.split(BATCH_SEPARATOR)
    if (bySeparator.size == expectedBlockCount) return bySeparator

    val byMarkerLine = splitBySeparatorLine(translated)
    if (byMarkerLine.size == expectedBlockCount) return byMarkerLine

    val byLineBreak = translated.replace("\r\n", "\n").split('\n')
    if (byLineBreak.size == expectedBlockCount) return byLineBreak

    if (expectedBlockCount == 1) return listOf(translated)
    if (byLineBreak.isEmpty()) return List(expectedBlockCount) { "" }

    if (byLineBreak.size < expectedBlockCount) {
      return buildList {
        addAll(byLineBreak)
        repeat(expectedBlockCount - byLineBreak.size) { add("") }
      }
    }

    return List(expectedBlockCount) { bucket ->
      val start = bucket * byLineBreak.size / expectedBlockCount
      val end = (bucket + 1) * byLineBreak.size / expectedBlockCount
      byLineBreak.subList(start, end).joinToString("\n")
    }
  }

  private fun splitBySeparatorLine(translated: String): List<String> {
    val normalized = translated.replace("\r\n", "\n")
    val segments = mutableListOf<String>()
    val current = mutableListOf<String>()

    normalized.lineSequence().forEach { line ->
      if (line.trim() == separatorMarker) {
        segments += current.joinToString("\n")
        current.clear()
      } else {
        current += line
      }
    }

    segments += current.joinToString("\n")
    return segments
  }

  private fun estimateWordCount(text: String): Int {
    val normalized = text.trim()
    if (normalized.isBlank()) return 0

    val cjkMatches = cjkRegex.findAll(normalized).count()
    val nonCjk = cjkRegex.replace(normalized, " ")
    val tokenMatches = latinWordRegex.findAll(nonCjk).count()

    val words = cjkMatches + tokenMatches
    return words.coerceAtLeast(1)
  }

  private fun normalizeTranslationText(text: String): String =
    text
      .replace(invisibleCharsRegex, "")
      .replace("\u00A0", " ")
      .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
      .replace(Regex(" *\\n *"), "\n")
      .replace(Regex("\\n{3,}"), "\n\n")
      .trim()

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

  private data class TranslationChunk(val startIndex: Int, val sourceTexts: List<String>)

  companion object {
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
    private const val BATCH_SEPARATOR = "\n\n%%\n\n"
    private const val separatorMarker = "%%"
    private const val separatorWordCost = 1
    private val cjkRegex =
      Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF\\u3040-\\u30FF\\uAC00-\\uD7AF]")
    private val latinWordRegex = Regex("[\\p{L}\\p{N}]+")
    private val invisibleCharsRegex = Regex("[\\u200B-\\u200D\\uFEFF]")
  }
}

/** 描述块。 */
data class SubmissionDescriptionBlock(val originalHtml: String, val sourceText: String)

sealed interface SubmissionDescriptionBlockResult {
  data class Success(val translatedText: String) : SubmissionDescriptionBlockResult

  data object EmptyResult : SubmissionDescriptionBlockResult

  data class Failure(val cause: Throwable) : SubmissionDescriptionBlockResult
}
