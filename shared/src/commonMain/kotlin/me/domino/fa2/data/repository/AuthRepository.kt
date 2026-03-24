package me.domino.fa2.data.repository

import me.domino.fa2.data.datasource.AuthDataSource
import me.domino.fa2.data.model.AuthProbeResult
import me.domino.fa2.util.logging.FaLog

/** 认证仓储。 用于把 UI 层与底层数据源隔离。 */
class AuthRepository(
  /** 认证数据源。 */
  private val dataSource: AuthDataSource
) {
  private val log = FaLog.withTag("AuthRepository")

  /**
   * 恢复持久化会话信息。
   *
   * @return 是否存在可用 Cookie。
   */
  suspend fun restorePersistedSession(): Boolean {
    log.i { "恢复会话 -> 开始" }
    val ok = dataSource.restorePersistedSession()
    log.i { "恢复会话 -> ${if (ok) "存在Cookie" else "无Cookie"}" }
    return ok
  }

  /**
   * 提交用户输入的 Cookie。
   *
   * @param rawCookieHeader 原始 Cookie 文本。
   */
  suspend fun submitCookie(rawCookieHeader: String) {
    log.i { "提交Cookie -> 长度=${rawCookieHeader.length}" }
    dataSource.submitCookie(rawCookieHeader)
  }

  /** 退出登录并清除会话 Cookie。 */
  suspend fun clearSession() {
    log.i { "退出登录 -> 清理会话" }
    dataSource.clearSession()
  }

  /** 加载当前 Cookie Header。 */
  suspend fun loadCookieHeader(): String {
    val value = dataSource.loadCookieHeader()
    log.d { "读取Cookie -> ${if (value.isBlank()) "空" else "已设置"}" }
    return value
  }

  /**
   * 合并 challenge 流程抓取到的 Cookie。
   *
   * @param rawCookieHeader WebView 抓取结果。
   */
  suspend fun mergeChallengeCookie(rawCookieHeader: String) {
    log.i { "合并Challenge Cookie -> 长度=${rawCookieHeader.length}" }
    dataSource.mergeChallengeCookie(rawCookieHeader)
  }

  /**
   * 持久化 WebView 捕获到的 UA。
   *
   * @param userAgent WebView UA。
   */
  suspend fun updateUserAgent(userAgent: String) {
    log.i { "更新UA -> ${if (userAgent.isBlank()) "空" else "已设置"}" }
    dataSource.updateUserAgent(userAgent)
  }

  /** 探测登录状态。 */
  suspend fun probeLogin(): AuthProbeResult {
    log.i { "探测登录态 -> 开始" }
    val result = dataSource.probeLogin()
    log.i {
      "探测登录态 -> ${
                when (result) {
                    is AuthProbeResult.LoggedIn -> "已登录"
                    is AuthProbeResult.AuthInvalid -> "认证失效"
                    is AuthProbeResult.Error -> "请求失败"
                }
            }"
    }
    return result
  }
}
