package me.domino.fa2.app.challenge

import kotlinx.coroutines.flow.StateFlow
import me.domino.fa2.ui.pages.auth.SessionWebViewPort

interface CfChallengeController {
  val state: StateFlow<CfChallengeUiState>

  suspend fun prepareWebViewSession(port: SessionWebViewPort, triggerUrl: String)

  suspend fun syncUserAgentFromWebView(port: SessionWebViewPort)

  suspend fun confirmFromWebView(port: SessionWebViewPort, triggerUrl: String): Boolean

  suspend fun cancel()
}
