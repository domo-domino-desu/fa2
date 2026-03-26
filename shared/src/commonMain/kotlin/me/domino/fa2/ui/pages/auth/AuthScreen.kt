package me.domino.fa2.ui.pages.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** 登录页主界面。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    /** 当前登录页面状态。 */
    state: AuthUiState.AuthInvalid,
    /** 当前选中的登录方式。 */
    loginMethod: AuthLoginMethod,
    /** WebView 交互状态。 */
    webViewUiState: AuthWebViewUiState,
    /** WebView 适配器。 */
    webViewAdapter: SessionWebViewAdapter,
    /** 当前 Cookie 输入值。 */
    cookieDraft: String,
    /** 登录方式切换回调。 */
    onLoginMethodChange: (AuthLoginMethod) -> Unit,
    /** 输入变更回调。 */
    onCookieDraftChange: (String) -> Unit,
    /** Cookie 提交回调。 */
    onSubmitCookie: () -> Unit,
    /** 重试探测回调。 */
    onRetry: () -> Unit,
    /** WebView 重载回调。 */
    onReloadWebView: () -> Unit,
    /** WebView 完成登录回调。 */
    onConfirmWebViewLogin: () -> Unit,
) {
  Column(
      modifier = Modifier.fillMaxSize().padding(20.dp).testTag("auth-screen"),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(text = "登录到 FurAffinity", style = MaterialTheme.typography.headlineSmall)
    Text(text = state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)

    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        val loginMethods = AuthLoginMethod.entries
        loginMethods.forEachIndexed { index, method ->
          SegmentedButton(
              selected = loginMethod == method,
              onClick = { onLoginMethodChange(method) },
              shape = SegmentedButtonDefaults.itemShape(index = index, count = loginMethods.size),
              modifier =
                  Modifier.testTag(
                      when (method) {
                        AuthLoginMethod.WebView -> "auth-tab-webview"
                        AuthLoginMethod.Cookie -> "auth-tab-cookie"
                      }
                  ),
          ) {
            Text(
                when (method) {
                  AuthLoginMethod.WebView -> "WebView"
                  AuthLoginMethod.Cookie -> "Cookie"
                }
            )
          }
        }
      }
    }

    when (loginMethod) {
      AuthLoginMethod.WebView -> {
        WebViewLoginTab(
            webViewUiState = webViewUiState,
            adapter = webViewAdapter,
            onReload = onReloadWebView,
            onConfirm = onConfirmWebViewLogin,
        )
      }

      AuthLoginMethod.Cookie -> {
        CookieLoginTab(
            cookieDraft = cookieDraft,
            onCookieDraftChange = onCookieDraftChange,
            onSubmit = onSubmitCookie,
            onRetry = onRetry,
        )
      }
    }
  }
}

/** WebView 登录内容。 */
@Composable
private fun WebViewLoginTab(
    webViewUiState: AuthWebViewUiState,
    adapter: SessionWebViewAdapter,
    onReload: () -> Unit,
    onConfirm: () -> Unit,
) {
  Column(
      modifier = Modifier.fillMaxSize().testTag("auth-webview-panel"),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = onReload) { Text("重载登录页") }
      Button(enabled = !webViewUiState.isConfirming, onClick = onConfirm) {
        Text(if (webViewUiState.isConfirming) "确认中..." else "完成登录")
      }
    }
    Text(
        text = webViewUiState.statusMessage,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SessionWebView(adapter = adapter, modifier = Modifier.fillMaxWidth().weight(1f))
  }
}

/** Cookie 登录内容。 */
@Composable
private fun CookieLoginTab(
    cookieDraft: String,
    onCookieDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
        text = "直接粘贴浏览器中的 Cookie Header。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = cookieDraft,
        onValueChange = onCookieDraftChange,
        label = { Text("Cookie Header") },
        placeholder = { Text("a=...; b=...") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 6,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = onSubmit) { Text("保存并登录") }
      Button(onClick = onRetry) { Text("重试现有登录态") }
    }
  }
}

/** 登录页加载态。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuthLoadingScreen() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      LoadingIndicator(
          modifier = Modifier.size(56.dp),
          color = MaterialTheme.colorScheme.primary,
      )
      Text(
          text = "正在检查登录态…",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
