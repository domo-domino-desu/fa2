package me.domino.fa2.data.fa.submission

private val fullImageTimestampRegex = Regex("""(?:^|/)art/[^/]+/(\d{9,})/""")
private val fullImageAllowedHosts = setOf("d.furaffinity.net", "d.facdn.net")

/** Derives a FA thumbnail URL from a trusted full-image CDN path. */
fun deriveSubmissionThumbnailUrlFromFullImage(
    sid: Int,
    fullImageUrl: String,
    size: Int = 600,
): String? {
  if (sid <= 0 || size <= 0) return null
  val normalized = fullImageUrl.trim()
  val host = parseUrlHost(normalized)?.lowercase() ?: return null
  if (host !in fullImageAllowedHosts) return null

  val timestamp =
      fullImageTimestampRegex
          .find(extractUrlPath(normalized))
          ?.groupValues
          ?.getOrNull(1)
          ?.trim()
          ?.takeIf(String::isNotBlank) ?: return null
  return "https://t.furaffinity.net/$sid@$size-$timestamp.jpg"
}

private fun parseUrlHost(url: String): String? {
  val normalized = if (url.startsWith("//")) "https:$url" else url
  val schemeIndex = normalized.indexOf("://")
  if (schemeIndex < 0) return null
  val hostStart = schemeIndex + 3
  if (hostStart >= normalized.length) return null
  val hostEnd = normalized.indexOfAny(charArrayOf('/', '?', '#'), startIndex = hostStart)
  return (if (hostEnd >= 0) normalized.substring(hostStart, hostEnd)
      else normalized.substring(hostStart))
      .trim()
      .ifBlank { null }
}

private fun extractUrlPath(url: String): String {
  val normalized = if (url.startsWith("//")) "https:$url" else url
  val schemeIndex = normalized.indexOf("://")
  if (schemeIndex < 0) return ""
  val pathStart = normalized.indexOf('/', startIndex = schemeIndex + 3)
  if (pathStart < 0) return ""
  return normalized.substring(pathStart).substringBefore('?').substringBefore('#')
}
