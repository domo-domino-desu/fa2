package me.domino.fa2.application.challenge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import me.domino.fa2.application.challenge.port.CfChallengeSignal
import me.domino.fa2.application.challenge.port.ChallengeResolver
import me.domino.fa2.application.challenge.port.SessionWebViewPort
import me.domino.fa2.data.network.FaCookiesStorage
import me.domino.fa2.data.network.UserAgentStorage
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizeUrl

class CfChallengeCoordinator(
    private val sessionStore: ChallengeSessionStore,
    private val cookiesStorage: FaCookiesStorage,
    private val userAgentStorage: UserAgentStorage,
    private val cookiePolicy: ChallengeCookiePolicy,
    private val probeVerifier: ChallengeProbeVerifier,
) : ChallengeResolver, CfChallengeController {
  private val log = FaLog.withTag("CfChallengeCoordinator")
  override val state: StateFlow<CfChallengeUiState> = sessionStore.state

  override suspend fun awaitResolution(challenge: CfChallengeSignal): Boolean {
    val safeUrl = summarizeUrl(challenge.requestUrl)
    val acquisition = sessionStore.acquire(challenge)
    if (acquisition.created) {
      val ray = acquisition.session.cfRay?.takeIf { it.isNotBlank() } ?: "-"
      log.i { "验证会话 -> 创建(url=${summarizeUrl(acquisition.session.triggerUrl)},cf-ray=$ray)" }
    }
    log.d { "验证会话 -> 等待(url=$safeUrl)" }
    val resolved = acquisition.session.deferred.await()
    log.i { "验证会话 -> 完成(url=$safeUrl,result=${if (resolved) "成功" else "失败"})" }
    return resolved
  }

  override suspend fun prepareWebViewSession(port: SessionWebViewPort, triggerUrl: String) {
    log.d { "验证会话 -> 准备WebView(url=${summarizeUrl(triggerUrl)})" }
    val cookieHeader = cookiesStorage.loadRawCookieHeader().orEmpty()
    for (url in linkedSetOf(FaUrls.home, triggerUrl)) {
      port.injectCookieHeader(url = url, cookieHeader = cookieHeader)
    }
  }

  override suspend fun syncUserAgentFromWebView(port: SessionWebViewPort) {
    val userAgent = readNonBlankUserAgent(port)
    if (userAgent.isBlank()) {
      log.w { "验证会话 -> 同步UA失败(空值)" }
      return
    }
    userAgentStorage.saveOverride(userAgent)
    log.d { "验证会话 -> 同步UA成功" }
  }

  override suspend fun confirmFromWebView(
      port: SessionWebViewPort,
      triggerUrl: String,
  ): Boolean {
    log.i { "验证会话 -> 开始确认(url=${summarizeUrl(triggerUrl)})" }
    val userAgent = readNonBlankUserAgent(port)
    if (userAgent.isNotBlank()) {
      userAgentStorage.saveOverride(userAgent)
    }
    val capturedCookieHeader =
        captureCookieHeaderWithRetry(
            port = port,
            urls = listOf(FaUrls.home, triggerUrl, port.lastLoadedUrl.orEmpty()),
            containsRequiredCookie = cookiePolicy::containsRequiredCookie,
        )
    return confirmCapturedCookie(
        capturedCookieHeader = capturedCookieHeader,
        triggerUrl = triggerUrl,
    )
  }

  override suspend fun cancel() {
    log.i { "验证会话 -> 用户取消" }
    completeActive(result = false)
  }

  private suspend fun confirmCapturedCookie(
      capturedCookieHeader: String,
      triggerUrl: String,
  ): Boolean {
    val session = sessionStore.currentSession() ?: return false
    sessionStore.markVerifying(session)

    if (!cookiePolicy.containsRequiredCookie(capturedCookieHeader)) {
      log.w { "验证会话 -> 未捕获cf cookie(url=${summarizeUrl(triggerUrl)})" }
      sessionStore.markVerificationFailed(session, cookiePolicy.missingCookieDetail)
      return false
    }

    val cookieSnapshot = cookiesStorage.loadRawCookieHeader().orEmpty()
    return try {
      cookiesStorage.mergeRawCookieHeader(
          raw = capturedCookieHeader,
          shouldMerge = cookiePolicy::shouldMergeCookie,
      )
      val mergedSnapshot = cookiesStorage.loadRawCookieHeader()
      log.d { "验证会话 -> 合并cookie(${if (mergedSnapshot.isBlank()) "空" else "已设置"})" }
      probeVerifier.verify(triggerUrl)
      completeActive(result = true)
      log.i { "验证会话 -> 确认成功(url=${summarizeUrl(triggerUrl)})" }
      true
    } catch (error: Throwable) {
      if (error is CancellationException) throw error
      cookiesStorage.saveRawCookieHeader(cookieSnapshot)
      log.e(error) { "验证会话 -> 确认失败(url=${summarizeUrl(triggerUrl)})" }
      sessionStore.markVerificationFailed(session, error.message ?: error::class.simpleName)
      false
    }
  }

  private suspend fun completeActive(result: Boolean) {
    val toComplete = sessionStore.complete(result)
    if (toComplete != null) {
      log.d { "验证会话 -> 清理(result=${if (result) "成功" else "失败"})" }
    }
  }
}

sealed interface CfChallengeUiState {
  data object Idle : CfChallengeUiState

  data class Active(val triggerUrl: String, val cfRay: String?, val status: CfChallengeStatus) :
      CfChallengeUiState
}

sealed interface CfChallengeStatus {
  data object AwaitingUserAction : CfChallengeStatus

  data object Verifying : CfChallengeStatus

  data class VerificationFailed(val detail: String?) : CfChallengeStatus
}
