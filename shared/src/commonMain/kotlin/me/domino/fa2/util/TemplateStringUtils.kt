package me.domino.fa2.util

private val braceTemplateTokenRegex = Regex("""\{([A-Za-z0-9_]+)\}""")
private val whitespaceRegex = Regex("""\s+""")
private val connectorSpacingRegex = Regex("""\s*([_-])\s*""")
private val duplicateConnectorRegex = Regex("""([_-])\1+""")

/** 渲染 `{key}` 风格模板。未知变量会保持原样。 */
fun renderBraceTemplate(template: String, values: Map<String, String>): String {
  if (template.isEmpty()) return template
  return braceTemplateTokenRegex.replace(template) { match ->
    val key = match.groupValues.getOrElse(1) { "" }
    if (key in values) {
      values[key].orEmpty()
    } else {
      match.value
    }
  }
}

/** 清理模板渲染后常见的空白与连接符冗余。 */
fun cleanupTemplateRenderedText(raw: String): String {
  if (raw.isBlank()) return ""
  return raw.replace(whitespaceRegex, " ")
      .replace(connectorSpacingRegex) { match -> match.groupValues[1] }
      .replace(duplicateConnectorRegex, "$1")
      .trim(' ', '-', '_', '.')
}
