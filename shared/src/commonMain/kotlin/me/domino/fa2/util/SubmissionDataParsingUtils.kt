package me.domino.fa2.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val submissionDataScriptRegex =
    Regex(
        pattern = """<script[^>]*id=["']js-submissionData["'][^>]*>(.*?)</script>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
private val submissionDataJson = Json { ignoreUnknownKeys = true }
private val fullImageTimestampRegex = Regex("""(?:^|/)art/[^/]+/(\d{9,})/""")
private val fullImageAllowedHosts = setOf("d.furaffinity.net", "d.facdn.net")

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
            submissionDataJson.parseToJsonElement(jsonText).jsonObject
          }
        }
        .getOrNull()

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
