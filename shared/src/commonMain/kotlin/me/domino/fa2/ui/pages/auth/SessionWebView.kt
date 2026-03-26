package me.domino.fa2.ui.pages.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.webview.cookie.Cookie
import io.github.kdroidfilter.webview.web.WebView
import io.github.kdroidfilter.webview.web.WebViewNavigator
import io.github.kdroidfilter.webview.web.WebViewState
import io.github.kdroidfilter.webview.web.rememberWebViewNavigator
import io.github.kdroidfilter.webview.web.rememberWebViewState
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import me.domino.fa2.application.challenge.port.SessionWebViewPort

/** 读取 UA 的超时时间（毫秒）。 */
private const val userAgentEvalTimeoutMs = 1_500L

/** 通用会话 WebView 组合适配器。 */
data class SessionWebViewAdapter(
    /** WebView 交互端口。 */
    val port: SessionWebViewPort,
    /** WebView 状态。 */
    val webViewState: WebViewState,
    /** WebView 导航器。 */
    val navigator: WebViewNavigator,
)

/**
 * 记忆通用会话 WebView 适配器。
 *
 * @param initialUrl 初始地址。
 */
@Composable
fun rememberSessionWebViewAdapter(initialUrl: String): SessionWebViewAdapter {
  val webViewState = rememberWebViewState(initialUrl)
  val navigator = rememberWebViewNavigator()
  return remember(webViewState, navigator) {
    SessionWebViewAdapter(
        port = ComposeSessionWebViewPort(webViewState = webViewState, navigator = navigator),
        webViewState = webViewState,
        navigator = navigator,
    )
  }
}

/**
 * 渲染通用会话 WebView。
 *
 * @param adapter 适配器。
 * @param modifier 组件修饰符。
 */
@Composable
fun SessionWebView(adapter: SessionWebViewAdapter, modifier: Modifier) {
  WebView(state = adapter.webViewState, navigator = adapter.navigator, modifier = modifier)
}

/** Compose WebView 端口实现。 */
private class ComposeSessionWebViewPort(
    /** WebView 状态。 */
    private val webViewState: WebViewState,
    /** WebView 导航器。 */
    private val navigator: WebViewNavigator,
) : SessionWebViewPort {
  /** 当前最后加载地址。 */
  override val lastLoadedUrl: String?
    get() = webViewState.lastLoadedUrl

  /**
   * 加载地址。
   *
   * @param url 目标地址。
   */
  override fun loadUrl(url: String) {
    navigator.loadUrl(url)
  }

  /**
   * 抓取目标地址 cookie。
   *
   * @param url 目标地址。
   */
  override suspend fun captureCookieHeader(url: String): String =
      webViewState.cookieManager.getCookies(url).joinToString("; ") { cookie ->
        "${cookie.name}=${cookie.value}"
      }

  /**
   * 注入 cookie header。
   *
   * @param url 目标地址。
   * @param cookieHeader cookie header。
   */
  override suspend fun injectCookieHeader(url: String, cookieHeader: String) {
    parseCookieHeader(cookieHeader).forEach { pair ->
      webViewState.cookieManager.setCookie(
          url = url,
          cookie =
              Cookie(
                  name = pair.name,
                  value = pair.value,
                  domain = null,
                  path = "/",
                  isSecure = false,
                  isHttpOnly = false,
              ),
      )
    }
  }

  /** 读取 WebView UA。 */
  override suspend fun readUserAgent(): String? =
      withTimeoutOrNull(userAgentEvalTimeoutMs) {
        suspendCancellableCoroutine { continuation ->
          navigator.evaluateJavaScript("navigator.userAgent") { rawUa ->
            if (!continuation.isActive) return@evaluateJavaScript
            continuation.resume(rawUa.removeSurrounding("\"").trim().ifBlank { "" })
          }
        }
      }
}

/**
 * 解析 Cookie Header 为键值对集合。
 *
 * @param rawCookieHeader 原始 cookie header。
 */
private fun parseCookieHeader(rawCookieHeader: String): List<CookieNameValue> =
    rawCookieHeader
        .split(';')
        .map { token -> token.trim() }
        .filter { token -> token.isNotBlank() && token.contains('=') }
        .map { token ->
          CookieNameValue(
              name = token.substringBefore('=').trim(),
              value = token.substringAfter('=', "").trim(),
          )
        }
        .filter { pair -> pair.name.isNotBlank() }

/** Cookie 键值对。 */
private data class CookieNameValue(
    /** Cookie 名称。 */
    val name: String,
    /** Cookie 值。 */
    val value: String,
)
