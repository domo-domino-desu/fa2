package me.domino.fa2.data.network.challenge

import me.domino.fa2.application.auth.AuthSessionController
import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult

/** 对业务 HTML 请求增加登录失效感知。 */
class AuthAwareFaHtmlDataSource(
    private val delegate: FaHtmlDataSource,
    private val authSessionController: AuthSessionController,
) : FaHtmlDataSource {
  override suspend fun get(url: String): HtmlResponseResult {
    val result = delegate.get(url)
    if (result is HtmlResponseResult.AuthRequired) {
      authSessionController.markReloginRequired(result.requestUrl)
    }
    return result
  }
}
