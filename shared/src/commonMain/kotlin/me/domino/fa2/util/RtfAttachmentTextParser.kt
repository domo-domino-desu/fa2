package me.domino.fa2.util.attachmenttext

/** RTF 附件文本解析器。 */
internal object RtfAttachmentTextParser : AttachmentTextParser {
  /** 解析器格式。 */
  override val format: AttachmentTextFormat = AttachmentTextFormat.RTF

  /** 是否支持 RTF。 */
  override fun supports(fileName: String): Boolean = fileName.extensionEquals("rtf")

  /** 解析 RTF。 */
  override suspend fun parse(
      fileName: String,
      bytes: ByteArray,
      reporter: AttachmentTextProgressReporter,
  ): AttachmentTextDocument {
    reporter.report(
        stageId = "decode_bytes",
        stageFraction = 0f,
        message = "正在解码 RTF 字节",
        currentItemLabel = fileName.trim(),
    )
    val raw = bytes.decodeIso88591()
    reporter.report(
        stageId = "decode_bytes",
        stageFraction = 1f,
        message = "RTF 字节解码完成",
        currentItemLabel = "${raw.length} 字符",
    )

    val tokens = tokenizeRtf(raw = raw, reporter = reporter)
    val parsedParagraphs = interpretRtfTokens(tokens = tokens, reporter = reporter)
    reporter.report(
        stageId = "normalize_paragraphs",
        stageFraction = 0f,
        message = "正在整理段落",
    )
    val paragraphs =
        parsedParagraphs.mapIndexedNotNull { index, runs ->
          buildParagraphFromRuns(
              runs = runs,
              sourceLabel = "段落 ${index + 1}",
          )
        }
    reporter.report(
        stageId = "normalize_paragraphs",
        stageFraction = 1f,
        message = "段落整理完成",
        currentItemLabel = "${paragraphs.size} 段",
    )
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

/** RTF 标记。 */
private sealed interface RtfToken {
  /** 组开始。 */
  data object GroupStart : RtfToken

  /** 组结束。 */
  data object GroupEnd : RtfToken

  /** 控制字。 */
  data class ControlWord(
      /** 名称。 */
      val name: String,
      /** 参数。 */
      val value: Int?,
  ) : RtfToken

  /** 控制符。 */
  data class ControlSymbol(
      /** 符号。 */
      val symbol: Char,
  ) : RtfToken

  /** 文本。 */
  data class Text(
      /** 内容。 */
      val value: String,
  ) : RtfToken
}

/** RTF 解释状态。 */
private data class RtfState(
    /** 粗体。 */
    val bold: Boolean = false,
    /** 斜体。 */
    val italic: Boolean = false,
    /** 删除线。 */
    val strike: Boolean = false,
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
        "fonttbl",
        "colortbl",
        "stylesheet",
        "info",
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
        "fldinst",
    )

/** 分词。 */
private fun tokenizeRtf(raw: String, reporter: AttachmentTextProgressReporter): List<RtfToken> {
  val tokens = mutableListOf<RtfToken>()
  val textBuffer = StringBuilder()

  fun flushText() {
    if (textBuffer.isNotEmpty()) {
      tokens += RtfToken.Text(textBuffer.toString())
      textBuffer.clear()
    }
  }

  var index = 0
  while (index < raw.length) {
    when (val ch = raw[index]) {
      '{' -> {
        flushText()
        tokens += RtfToken.GroupStart
        index += 1
      }

      '}' -> {
        flushText()
        tokens += RtfToken.GroupEnd
        index += 1
      }

      '\\' -> {
        flushText()
        if (index + 1 >= raw.length) {
          index += 1
        } else {
          val next = raw[index + 1]
          when {
            next == '\\' || next == '{' || next == '}' -> {
              tokens += RtfToken.Text(next.toString())
              index += 2
            }

            next == '\'' && index + 3 < raw.length -> {
              val hex = raw.substring(index + 2, index + 4)
              val decoded = hex.toIntOrNull(16)?.toChar()?.toString().orEmpty()
              tokens += RtfToken.Text(decoded)
              index += 4
            }

            next.isLetter() -> {
              var end = index + 1
              while (end < raw.length && raw[end].isLetter()) {
                end += 1
              }
              val name = raw.substring(index + 1, end)
              var sign = 1
              if (end < raw.length && raw[end] == '-') {
                sign = -1
                end += 1
              }
              val digitStart = end
              while (end < raw.length && raw[end].isDigit()) {
                end += 1
              }
              val value =
                  if (digitStart == end) {
                    null
                  } else {
                    raw.substring(digitStart, end).toIntOrNull()?.let { parsed -> parsed * sign }
                  }
              if (end < raw.length && raw[end] == ' ') {
                end += 1
              }
              tokens += RtfToken.ControlWord(name = name, value = value)
              index = end
            }

            else -> {
              tokens += RtfToken.ControlSymbol(symbol = next)
              index += 2
            }
          }
        }
      }

      else -> {
        textBuffer.append(ch)
        index += 1
      }
    }

    if (index % 256 == 0 || index == raw.length) {
      reporter.report(
          stageId = "tokenize",
          stageFraction = index / raw.length.coerceAtLeast(1).toFloat(),
          message = "正在切分 RTF 标记",
          currentItemLabel = "${tokens.size} 个标记",
      )
    }
  }

  flushText()
  reporter.report(
      stageId = "tokenize",
      stageFraction = 1f,
      message = "RTF 标记切分完成",
      currentItemLabel = "${tokens.size} 个标记",
  )
  return tokens
}

/** 解释标记。 */
private fun interpretRtfTokens(
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
        val state = currentState()
        if (state.pendingIgnorableDestination) {
          val shouldSkip = token.name in rtfIgnoredDestinations
          replaceCurrentState { current ->
            current.copy(
                skipDestination = shouldSkip || current.skipDestination,
                pendingIgnorableDestination = false,
            )
          }
          if (shouldSkip) return@forEachIndexed
        }
        if (currentState().skipDestination && token.name !in setOf("par", "line")) {
          return@forEachIndexed
        }
        when (token.name) {
          "par" -> flushParagraph()
          "line" -> appendText("\n")
          "tab" -> appendText("    ")
          "plain" ->
              replaceCurrentState { current ->
                current.copy(bold = false, italic = false, strike = false)
              }
          "b" -> replaceCurrentState { current -> current.copy(bold = (token.value ?: 1) != 0) }
          "i" -> replaceCurrentState { current -> current.copy(italic = (token.value ?: 1) != 0) }
          "strike" ->
              replaceCurrentState { current -> current.copy(strike = (token.value ?: 1) != 0) }
          "ulnone" -> Unit
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

/** ISO-8859-1 解码。 */
private fun ByteArray.decodeIso88591(): String =
    buildString(size) {
      this@decodeIso88591.forEach { byte -> append((byte.toInt() and 0xFF).toChar()) }
    }

/** 转为安全 Unicode 文本。 */
private fun Int.toSafeUnicodeString(): String {
  val adjusted = if (this < 0) 65536 + this else this
  return adjusted.toChar().toString()
}
