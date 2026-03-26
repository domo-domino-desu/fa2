package me.domino.fa2.app.challenge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.domino.fa2.data.network.FaCookiesStorage
import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.data.network.UserAgentStorage
import me.domino.fa2.data.network.challenge.CfChallengeSignal
import me.domino.fa2.data.network.challenge.ChallengeResolver
import me.domino.fa2.ui.pages.auth.SessionWebViewPort
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.isCloudflareCookieName
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizeUrl

class CfChallengeCoordinator(
    private val cookiesStorage: FaCookiesStorage,
    private val userAgentStorage: UserAgentStorage,
    private val rawHtmlDataSource: FaHtmlDataSource,
    private val probeRetryDelaysMs: List<Long> = listOf(0L, 600L, 1_200L),
) : ChallengeResolver, CfChallengeController {
  private val log = FaLog.withTag("CfChallengeCoordinator")
  private val mutex = Mutex()
  private var active: ActiveChallengeSession? = null
  private val mutableState = MutableStateFlow<CfChallengeUiState>(CfChallengeUiState.Idle)
  override val state: StateFlow<CfChallengeUiState> = mutableState.asStateFlow()

  override suspend fun awaitResolution(challenge: CfChallengeSignal): Boolean {
    val safeUrl = summarizeUrl(challenge.requestUrl)
    val deferred =
        mutex.withLock {
          active?.deferred
              ?: run {
                val created =
                    ActiveChallengeSession(
                        triggerUrl = challenge.requestUrl,
                        cfRay = challenge.cfRay,
                        deferred = CompletableDeferred(),
                    )
                active = created
                mutableState.value =
                    CfChallengeUiState.Active(
                        triggerUrl = created.triggerUrl,
                        cfRay = created.cfRay,
                        status = CfChallengeStatus.AwaitingUserAction,
                    )
                val ray = created.cfRay?.takeIf { it.isNotBlank() } ?: "-"
                log.i { "验证会话 -> 创建(url=${summarizeUrl(created.triggerUrl)},cf-ray=$ray)" }
                created.deferred
              }
        }
    log.d { "验证会话 -> 等待(url=$safeUrl)" }
    val resolved = deferred.await()
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
        captureCfCookieHeaderWithRetry(
            port = port,
            urls = listOf(FaUrls.home, triggerUrl, port.lastLoadedUrl.orEmpty()),
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
    val session = mutex.withLock { active } ?: return false
    mutableState.value =
        CfChallengeUiState.Active(
            triggerUrl = session.triggerUrl,
            cfRay = session.cfRay,
            status = CfChallengeStatus.Verifying,
        )

    if (!containsCloudflareCookie(capturedCookieHeader)) {
      log.w { "验证会话 -> 未捕获cf cookie(url=${summarizeUrl(triggerUrl)})" }
      mutableState.value =
          CfChallengeUiState.Active(
              triggerUrl = session.triggerUrl,
              cfRay = session.cfRay,
              status =
                  CfChallengeStatus.VerificationFailed(
                      detail = "未抓取到 cf_clearance，请先在 WebView 完成验证。"
                  ),
          )
      return false
    }

    val cookieSnapshot = cookiesStorage.loadRawCookieHeader().orEmpty()
    return try {
      cookiesStorage.mergeRawCookieHeader(
          raw = capturedCookieHeader,
          shouldMerge = ::isCloudflareCookieName,
      )
      val mergedSnapshot = cookiesStorage.loadRawCookieHeader()
      log.d { "验证会话 -> 合并cookie(${if (mergedSnapshot.isBlank()) "空" else "已设置"})" }
      probeWithRetry(triggerUrl)
      completeActive(result = true)
      log.i { "验证会话 -> 确认成功(url=${summarizeUrl(triggerUrl)})" }
      true
    } catch (error: Throwable) {
      if (error is CancellationException) throw error
      cookiesStorage.saveRawCookieHeader(cookieSnapshot)
      log.e(error) { "验证会话 -> 确认失败(url=${summarizeUrl(triggerUrl)})" }
      mutableState.value =
          CfChallengeUiState.Active(
              triggerUrl = session.triggerUrl,
              cfRay = session.cfRay,
              status =
                  CfChallengeStatus.VerificationFailed(
                      detail = error.message ?: error::class.simpleName
                  ),
          )
      false
    }
  }

  private suspend fun probeWithRetry(triggerUrl: String) {
    var lastError: Throwable? = null
    val probeUrl = triggerUrl.ifBlank { FaUrls.home }
    for ((index, delayMs) in probeRetryDelaysMs.withIndex()) {
      if (delayMs > 0) delay(delayMs)
      log.d { "验证探测 -> 第${index + 1}次(url=${summarizeUrl(probeUrl)})" }
      val response = rawHtmlDataSource.get(probeUrl)
      when (response) {
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

  private suspend fun completeActive(result: Boolean) {
    val toComplete =
        mutex.withLock {
          val session = active
          active = null
          mutableState.value = CfChallengeUiState.Idle
          session
        }
    if (toComplete != null) {
      log.d { "验证会话 -> 清理(result=${if (result) "成功" else "失败"})" }
    }
    toComplete?.deferred?.complete(result)
  }
}

private suspend fun captureCfCookieHeaderWithRetry(
    port: SessionWebViewPort,
    urls: List<String>,
    maxAttempts: Int = 8,
    delayMs: Long = 450L,
): String {
  val distinctUrls = urls.map { url -> url.trim() }.filter { url -> url.isNotBlank() }.distinct()
  repeat(maxAttempts) { attempt ->
    distinctUrls.forEach { url ->
      val cookieHeader = port.captureCookieHeader(url).trim()
      if (containsCloudflareCookie(cookieHeader)) {
        return cookieHeader
      }
    }
    if (attempt < maxAttempts - 1) {
      delay(delayMs)
    }
  }
  return ""
}

private suspend fun readNonBlankUserAgent(
    port: SessionWebViewPort,
    maxAttempts: Int = 3,
    delayMs: Long = 220L,
): String {
  repeat(maxAttempts) { attempt ->
    val userAgent = port.readUserAgent()?.trim().orEmpty()
    if (userAgent.isNotBlank()) {
      return userAgent
    }
    if (attempt < maxAttempts - 1) {
      delay(delayMs)
    }
  }
  return ""
}

private fun containsCloudflareCookie(cookieHeader: String): Boolean =
    cookieHeader
        .split(';')
        .map { token -> token.trim().substringBefore('=').trim() }
        .any(::isCloudflareCookieName)

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

private data class ActiveChallengeSession(
    val triggerUrl: String,
    val cfRay: String?,
    val deferred: CompletableDeferred<Boolean>,
)
