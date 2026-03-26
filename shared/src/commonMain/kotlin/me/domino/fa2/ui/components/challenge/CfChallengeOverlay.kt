package me.domino.fa2.ui.components.challenge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import kotlinx.coroutines.launch
import me.domino.fa2.application.challenge.CfChallengeController
import me.domino.fa2.application.challenge.CfChallengeStatus
import me.domino.fa2.application.challenge.CfChallengeUiState
import me.domino.fa2.i18n.challengeAwaitingUserActionText
import me.domino.fa2.i18n.challengeVerificationFailedText
import me.domino.fa2.ui.pages.auth.SessionWebView
import me.domino.fa2.ui.pages.auth.rememberSessionWebViewAdapter
import org.jetbrains.compose.resources.stringResource

/** 全局 challenge 覆盖层：作为“插入流程”覆盖当前页面。 */
@Composable
fun CfChallengeOverlay(
    state: CfChallengeUiState.Active,
    controller: CfChallengeController,
    modifier: Modifier = Modifier,
) {
  val adapter = rememberSessionWebViewAdapter(initialUrl = state.triggerUrl)
  val scope = rememberCoroutineScope()

  LaunchedEffect(state.triggerUrl) {
    controller.prepareWebViewSession(port = adapter.port, triggerUrl = state.triggerUrl)
  }

  LaunchedEffect(adapter.port.lastLoadedUrl) { controller.syncUserAgentFromWebView(adapter.port) }

  Surface(
      modifier = modifier.fillMaxSize().testTag("cf-challenge-status"),
      color = MaterialTheme.colorScheme.surface,
  ) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(onClick = { adapter.port.loadUrl(state.triggerUrl) }) {
          Text(stringResource(Res.string.reload))
        }
        Button(onClick = { scope.launch { controller.cancel() } }) {
          Text(stringResource(Res.string.cancel))
        }
        Button(
            enabled = state.status !is CfChallengeStatus.Verifying,
            onClick = {
              scope.launch {
                controller.confirmFromWebView(
                    port = adapter.port,
                    triggerUrl = state.triggerUrl,
                )
              }
            },
        ) {
          Text(
              if (state.status is CfChallengeStatus.Verifying) {
                stringResource(Res.string.challenge_verifying)
              } else {
                stringResource(Res.string.cloudflare_challenge_done)
              }
          )
        }
      }

      Text(
          text = challengeStatusMessage(state),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(horizontal = 12.dp),
      )

      SessionWebView(
          adapter = adapter,
          modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(bottom = 12.dp),
      )
    }
  }
}

@Composable
private fun challengeStatusMessage(state: CfChallengeUiState.Active): String =
    when (val status = state.status) {
      CfChallengeStatus.AwaitingUserAction -> challengeAwaitingUserActionText(state.cfRay)
      CfChallengeStatus.Verifying -> stringResource(Res.string.challenge_validating_result)
      is CfChallengeStatus.VerificationFailed -> challengeVerificationFailedText(status.detail)
    }
