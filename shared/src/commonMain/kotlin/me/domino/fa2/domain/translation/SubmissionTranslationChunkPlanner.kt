package me.domino.fa2.domain.translation

/** 翻译分块数据，包含起始索引和该块的源文本列表。 */
internal data class SubmissionTranslationChunk(
    /** 该块在源文本列表中的起始索引。 */
    val startIndex: Int,
    /** 该块包含的源文本列表。 */
    val sourceTexts: List<String>,
)

/** 将源文本列表按字数限制切分为翻译块。 */
internal class SubmissionTranslationChunkPlanner {
  /** 根据字数限制将源文本列表构建为分块列表。 */
  fun buildChunks(
      sourceTexts: List<String>,
      chunkWordLimit: Int,
  ): List<SubmissionTranslationChunk> {
    val normalizedWordLimit = chunkWordLimit.coerceAtLeast(1)
    val chunks = mutableListOf<SubmissionTranslationChunk>()

    var startIndex = 0
    val current = mutableListOf<String>()
    var currentWords = 0

    sourceTexts.forEachIndexed { index, sourceText ->
      val words = estimateWordCount(sourceText)
      val nextWords = currentWords + if (current.isEmpty()) words else words + separatorWordCost
      val shouldFlush = current.isNotEmpty() && nextWords > normalizedWordLimit

      if (shouldFlush) {
        chunks +=
            SubmissionTranslationChunk(startIndex = startIndex, sourceTexts = current.toList())
        current.clear()
        currentWords = 0
        startIndex = index
      }

      current += sourceText
      currentWords += if (current.size == 1) words else words + separatorWordCost
    }

    if (current.isNotEmpty()) {
      chunks += SubmissionTranslationChunk(startIndex = startIndex, sourceTexts = current.toList())
    }

    return chunks
  }

  /** 估算文本的词数（兼容 CJK 和拉丁词）。 */
  private fun estimateWordCount(text: String): Int {
    val normalized = text.trim()
    if (normalized.isBlank()) return 0

    val cjkMatches = cjkRegex.findAll(normalized).count()
    val nonCjk = cjkRegex.replace(normalized, " ")
    val tokenMatches = latinWordRegex.findAll(nonCjk).count()

    val words = cjkMatches + tokenMatches
    return words.coerceAtLeast(1)
  }

  private companion object {
    /** 块间分隔符估算的额外字数开销。 */
    private const val separatorWordCost = 1
    /** 匹配 CJK 字符的正则。 */
    private val cjkRegex =
        Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF\\u3040-\\u30FF\\uAC00-\\uD7AF]")
    /** 匹配拉丁词汇的正则。 */
    private val latinWordRegex = Regex("[\\p{L}\\p{N}]+")
  }
}
