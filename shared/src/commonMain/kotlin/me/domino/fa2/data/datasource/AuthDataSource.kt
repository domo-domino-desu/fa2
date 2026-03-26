package me.domino.fa2.data.datasource

import me.domino.fa2.data.model.AuthProbeResult
import me.domino.fa2.data.network.FaCookiesStorage
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.data.network.UserAgentStorage
import me.domino.fa2.data.network.endpoint.HomeEndpoint
import me.domino.fa2.util.isCloudflareCookieName

/** 登录态数据源。 */
class AuthDataSource(
    /** 首页端点，用于登录态探测。 */
    private val homeEndpoint: HomeEndpoint,
    /** Cookie 存储。 */
    private val cookiesStorage: FaCookiesStorage,
    /** UA 存储。 */
    private val userAgentStorage: UserAgentStorage,
) {
  /**
   * 启动时恢复会话持久化数据。
   *
   * @return 是否恢复到非空 Cookie。
   */
  suspend fun restorePersistedSession(): Boolean {
    userAgentStorage.loadPersistedIfNeeded()
    cookiesStorage.restorePersistedIfNeeded()
    val hasCookie = cookiesStorage.hasCookie()
    return hasCookie
  }

  /**
   * 提交用户输入的 Cookie。
   *
   * @param rawCookieHeader 原始 Cookie 文本。
   */
  suspend fun submitCookie(rawCookieHeader: String) {
    // 手动输入仅接收 auth 相关 cookie；Cloudflare cookie 只能来自内置 WebView。
    cookiesStorage.replaceRawCookieHeader(
        raw = rawCookieHeader,
        preserveExisting = ::isCloudflareCookieName,
        acceptIncoming = { cookieName -> !isCloudflareCookieName(cookieName) },
    )
  }

  /** 清除当前会话（退出登录）。 */
  suspend fun clearSession() {
    cookiesStorage.clear()
  }

  /** 读取当前 cookie header。 */
  suspend fun loadCookieHeader(): String = cookiesStorage.loadRawCookieHeader()

  /**
   * 以 WebView 捕获结果覆盖当前 Cookie 快照。
   *
   * @param rawCookieHeader 从 WebView 抓取并合并后的 Cookie Header。
   */
  suspend fun syncWebViewCookie(rawCookieHeader: String) {
    val normalized = rawCookieHeader.trim()
    if (normalized.isBlank()) return
    cookiesStorage.saveRawCookieHeader(normalized)
  }

  /**
   * 仅合并 Cloudflare 相关 cookie。
   *
   * @param rawCookieHeader 从 WebView 抓取的 Cookie Header。
   */
  suspend fun mergeChallengeCookie(rawCookieHeader: String) {
    cookiesStorage.mergeRawCookieHeader(
        raw = rawCookieHeader,
        shouldMerge = ::isCloudflareCookieName,
    )
  }

  /**
   * 持久化 WebView 捕获到的 UA。
   *
   * @param userAgent WebView UA。
   */
  suspend fun updateUserAgent(userAgent: String) {
    userAgentStorage.saveOverride(userAgent)
  }

  /** 探测当前登录态。 */
  suspend fun probeLogin(): AuthProbeResult {
    return when (val response = homeEndpoint.fetch()) {
      is HtmlResponseResult.Success -> {
        if (isLoggedIn(response.body)) {
          AuthProbeResult.LoggedIn(username = extractUsername(response.body))
        } else {
          AuthProbeResult.AuthInvalid(message = "认证失效，请粘贴浏览器 Cookie。")
        }
      }

      is HtmlResponseResult.CfChallenge -> {
        AuthProbeResult.Error(message = "Cloudflare challenge 尚未完成，请先在覆盖层完成验证。")
      }

      is HtmlResponseResult.MatureBlocked -> {
        AuthProbeResult.AuthInvalid(message = response.reason)
      }

      is HtmlResponseResult.Error -> {
        AuthProbeResult.Error(message = response.message)
      }
    }
  }

  /**
   * 判断首页 HTML 是否表示已登录。
   *
   * @param body 首页 HTML。
   */
  private fun isLoggedIn(body: String): Boolean {
    val normalized = body.lowercase()
    return "data-user-logged-in=\"1\"" in normalized || "loggedin_user_avatar" in normalized
  }

  /**
   * 从页面中提取用户名。
   *
   * @param body 页面 HTML。
   */
  private fun extractUsername(body: String): String? =
      Regex("""/user/([^/\"']+)""").find(body)?.groupValues?.getOrNull(1)?.takeIf {
        it.isNotBlank()
      }
}
