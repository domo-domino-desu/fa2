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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.domino.fa2.app.challenge.CfChallengeController
import me.domino.fa2.app.challenge.CfChallengeStatus
import me.domino.fa2.app.challenge.CfChallengeUiState
import me.domino.fa2.ui.pages.auth.CfChallengeWebView
import me.domino.fa2.ui.pages.auth.rememberChallengeWebViewAdapter

/** 全局 challenge 覆盖层：作为“插入流程”覆盖当前页面。 */
@Composable
fun CfChallengeOverlay(
  state: CfChallengeUiState.Active,
  controller: CfChallengeController,
  modifier: Modifier = Modifier,
) {
  val adapter = rememberChallengeWebViewAdapter(initialUrl = state.triggerUrl)
  val scope = rememberCoroutineScope()

  LaunchedEffect(state.triggerUrl) {
    controller.prepareWebViewSession(port = adapter.port, triggerUrl = state.triggerUrl)
  }

  LaunchedEffect(adapter.port.lastLoadedUrl) { controller.syncUserAgentFromWebView(adapter.port) }

  Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(onClick = { adapter.port.loadUrl(state.triggerUrl) }) { Text("重载") }
        Button(onClick = { scope.launch { controller.cancel() } }) { Text("取消") }
        Button(
          enabled = state.status !is CfChallengeStatus.Verifying,
          onClick = {
            scope.launch {
              controller.confirmFromWebView(port = adapter.port, triggerUrl = state.triggerUrl)
            }
          },
        ) {
          Text(if (state.status is CfChallengeStatus.Verifying) "验证中..." else "我已完成验证")
        }
      }

      Text(
        text = challengeStatusMessage(state),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 12.dp),
      )

      CfChallengeWebView(
        adapter = adapter,
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(bottom = 12.dp),
      )
    }
  }
}

private fun challengeStatusMessage(state: CfChallengeUiState.Active): String =
  when (val status = state.status) {
    CfChallengeStatus.AwaitingUserAction -> {
      val rayText = state.cfRay?.let { value -> "\nCF-Ray: $value" }.orEmpty()
      "请在 WebView 中完成 Cloudflare 验证后点击“我已完成验证”。$rayText"
    }

    CfChallengeStatus.Verifying -> "正在校验 challenge 结果..."
    is CfChallengeStatus.VerificationFailed -> "验证失败：${status.detail}"
  }
