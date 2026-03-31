package me.domino.fa2.ui.pages.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.kdroidfilter.webview.web.LoadingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.domino.fa2.ui.navigation.MainRouteScreen
import me.domino.fa2.util.FaUrls

/** 认证路由页面。 */
class AuthRouteScreen : Screen {
  /** 页面内容。 */
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()
    val screenModel = koinScreenModel<AuthScreenModel>()
    val state by screenModel.state.collectAsState()
    val cookieDraft by screenModel.cookieDraft().collectAsState()
    val loginMethod by screenModel.loginMethod().collectAsState()
    val webViewUiState by screenModel.webViewState().collectAsState()

    LaunchedEffect(Unit) { screenModel.bootstrap() }

    LaunchedEffect(state) {
      val snapshot = state
      if (snapshot is AuthUiState.Authenticated) {
        navigator.replaceAll(
            MainRouteScreen(deferInitialFeedLoad = screenModel.hasPendingRestoreUri())
        )
      }
    }

    when (val snapshot = state) {
      AuthUiState.Loading -> {
        AuthLoadingScreen()
      }

      is AuthUiState.AuthInvalid -> {
        val webViewAdapter = rememberSessionWebViewAdapter(initialUrl = FaUrls.login)
        val isWebViewSelected = loginMethod == AuthLoginMethod.WebView

        LaunchedEffect(isWebViewSelected) {
          if (isWebViewSelected) {
            screenModel.prepareWebViewSession(webViewAdapter.port)
          }
        }

        LaunchedEffect(
            isWebViewSelected,
            webViewAdapter.port.lastLoadedUrl,
            webViewAdapter.webViewState.loadingState,
        ) {
          if (
              isWebViewSelected && webViewAdapter.webViewState.loadingState is LoadingState.Finished
          ) {
            screenModel.syncWebViewSession(webViewAdapter.port)
          }
        }

        LaunchedEffect(isWebViewSelected, webViewAdapter.port) {
          if (!isWebViewSelected) return@LaunchedEffect
          while (true) {
            delay(webViewSyncIntervalMs)
            screenModel.syncWebViewSession(webViewAdapter.port)
          }
        }

        AuthScreen(
            state = snapshot,
            loginMethod = loginMethod,
            webViewUiState = webViewUiState,
            webViewAdapter = webViewAdapter,
            cookieDraft = cookieDraft,
            onLoginMethodChange = screenModel::selectLoginMethod,
            onCookieDraftChange = screenModel::updateCookieDraft,
            onSubmitCookie = screenModel::submitCookie,
            onRetry = screenModel::retryProbe,
            onReloadWebView = {
              webViewAdapter.port.loadUrl(webViewAdapter.port.lastLoadedUrl ?: FaUrls.login)
            },
            onConfirmWebViewLogin = {
              scope.launch { screenModel.confirmWebViewLogin(webViewAdapter.port) }
            },
        )
      }

      is AuthUiState.ProbeFailed -> {
        AuthProbeFailedScreen(state = snapshot, onRetry = screenModel::retryProbe)
      }

      is AuthUiState.Authenticated -> {
        // 导航 effect 会在上方处理，这里保持占位防止闪屏。
        AuthLoadingScreen()
      }
    }
  }
}

private const val webViewSyncIntervalMs: Long = 1_500L
