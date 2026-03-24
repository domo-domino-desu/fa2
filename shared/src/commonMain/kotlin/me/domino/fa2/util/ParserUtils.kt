package me.domino.fa2.util

import com.fleeksoft.ksoup.nodes.Document
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 解析器通用工具。 */
object ParserUtils {
  private val submissionRegex = Regex("""/view/(\d+)/?""")
  private val submissionsFromSidRegex = Regex("""new~(\d+)@""")
  private val journalRegex = Regex("""/journal/(\d+)/?""")
  private val submissionDataScriptRegex =
      Regex(
          pattern = """<script[^>]*id=["']js-submissionData["'][^>]*>(.*?)</script>""",
          options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
      )
  private val json = Json { ignoreUnknownKeys = true }
  private val fullImageTimestampRegex = Regex("""(?:^|/)art/[^/]+/(\d{9,})/""")
  private val fullImageAllowedHosts = setOf("d.furaffinity.net", "d.facdn.net")

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

  /** 根据原图 URL 推导缩略图 URL。 需要 submission sid 与原图路径中的时间戳目录（`/art/{author}/{timestamp}/...`）。 */
  fun deriveSubmissionThumbnailUrlFromFullImage(
      sid: Int,
      fullImageUrl: String,
      size: Int = 600,
  ): String? {
    if (sid <= 0 || size <= 0) return null
    val normalized = fullImageUrl.trim()
    val host = parseUrlHost(normalized)?.lowercase() ?: return null
    if (host !in fullImageAllowedHosts) return null

    val pathOnly = extractUrlPath(normalized)
    val timestamp =
        fullImageTimestampRegex.find(pathOnly)?.groupValues?.getOrNull(1)?.trim()?.takeIf { value ->
          value.isNotBlank()
        } ?: return null
    return "https://t.furaffinity.net/$sid@$size-$timestamp.jpg"
  }

  private fun parseUrlHost(url: String): String? {
    val normalized = if (url.startsWith("//")) "https:$url" else url
    val schemeIndex = normalized.indexOf("://")
    if (schemeIndex < 0) return null
    val hostStart = schemeIndex + 3
    if (hostStart >= normalized.length) return null
    val hostEnd = normalized.indexOfAny(charArrayOf('/', '?', '#'), startIndex = hostStart)
    val host =
        if (hostEnd >= 0) normalized.substring(hostStart, hostEnd)
        else normalized.substring(hostStart)
    return host.trim().ifBlank { null }
  }

  private fun extractUrlPath(url: String): String {
    val normalized = if (url.startsWith("//")) "https:$url" else url
    val schemeIndex = normalized.indexOf("://")
    if (schemeIndex < 0) return ""
    val hostStart = schemeIndex + 3
    val pathStart = normalized.indexOf('/', startIndex = hostStart)
    if (pathStart < 0) return ""
    val rawPath = normalized.substring(pathStart)
    return rawPath.substringBefore('?').substringBefore('#')
  }

  /** 解析页面中提交项对应的作者头像地址。 依赖页面内 `#js-submissionData` JSON，返回 `sid -> avatarUrl`。 */
  fun parseSubmissionAvatarUrls(html: String): Map<Int, String> {
    val root = parseSubmissionDataRoot(html) ?: return emptyMap()

    return buildMap(capacity = root.size) {
      root.forEach { (sidRaw, entryElement) ->
        val sid = sidRaw.toIntOrNull() ?: return@forEach
        val entry = entryElement.jsonObject
        val usernameLower =
            entry["lower"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
                ?: entry["username"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase().orEmpty()
        val avatarMtime = entry["avatar_mtime"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (avatarMtime.isBlank()) return@forEach
        val avatarUrl = FaUrls.avatar(usernameLower = usernameLower, avatarMtime = avatarMtime)
        if (avatarUrl.isNotBlank()) {
          put(sid, avatarUrl)
        }
      }
    }
  }

  private fun parseSubmissionDataRoot(html: String) =
      runCatching {
            val jsonText =
                submissionDataScriptRegex.find(html)?.groupValues?.getOrNull(1).orEmpty().trim()
            if (jsonText.isBlank()) {
              null
            } else {
              json.parseToJsonElement(jsonText).jsonObject
            }
          }
          .getOrNull()

  /** 若页面是系统消息页则抛出业务可读异常。 */
  fun ensureUserPageAccessible(document: Document) {
    val title = document.title().trim().lowercase()
    val bodyText = document.selectFirst("body")?.text()?.lowercase().orEmpty()

    if (title.contains("system error") && bodyText.contains("cannot be found")) {
      throw IllegalStateException("This user cannot be found")
    }
    if (bodyText.contains("access has been disabled to the account and contents of user")) {
      throw IllegalStateException("Access to this user has been disabled")
    }
    if (bodyText.contains("currently pending deletion")) {
      throw IllegalStateException("This user page is pending deletion")
    }
  }
}
