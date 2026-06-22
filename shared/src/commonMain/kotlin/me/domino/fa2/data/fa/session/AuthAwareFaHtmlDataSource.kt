package me.domino.fa2.data.fa.session

import me.domino.fa2.data.fa.core.FaHtmlDataSource
import me.domino.fa2.data.fa.core.HtmlResponseResult
import me.domino.fa2.data.fa.core.summarizeHtmlResult
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl

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
