package me.domino.fa2.data.network.challenge

import me.domino.fa2.application.auth.AuthSessionController
import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizeHtmlResult
import me.domino.fa2.util.logging.summarizeUrl

/** 对业务 HTML 请求增加登录失效感知。 */
class AuthAwareFaHtmlDataSource(
    private val delegate: FaHtmlDataSource,
    private val authSessionController: AuthSessionController,
) : FaHtmlDataSource {
  private val log = FaLog.withTag("AuthAwareFaHtmlDataSource")

  override suspend fun get(url: String): HtmlResponseResult {
    val safeUrl = summarizeUrl(url)
    val result = delegate.get(url)
    if (result is HtmlResponseResult.AuthRequired) {
      log.w { "认证代理请求 -> 命中需要登录(url=$safeUrl)" }
      authSessionController.markReloginRequired(result.requestUrl)
    } else {
      log.d { "认证代理请求 -> ${summarizeHtmlResult(result)}(url=$safeUrl)" }
    }
    return result
  }
}
