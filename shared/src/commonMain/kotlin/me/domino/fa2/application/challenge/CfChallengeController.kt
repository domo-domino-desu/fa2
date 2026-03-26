package me.domino.fa2.application.challenge

import kotlinx.coroutines.flow.StateFlow
import me.domino.fa2.application.challenge.port.SessionWebViewPort

interface CfChallengeController {
  val state: StateFlow<CfChallengeUiState>

  suspend fun prepareWebViewSession(port: SessionWebViewPort, triggerUrl: String)

  suspend fun syncUserAgentFromWebView(port: SessionWebViewPort)

  suspend fun confirmFromWebView(port: SessionWebViewPort, triggerUrl: String): Boolean

  suspend fun cancel()
}
