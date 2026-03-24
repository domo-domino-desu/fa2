package me.domino.fa2.app.challenge

import kotlinx.coroutines.flow.StateFlow
import me.domino.fa2.ui.pages.auth.CfChallengeWebViewPort

interface CfChallengeController {
  val state: StateFlow<CfChallengeUiState>

  suspend fun prepareWebViewSession(port: CfChallengeWebViewPort, triggerUrl: String)

  suspend fun syncUserAgentFromWebView(port: CfChallengeWebViewPort)

  suspend fun confirmFromWebView(port: CfChallengeWebViewPort, triggerUrl: String): Boolean

  suspend fun cancel()
}
