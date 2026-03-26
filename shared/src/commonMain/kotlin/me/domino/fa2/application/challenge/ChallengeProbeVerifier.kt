package me.domino.fa2.application.challenge

import kotlinx.coroutines.delay
import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizeUrl

class ChallengeProbeVerifier(
    private val rawHtmlDataSource: FaHtmlDataSource,
    private val retryDelaysMs: List<Long> = listOf(0L, 600L, 1_200L),
) {
  private val log = FaLog.withTag("ChallengeProbeVerifier")

  suspend fun verify(triggerUrl: String) {
    var lastError: Throwable? = null
    val probeUrl = triggerUrl.ifBlank { FaUrls.home }
    for ((index, delayMs) in retryDelaysMs.withIndex()) {
      if (delayMs > 0) delay(delayMs)
      log.d { "验证探测 -> 第${index + 1}次(url=${summarizeUrl(probeUrl)})" }
      when (val response = rawHtmlDataSource.get(probeUrl)) {
        is HtmlResponseResult.Success -> return
        is HtmlResponseResult.MatureBlocked -> return
        is HtmlResponseResult.CfChallenge -> {
          log.w { "验证探测 -> 仍命中challenge" }
          lastError =
              IllegalStateException(
                  "Challenge still active${response.cfRay?.let { ", cf-ray=$it" }.orEmpty()}"
              )
        }

        is HtmlResponseResult.Error -> {
          log.w { "验证探测 -> 请求失败(${response.message})" }
          lastError = IllegalStateException(response.message)
        }
      }
    }
    throw lastError ?: IllegalStateException("Challenge probe failed")
  }
}
