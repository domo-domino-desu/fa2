package me.domino.fa2.data.fa.session

import me.domino.fa2.data.fa.core.FaHtmlDataSource
import me.domino.fa2.data.fa.core.HtmlResponseResult
import me.domino.fa2.data.fa.core.summarizeHtmlResult
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl

/** 对 HTML 数据源增加 challenge 感知： 命中 challenge 时挂起等待用户完成验证，再自动重试一次原请求。 */
class ChallengeAwareFaHtmlDataSource(
    private val delegate: FaHtmlDataSource,
    private val challengeResolver: ChallengeResolver,
) : FaHtmlDataSource {
  private val log = FaLog.withTag("ChallengeAwareFaHtmlDataSource")

  override suspend fun get(url: String): HtmlResponseResult {
    val safeUrl = summarizeUrl(url)
    val first = delegate.get(url)
    if (first !is HtmlResponseResult.CfChallenge) {
      log.d { "Challenge代理请求 -> ${summarizeHtmlResult(first)}(url=$safeUrl)" }
      return first
    }

    val ray = first.cfRay?.takeIf { it.isNotBlank() } ?: "-"
    log.i { "Challenge代理请求 -> 命中验证(url=$safeUrl,cf-ray=$ray)" }
    val resolved =
        challengeResolver.awaitResolution(CfChallengeSignal(requestUrl = url, cfRay = first.cfRay))
    if (!resolved) {
      log.w { "Challenge代理请求 -> 验证未完成(url=$safeUrl)" }
      return HtmlResponseResult.ChallengeAborted
    }

    val retried = delegate.get(url)
    log.i { "Challenge代理请求 -> 重试${summarizeHtmlResult(retried)}(url=$safeUrl)" }
    return retried
  }
}
