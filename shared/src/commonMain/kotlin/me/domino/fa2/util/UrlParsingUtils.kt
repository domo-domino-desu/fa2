package me.domino.fa2.util

private val submissionRegex = Regex("""/view/(\d+)/?""")
private val submissionsFromSidRegex = Regex("""new~(\d+)@""")
private val journalRegex = Regex("""/journal/(\d+)/?""")

/**
 * 将相对 URL 转为绝对 URL（KMP 纯字符串实现）。
 *
 * @param baseUrl 基准地址。
 * @param maybeRelativeUrl 可能是相对路径的地址。
 */
fun toAbsoluteUrl(baseUrl: String, maybeRelativeUrl: String): String {
  val target = maybeRelativeUrl.trim()
  if (target.isBlank()) return target
  if (
      target.startsWith("http://", ignoreCase = true) ||
          target.startsWith("https://", ignoreCase = true)
  ) {
    return target
  }
  if (target.startsWith("//")) {
    return "https:$target"
  }

  val normalizedBase = baseUrl.trim()
  val schemeEnd = normalizedBase.indexOf("://")
  if (schemeEnd < 0) return target

  val hostStart = schemeEnd + 3
  val pathStart = normalizedBase.indexOf('/', hostStart)
  val origin = if (pathStart >= 0) normalizedBase.substring(0, pathStart) else normalizedBase

  if (target.startsWith('/')) {
    return "$origin$target"
  }

  val baseDir =
      if (pathStart >= 0) {
        normalizedBase.substring(0, normalizedBase.lastIndexOf('/') + 1)
      } else {
        "$normalizedBase/"
      }
  return "$baseDir$target"
}

/**
 * 解析正浮点数。
 *
 * @param raw 原始字符串。
 */
fun parsePositiveFloat(raw: String): Float? {
  val parsed = raw.trim().toFloatOrNull() ?: return null
  return parsed.takeIf { it > 0f && it.isFinite() }
}

/**
 * 从 submission URL 中解析 sid。
 *
 * @param url submission 链接。
 */
fun parseSubmissionSid(url: String): Int? =
    submissionRegex.find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()

/**
 * 从 submissions 下一页 URL 中解析 fromSid 游标。
 *
 * @param url 下一页链接。
 */
fun parseSubmissionsFromSid(url: String): Int? =
    submissionsFromSidRegex.find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()

/**
 * 从 journal URL 中解析日志 ID。
 *
 * @param url journal 链接。
 */
fun parseJournalId(url: String): Int? =
    journalRegex.find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()
