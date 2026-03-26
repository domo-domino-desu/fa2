package me.domino.fa2.application.challenge

import kotlinx.coroutines.delay
import me.domino.fa2.application.challenge.port.SessionWebViewPort

internal suspend fun captureCookieHeaderWithRetry(
    port: SessionWebViewPort,
    urls: List<String>,
    containsRequiredCookie: (String) -> Boolean,
    maxAttempts: Int = 8,
    delayMs: Long = 450L,
): String {
  val distinctUrls = urls.map { url -> url.trim() }.filter { url -> url.isNotBlank() }.distinct()
  repeat(maxAttempts) { attempt ->
    val mergedCookies = LinkedHashMap<String, String>()
    distinctUrls.forEach { url ->
      val cookieHeader = port.captureCookieHeader(url).trim()
      cookieHeader
          .split(';')
          .map { token -> token.trim() }
          .filter { token -> token.isNotBlank() && token.contains('=') }
          .forEach { token ->
            val name = token.substringBefore('=').trim()
            val value = token.substringAfter('=', "").trim()
            if (name.isNotBlank()) {
              mergedCookies[name] = value
            }
          }
    }
    val mergedHeader = mergedCookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }
    if (containsRequiredCookie(mergedHeader)) {
      return mergedHeader
    }
    if (attempt < maxAttempts - 1) {
      delay(delayMs)
    }
  }
  return ""
}

internal suspend fun readNonBlankUserAgent(
    port: SessionWebViewPort,
    maxAttempts: Int = 3,
    delayMs: Long = 220L,
): String {
  repeat(maxAttempts) { attempt ->
    val userAgent = port.readUserAgent()?.trim().orEmpty()
    if (userAgent.isNotBlank()) {
      return userAgent
    }
    if (attempt < maxAttempts - 1) {
      delay(delayMs)
    }
  }
  return ""
}
