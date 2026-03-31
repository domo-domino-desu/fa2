package me.domino.fa2.ui.pages.auth

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import fa2.shared.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.domino.fa2.application.challenge.port.SessionWebViewPort
import me.domino.fa2.data.model.AuthProbeResult
import me.domino.fa2.data.repository.AuthRepository
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.i18n.SystemLanguageProvider
import me.domino.fa2.i18n.appString
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.logging.FaLog

/** 登录页面状态模型。 */
class AuthScreenModel(
    /** 认证仓储。 */
    private val authRepository: AuthRepository,
    private val settingsService: AppSettingsService? = null,
    private val systemLanguageProvider: SystemLanguageProvider? = null,
) : StateScreenModel<AuthUiState>(AuthUiState.Loading) {
  private val log = FaLog.withTag("AuthScreenModel")

  /** Cookie 输入草稿流。 */
  private val cookieDraftState = MutableStateFlow("")

  /** 当前选中的登录方式。 */
  private val loginMethodState = MutableStateFlow(AuthLoginMethod.WebView)

  /** WebView 登录交互状态。 */
  private val webViewUiState =
      MutableStateFlow(
          AuthWebViewUiState(
              isConfirming = false,
              statusMessage = appString(Res.string.default_web_view_login_status),
          )
      )

  /** 最近一次已同步的 WebView cookie 快照。 */
  private var lastSyncedWebViewCookieSnapshot: String = ""

  /** 最近一次已同步的 WebView UA。 */
  private var lastSyncedWebViewUserAgent: String = ""

  /** 暴露 Cookie 输入草稿。 */
  fun cookieDraft(): StateFlow<String> = cookieDraftState.asStateFlow()

  /** 暴露当前登录方式。 */
  fun loginMethod(): StateFlow<AuthLoginMethod> = loginMethodState.asStateFlow()

  /** 暴露 WebView 登录交互状态。 */
  fun webViewState(): StateFlow<AuthWebViewUiState> = webViewUiState.asStateFlow()

  /**
   * 更新 Cookie 输入内容。
   *
   * @param draft 新输入文本。
   */
  fun updateCookieDraft(draft: String) {
    cookieDraftState.value = draft
  }

  /**
   * 选择登录方式。
   *
   * @param method 目标登录方式。
   */
  fun selectLoginMethod(method: AuthLoginMethod) {
    loginMethodState.value = method
  }

  /** 启动初始化：先读 KV，再决定是否进入认证无效态。 */
  fun bootstrap() {
    log.i { "认证初始化 -> 开始" }
    screenModelScope.launch {
      mutableState.value = AuthUiState.Loading
      resetAuthInteractionState()
      val hasCookie = authRepository.restorePersistedSession()
      refreshCookieDraft()
      if (!hasCookie) {
        log.w { "认证初始化 -> 无可用Cookie" }
        mutableState.value =
            AuthUiState.AuthInvalid(message = appString(Res.string.missing_persisted_cookie))
        return@launch
      }
      probeAndUpdate()
    }
  }

  /** 提交 Cookie 并重新探测登录态。 */
  fun submitCookie() {
    log.i { "提交Cookie -> 开始" }
    screenModelScope.launch {
      val normalized = cookieDraftState.value.trim()
      if (normalized.isBlank()) {
        log.w { "提交Cookie -> 失败(空输入)" }
        mutableState.value =
            AuthUiState.AuthInvalid(message = appString(Res.string.empty_cookie_header))
        return@launch
      }

      authRepository.submitCookie(normalized)
      refreshCookieDraft()
      probeAndUpdate()
    }
  }

  /** 重试当前登录态探测。 */
  fun retryProbe() {
    log.i { "重试探测 -> 开始" }
    screenModelScope.launch { probeAndUpdate() }
  }

  /**
   * 将当前持久化会话注入到 WebView。
   *
   * @param port 会话 WebView 端口。
   */
  suspend fun prepareWebViewSession(port: SessionWebViewPort) {
    log.d { "准备WebView会话 -> 开始" }
    val cookieHeader = authRepository.loadCookieHeader()
    sessionSyncUrls(port).forEach { url ->
      port.injectCookieHeader(url = url, cookieHeader = cookieHeader)
    }
    syncUserAgentFromWebView(port)
    updateWebViewUiState(statusMessage = appString(Res.string.default_web_view_login_status))
  }

  /**
   * 从 WebView 同步 cookie 与 UA 到应用侧。
   *
   * @param port 会话 WebView 端口。
   */
  suspend fun syncWebViewSession(port: SessionWebViewPort) {
    val changedCookie = syncCookieSnapshotFromWebView(port)
    val changedUa = syncUserAgentFromWebView(port)
    if (changedCookie || changedUa) {
      updateWebViewUiState(statusMessage = appString(Res.string.web_view_session_synced))
    }
  }

  /**
   * 读取 WebView 登录结果并执行最终登录态探测。
   *
   * @param port 会话 WebView 端口。
   */
  suspend fun confirmWebViewLogin(port: SessionWebViewPort) {
    log.i { "WebView登录确认 -> 开始" }
    updateWebViewUiState(
        isConfirming = true,
        statusMessage = appString(Res.string.reading_web_view_login_info),
    )

    val cookieChanged = syncCookieSnapshotFromWebView(port)
    syncUserAgentFromWebView(port)

    if (!cookieChanged && cookieDraftState.value.isBlank()) {
      updateWebViewUiState(
          isConfirming = true,
          statusMessage = appString(Res.string.no_cookie_captured_checking_session),
      )
    } else {
      updateWebViewUiState(
          isConfirming = true,
          statusMessage = appString(Res.string.cookie_synced_validating_login_state),
      )
    }

    val nextState = probeAndUpdate()
    when (nextState) {
      is AuthUiState.Authenticated -> {
        updateWebViewUiState(
            isConfirming = false,
            statusMessage = appString(Res.string.login_succeeded_entering_home),
        )
      }

      is AuthUiState.AuthInvalid -> {
        updateWebViewUiState(
            isConfirming = false,
            statusMessage = appString(Res.string.login_incomplete, nextState.message),
        )
      }

      is AuthUiState.ProbeFailed -> {
        updateWebViewUiState(isConfirming = false, statusMessage = nextState.message)
      }

      AuthUiState.Loading -> {
        updateWebViewUiState(
            isConfirming = false,
            statusMessage = appString(Res.string.default_web_view_login_status),
        )
      }
    }
  }

  /** 执行一次登录态探测并更新 UI 状态。 */
  private suspend fun probeAndUpdate(): AuthUiState {
    val nextState =
        try {
          when (val result = authRepository.probeLogin()) {
            is AuthProbeResult.LoggedIn -> {
              AuthUiState.Authenticated(username = result.username)
            }

            is AuthProbeResult.AuthInvalid -> {
              AuthUiState.AuthInvalid(message = result.message)
            }

            is AuthProbeResult.ProbeFailed -> {
              AuthUiState.ProbeFailed(message = result.message)
            }
          }
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Throwable) {
          AuthUiState.ProbeFailed(
              message = error.message?.takeIf { it.isNotBlank() } ?: error.toString()
          )
        }
    mutableState.value = nextState
    log.i { "登录态探测 -> ${summarizeAuthUiState(nextState)}" }
    return nextState
  }

  /** 重置认证交互状态。 */
  private fun resetAuthInteractionState() {
    loginMethodState.value = AuthLoginMethod.WebView
    updateWebViewUiState(
        isConfirming = false,
        statusMessage = appString(Res.string.default_web_view_login_status),
    )
    lastSyncedWebViewCookieSnapshot = ""
    lastSyncedWebViewUserAgent = ""
  }

  /** 刷新输入框草稿，并同步内部 cookie 快照。 */
  private suspend fun refreshCookieDraft() {
    val loadedCookie = authRepository.loadCookieHeader()
    cookieDraftState.value = loadedCookie
    lastSyncedWebViewCookieSnapshot = loadedCookie
  }

  /**
   * 从 WebView 同步 cookie 快照。
   *
   * @return 本次是否写入了新快照。
   */
  private suspend fun syncCookieSnapshotFromWebView(port: SessionWebViewPort): Boolean {
    val mergedCookieHeader = captureMergedWebViewCookieHeader(port)
    if (mergedCookieHeader.isBlank() || mergedCookieHeader == lastSyncedWebViewCookieSnapshot) {
      return false
    }

    authRepository.syncWebViewCookie(mergedCookieHeader)
    refreshCookieDraft()
    return true
  }

  /**
   * 从 WebView 同步 UA。
   *
   * @return 本次是否写入了新 UA。
   */
  private suspend fun syncUserAgentFromWebView(port: SessionWebViewPort): Boolean {
    val userAgent = port.readUserAgent()?.trim().orEmpty()
    if (userAgent.isBlank() || userAgent == lastSyncedWebViewUserAgent) {
      return false
    }
    authRepository.updateUserAgent(userAgent)
    lastSyncedWebViewUserAgent = userAgent
    return true
  }

  /** 合并抓取多个 URL 下的 cookie 快照。 */
  private suspend fun captureMergedWebViewCookieHeader(port: SessionWebViewPort): String {
    val merged = LinkedHashMap<String, String>()
    sessionSyncUrls(port).forEach { url ->
      parseCookieHeader(port.captureCookieHeader(url)).forEach { (name, value) ->
        merged[name] = value
      }
    }
    return merged.entries.joinToString("; ") { (name, value) -> "$name=$value" }
  }

  /** 当前会话同步需要覆盖的 URL。 */
  private fun sessionSyncUrls(port: SessionWebViewPort): List<String> =
      linkedSetOf(FaUrls.home, FaUrls.login, port.lastLoadedUrl ?: FaUrls.login).toList()

  /** 更新 WebView 交互状态。 */
  private fun updateWebViewUiState(
      isConfirming: Boolean = webViewUiState.value.isConfirming,
      statusMessage: String = webViewUiState.value.statusMessage,
  ) {
    webViewUiState.value =
        AuthWebViewUiState(
            isConfirming = isConfirming,
            statusMessage = statusMessage,
        )
  }

  private fun summarizeAuthUiState(state: AuthUiState): String =
      when (state) {
        AuthUiState.Loading -> "加载中"
        is AuthUiState.Authenticated -> "已认证"
        is AuthUiState.AuthInvalid -> "认证无效"
        is AuthUiState.ProbeFailed -> "探测失败"
      }
}

/** 登录方式。 */
enum class AuthLoginMethod {
  WebView,
  Cookie,
}

/** WebView 登录交互状态。 */
data class AuthWebViewUiState(
    /** 当前是否正在执行最终确认。 */
    val isConfirming: Boolean,
    /** 展示给用户的状态说明。 */
    val statusMessage: String,
)

/** 登录页状态定义。 */
sealed interface AuthUiState {
  /** 初始化中。 */
  data object Loading : AuthUiState

  /**
   * 认证无效，需要用户输入 Cookie。
   *
   * @property message 提示文案。
   */
  data class AuthInvalid(
      /** 展示给用户的输入提示文案。 */
      val message: String
  ) : AuthUiState

  /**
   * 登录态探测失败，但当前会话信息仍然保留。
   *
   * @property message 失败摘要。
   */
  data class ProbeFailed(
      /** 展示给用户的错误说明。 */
      val message: String
  ) : AuthUiState

  /**
   * 已认证完成。
   *
   * @property username 当前用户名，可能为空。
   */
  data class Authenticated(
      /** 已登录用户名；页面缺信息时可能为空。 */
      val username: String?
  ) : AuthUiState
}

/** 解析 Cookie Header 为键值对集合。 */
private fun parseCookieHeader(rawCookieHeader: String): List<Pair<String, String>> =
    rawCookieHeader
        .split(';')
        .map { token -> token.trim() }
        .filter { token -> token.isNotBlank() && token.contains('=') }
        .mapNotNull { token ->
          val name = token.substringBefore('=').trim()
          val value = token.substringAfter('=', "").trim()
          name.takeIf { it.isNotBlank() }?.let { nonBlankName -> nonBlankName to value }
        }
