package me.domino.fa2.data.attachmenttext

import me.domino.fa2.application.attachmenttext.AttachmentTextProgressReporter
import me.domino.fa2.domain.attachmenttext.*

/** RTF 解释状态。 */
private data class RtfState(
    /** 粗体。 */
    val bold: Boolean = false,
    /** 斜体。 */
    val italic: Boolean = false,
    /** 删除线。 */
    val strike: Boolean = false,
    /** 下划线。 */
    val underline: Boolean = false,
    /** 跳过目标。 */
    val skipDestination: Boolean = false,
    /** Unicode 后跳过字符数。 */
    val unicodeSkipCount: Int = 1,
    /** 待跳过的回退字符数。 */
    val pendingFallbackSkip: Int = 0,
    /** 下一个控制字是否是可忽略目标。 */
    val pendingIgnorableDestination: Boolean = false,
)

/** 目标控制字。 */
private val rtfIgnoredDestinations =
    setOf(
        "latentstyles",
        "fonttbl",
        "colortbl",
        "stylesheet",
        "info",
        "rsidtbl",
        "mmathpr",
        "pict",
        "object",
        "header",
        "headerl",
        "headerr",
        "headerf",
        "footer",
        "footerl",
        "footerr",
        "footerf",
        "generator",
        "xmlnstbl",
        "datastore",
        "themedata",
        "wgrffmtfilter",
        "fldinst",
    )

/** 解释标记。 */
internal fun interpretRtfTokens(
    tokens: List<RtfToken>,
    reporter: AttachmentTextProgressReporter,
): List<List<StyledTextRun>> {
  val paragraphs = mutableListOf<MutableList<StyledTextRun>>()
  var currentParagraph = mutableListOf<StyledTextRun>()
  val stateStack = mutableListOf(RtfState())

  fun currentState(): RtfState = stateStack.last()

  fun replaceCurrentState(transform: (RtfState) -> RtfState) {
    stateStack[stateStack.lastIndex] = transform(currentState())
  }

  fun flushParagraph() {
    if (currentParagraph.isNotEmpty()) {
      paragraphs += currentParagraph
      currentParagraph = mutableListOf()
    }
  }

  fun appendText(rawText: String) {
    val state = currentState()
    if (state.skipDestination || rawText.isEmpty()) return
    val dropped =
        if (state.pendingFallbackSkip > 0) {
          rawText.drop(state.pendingFallbackSkip.coerceAtMost(rawText.length))
        } else {
          rawText
        }
    if (state.pendingFallbackSkip > 0) {
      replaceCurrentState { current -> current.copy(pendingFallbackSkip = 0) }
    }
    if (dropped.isEmpty()) return
    currentParagraph +=
        StyledTextRun(
            text = dropped,
            style =
                AttachmentInlineStyle(
                    bold = state.bold,
                    italic = state.italic,
                    strike = state.strike,
                    underline = state.underline,
                ),
        )
  }

  tokens.forEachIndexed { index, token ->
    when (token) {
      RtfToken.GroupStart -> stateStack += currentState().copy(pendingIgnorableDestination = false)

      RtfToken.GroupEnd -> {
        if (stateStack.size > 1) {
          stateStack.removeAt(stateStack.lastIndex)
        }
      }

      is RtfToken.Text -> appendText(token.value)

      is RtfToken.ControlSymbol -> {
        when (token.symbol) {
          '*' -> replaceCurrentState { current -> current.copy(pendingIgnorableDestination = true) }
          '~' -> appendText(" ")
          '_' -> appendText("-")
          '-' -> appendText("-")
        }
      }

      is RtfToken.ControlWord -> {
        val normalizedName = token.name.lowercase()
        val state = currentState()
        if (normalizedName in rtfIgnoredDestinations) {
          replaceCurrentState { current ->
            current.copy(skipDestination = true, pendingIgnorableDestination = false)
          }
          return@forEachIndexed
        }
        if (state.pendingIgnorableDestination) {
          val shouldSkip = normalizedName in rtfIgnoredDestinations
          replaceCurrentState { current ->
            current.copy(
                skipDestination = shouldSkip || current.skipDestination,
                pendingIgnorableDestination = false,
            )
          }
          if (shouldSkip) return@forEachIndexed
        }
        if (currentState().skipDestination && normalizedName !in setOf("par", "line")) {
          return@forEachIndexed
        }
        when (normalizedName) {
          "par" -> flushParagraph()
          "line" -> appendText("\n")
          "tab" -> appendText("    ")
          "plain" ->
              replaceCurrentState { current ->
                current.copy(bold = false, italic = false, strike = false, underline = false)
              }
          "b" -> replaceCurrentState { current -> current.copy(bold = (token.value ?: 1) != 0) }
          "i" -> replaceCurrentState { current -> current.copy(italic = (token.value ?: 1) != 0) }
          "strike" ->
              replaceCurrentState { current -> current.copy(strike = (token.value ?: 1) != 0) }
          "ul" ->
              replaceCurrentState { current -> current.copy(underline = (token.value ?: 1) != 0) }
          "ulnone" -> replaceCurrentState { current -> current.copy(underline = false) }
          "uc" ->
              replaceCurrentState { current ->
                current.copy(
                    unicodeSkipCount = token.value?.coerceAtLeast(0) ?: current.unicodeSkipCount
                )
              }
          "u" -> {
            val codePoint = token.value ?: 0
            appendText(codePoint.toSafeUnicodeString())
            replaceCurrentState { current ->
              current.copy(pendingFallbackSkip = current.unicodeSkipCount)
            }
          }
        }
      }
    }

    if ((index + 1) % 128 == 0 || index == tokens.lastIndex) {
      reporter.report(
          stageId = "interpret_groups",
          stageFraction = (index + 1) / tokens.size.coerceAtLeast(1).toFloat(),
          message = "正在解释 RTF 语法组",
          currentItemLabel = "${paragraphs.size + 1} 段",
      )
    }
  }

  flushParagraph()
  reporter.report(
      stageId = "interpret_groups",
      stageFraction = 1f,
      message = "RTF 语法组解释完成",
      currentItemLabel = "${paragraphs.size} 段",
  )
  return paragraphs
}

/** 转为安全 Unicode 文本。 */
private fun Int.toSafeUnicodeString(): String {
  val adjusted = if (this < 0) 65536 + this else this
  return adjusted.toChar().toString()
}
