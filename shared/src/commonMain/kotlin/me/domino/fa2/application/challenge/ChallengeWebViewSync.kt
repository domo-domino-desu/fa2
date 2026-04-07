package me.domino.fa2.application.challenge

import kotlinx.coroutines.delay
import me.domino.fa2.domain.challenge.SessionWebViewPort
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizeUrl

private val challengeWebViewSyncLog = FaLog.withTag("ChallengeWebViewSync")

internal suspend fun captureCookieHeaderWithRetry(
    port: SessionWebViewPort,
    urls: List<String>,
    containsRequiredCookie: (String) -> Boolean,
    maxAttempts: Int = 8,
    delayMs: Long = 450L,
): String {
  val distinctUrls = urls.map { url -> url.trim() }.filter { url -> url.isNotBlank() }.distinct()
  challengeWebViewSyncLog.d {
    "抓取Challenge Cookie -> 开始(urls=${distinctUrls.joinToString { url -> summarizeUrl(url) }},maxAttempts=$maxAttempts)"
  }
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
      challengeWebViewSyncLog.d { "抓取Challenge Cookie -> 第${attempt + 1}次命中" }
      return mergedHeader
    }
    challengeWebViewSyncLog.d { "抓取Challenge Cookie -> 第${attempt + 1}次未命中" }
    if (attempt < maxAttempts - 1) {
      delay(delayMs)
    }
  }
  challengeWebViewSyncLog.w { "抓取Challenge Cookie -> 结束(未命中)" }
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
      challengeWebViewSyncLog.d { "读取Challenge UA -> 第${attempt + 1}次成功" }
      return userAgent
    }
    challengeWebViewSyncLog.d { "读取Challenge UA -> 第${attempt + 1}次为空" }
    if (attempt < maxAttempts - 1) {
      delay(delayMs)
    }
  }
  challengeWebViewSyncLog.w { "读取Challenge UA -> 结束(空值)" }
  return ""
}
