package me.domino.fa2.data.remote.translation

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.settings.TranslationProvider
import me.domino.fa2.utils.concurrency.runSuspendCatching
import me.domino.fa2.utils.html.HtmlTextBlock
import me.domino.fa2.utils.html.HtmlTextBlockExtractor
import me.domino.fa2.utils.html.normalizeHtmlText

sealed interface TextTranslationResult {
  data class Success(val translatedText: String) : TextTranslationResult

  data object Empty : TextTranslationResult

  data class Failure(val cause: Throwable) : TextTranslationResult
}

class SubmissionTextTranslationEngine(
    private val port: TranslationPort,
    private val settingsService: AppSettingsService,
) {
  private val blockExtractor = HtmlTextBlockExtractor(::normalizeHtmlText)

  fun extractDescriptionBlocks(descriptionHtml: String): List<HtmlTextBlock> =
      blockExtractor.extract(descriptionHtml)

  suspend fun translateTexts(
      sourceTexts: List<String>,
      settings: AppSettings,
      onTextResult: (index: Int, result: TextTranslationResult) -> Unit,
  ) = coroutineScope {
    val semaphore = Semaphore(settings.translationMaxConcurrency)
    chunks(sourceTexts, settings.translationChunkWordLimit).forEach { chunk ->
      launch {
        semaphore.withPermit {
          translate(chunk.texts, settings).forEachIndexed { offset, result ->
            onTextResult(chunk.start + offset, result)
          }
        }
      }
    }
  }

  suspend fun translateTexts(
      sourceTexts: List<String>,
      onTextResult: (index: Int, result: TextTranslationResult) -> Unit,
  ) {
    settingsService.ensureLoaded()
    translateTexts(sourceTexts, settingsService.settings.value, onTextResult)
  }

  suspend fun translateTexts(sourceTexts: List<String>): List<TextTranslationResult> {
    val results =
        MutableList<TextTranslationResult>(sourceTexts.size) { TextTranslationResult.Empty }
    translateTexts(sourceTexts) { index, result -> results[index] = result }
    return results
  }

  suspend fun translateText(sourceText: String): TextTranslationResult =
      if (sourceText.isBlank()) TextTranslationResult.Empty
      else translateTexts(listOf(sourceText)).single()

  private suspend fun translate(
      texts: List<String>,
      settings: AppSettings,
  ): List<TextTranslationResult> {
    if (texts.all(String::isBlank)) return List(texts.size) { TextTranslationResult.Empty }
    val translated =
        runSuspendCatching {
              port.translate(
                  TranslationRequest(
                      provider = settings.translationProvider,
                      indexTranslateModel = settings.indexTranslateModel,
                      sourceText = texts.joinToString(separator),
                      targetLanguageCode = settings.translationTargetLanguage.languageCode,
                      openAiConfig =
                          settings.openAiTranslationConfig.takeIf {
                            settings.translationProvider == TranslationProvider.OPENAI_COMPATIBLE
                          },
                  )
              )
            }
            .getOrElse { error ->
              return List(texts.size) { TextTranslationResult.Failure(error) }
            }
    return align(translated, texts.size).map { text ->
      normalizeHtmlText(text).replace(markerAtStart, "").replace(markerAtEnd, "").trim().let {
        if (it.isEmpty()) TextTranslationResult.Empty else TextTranslationResult.Success(it)
      }
    }
  }

  private fun chunks(texts: List<String>, limit: Int): List<Chunk> {
    val result = mutableListOf<Chunk>()
    var start = 0
    var words = 0
    val current = mutableListOf<String>()
    texts.forEachIndexed { index, text ->
      val size = wordCount(text)
      if (current.isNotEmpty() && words + size + 1 > limit.coerceAtLeast(1)) {
        result += Chunk(start, current.toList())
        current.clear()
        words = 0
        start = index
      }
      current += text
      words += size + if (current.size == 1) 0 else 1
    }
    if (current.isNotEmpty()) result += Chunk(start, current)
    return result
  }

  private fun align(text: String, count: Int): List<String> {
    val normalized = text.replace('％', '%').replace("\r\n", "\n")
    listOf(normalized.split(separator), normalized.split(separatorLine), normalized.split('\n'))
        .firstOrNull { it.size == count }
        ?.let {
          return it
        }
    if (count == 1) return listOf(normalized)
    val lines = normalized.lines()
    return List(count) { index ->
      lines.subList(index * lines.size / count, (index + 1) * lines.size / count).joinToString("\n")
    }
  }

  private fun wordCount(text: String): Int {
    val cjk = cjkRegex.findAll(text).count()
    return (cjk + latinRegex.findAll(cjkRegex.replace(text, " ")).count()).coerceAtLeast(1)
  }

  private data class Chunk(val start: Int, val texts: List<String>)

  private companion object {
    const val separator = "\n\n%%\n\n"
    const val separatorLine = "\n%%\n"
    val markerAtStart = Regex("""^\s*%%(?:\s|$)+""")
    val markerAtEnd = Regex("""(?:\s|^)%%\s*$""")
    val cjkRegex =
        Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF\\u3040-\\u30FF\\uAC00-\\uD7AF]")
    val latinRegex = Regex("[\\p{L}\\p{N}]+")
  }
}
