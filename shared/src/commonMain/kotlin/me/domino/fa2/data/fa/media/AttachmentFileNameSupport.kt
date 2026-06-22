package me.domino.fa2.data.fa.media

/** 扩展名比较。 */
fun String.extensionEquals(expected: String): Boolean =
    substringAfterLast('.', missingDelimiterValue = "").trim().lowercase() == expected.lowercase()

/** 扩展名是否匹配列表。 */
fun String.extensionIn(vararg expected: String): Boolean =
    expected.any { candidate -> extensionEquals(candidate) }

/** 提取扩展名。 */
fun attachmentFileExtension(fileName: String?): String? =
    fileName?.substringAfterLast('.', missingDelimiterValue = "")?.trim()?.lowercase()?.ifBlank {
      null
    }

/** 规范化附件文件名。 */
fun normalizeAttachmentFileName(fileName: String?): String? =
    fileName?.substringAfterLast('/')?.substringAfterLast('\\')?.trim()?.ifBlank { null }

/** 从下载 URL / 链接文本推导文件名。 */
fun deriveAttachmentFileName(downloadUrl: String?, linkText: String? = null): String? {
  val normalizedText = normalizeAttachmentFileName(linkText)
  if (!normalizedText.isNullOrBlank() && normalizedText.contains('.')) {
    return normalizedText
  }

  val normalizedUrl = downloadUrl?.trim().orEmpty()
  if (normalizedUrl.isBlank()) return normalizedText

  val pathOnly = normalizedUrl.substringBefore('#').substringBefore('?')
  val pathFileName = normalizeAttachmentFileName(pathOnly.substringAfterLast('/'))
  if (!pathFileName.isNullOrBlank() && pathFileName.contains('.')) {
    return pathFileName
  }

  val query = normalizedUrl.substringAfter('?', missingDelimiterValue = "")
  val queryFileName =
      query.split('&').firstNotNullOfOrNull { pair ->
        val key = pair.substringBefore('=', missingDelimiterValue = "").trim().lowercase()
        val value = pair.substringAfter('=', missingDelimiterValue = "").trim()
        when (key) {
          "filename",
          "file",
          "name",
          "download" -> normalizeAttachmentFileName(value)
          else -> null
        }
      }
  if (!queryFileName.isNullOrBlank()) return queryFileName

  return normalizedText
}
