package me.domino.fa2.data.fa.session

import kotlinx.coroutines.flow.StateFlow

interface CfChallengeController {
  val state: StateFlow<CfChallengeUiState>

  suspend fun prepareWebViewSession(port: SessionWebViewPort, triggerUrl: String)

  suspend fun syncUserAgentFromWebView(port: SessionWebViewPort)

  suspend fun confirmFromWebView(port: SessionWebViewPort, triggerUrl: String): Boolean

  suspend fun cancel()
}
