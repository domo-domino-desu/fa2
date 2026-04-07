package me.domino.fa2.application.challenge

import me.domino.fa2.util.isCloudflareCookieName
import me.domino.fa2.util.logging.FaLog

interface ChallengeCookiePolicy {
  val missingCookieDetail: String

  fun containsRequiredCookie(cookieHeader: String): Boolean

  fun shouldMergeCookie(cookieName: String): Boolean
}

class CloudflareChallengeCookiePolicy : ChallengeCookiePolicy {
  private val log = FaLog.withTag("ChallengeCookiePolicy")
  override val missingCookieDetail: String = "未抓取到 cf_clearance，请先在 WebView 完成验证。"

  override fun containsRequiredCookie(cookieHeader: String): Boolean {
    val found =
        cookieHeader
            .split(';')
            .map { token -> token.trim().substringBefore('=').trim() }
            .any(::isCloudflareCookieName)
    log.d { "Challenge Cookie校验 -> ${if (found) "命中Cloudflare Cookie" else "未命中"}" }
    return found
  }

  override fun shouldMergeCookie(cookieName: String): Boolean =
      isCloudflareCookieName(cookieName).also { shouldMerge ->
        log.d { "Challenge Cookie合并策略 -> name=$cookieName,shouldMerge=$shouldMerge" }
      }
}
