package me.domino.fa2.data.attachmenttext

import me.domino.fa2.application.attachmenttext.AttachmentTextProgressReporter
import me.domino.fa2.domain.attachmenttext.*

/** RTF 标记。 */
internal sealed interface RtfToken {
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

/** 分词。 */
internal fun tokenizeRtf(raw: String, reporter: AttachmentTextProgressReporter): List<RtfToken> {
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

/** ISO-8859-1 解码。 */
internal fun ByteArray.decodeIso88591(): String =
    buildString(size) {
      this@decodeIso88591.forEach { byte -> append((byte.toInt() and 0xFF).toChar()) }
    }
