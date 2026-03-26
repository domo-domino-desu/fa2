package me.domino.fa2.application.challenge

import me.domino.fa2.util.isCloudflareCookieName

interface ChallengeCookiePolicy {
  val missingCookieDetail: String

  fun containsRequiredCookie(cookieHeader: String): Boolean

  fun shouldMergeCookie(cookieName: String): Boolean
}

class CloudflareChallengeCookiePolicy : ChallengeCookiePolicy {
  override val missingCookieDetail: String = "未抓取到 cf_clearance，请先在 WebView 完成验证。"

  override fun containsRequiredCookie(cookieHeader: String): Boolean =
      cookieHeader
          .split(';')
          .map { token -> token.trim().substringBefore('=').trim() }
          .any(::isCloudflareCookieName)

  override fun shouldMergeCookie(cookieName: String): Boolean = isCloudflareCookieName(cookieName)
}
