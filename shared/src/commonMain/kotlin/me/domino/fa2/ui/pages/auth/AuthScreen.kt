package me.domino.fa2.ui.pages.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import me.domino.fa2.ui.components.ExpressiveButton
import me.domino.fa2.ui.components.ExpressiveFilledTonalButton
import org.jetbrains.compose.resources.stringResource

/** 登录页主界面。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val webViewLabel = stringResource(Res.string.web_view)
    val cookieLabel = stringResource(Res.string.cookie)
    Text(
        text = stringResource(Res.string.sign_in_to_fur_affinity),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(text = state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)

    ButtonGroup(
        overflowIndicator = { menuState ->
          ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
      AuthLoginMethod.entries.forEach { method ->
        val selected = loginMethod == method
        val label = if (method == AuthLoginMethod.WebView) webViewLabel else cookieLabel
        toggleableItem(
            checked = selected,
            onCheckedChange = { checked ->
              if (checked && !selected) {
                onLoginMethodChange(method)
              }
            },
            label = label,
            weight = 1f,
        )
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
      ExpressiveFilledTonalButton(onClick = onReload) {
        Text(stringResource(Res.string.reload_login_page))
      }
      ExpressiveButton(enabled = !webViewUiState.isConfirming, onClick = onConfirm) {
        Text(
            if (webViewUiState.isConfirming) stringResource(Res.string.confirming_login)
            else stringResource(Res.string.complete_login)
        )
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
        text = stringResource(Res.string.paste_cookie_header_directly),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = cookieDraft,
        onValueChange = onCookieDraftChange,
        label = { Text(stringResource(Res.string.cookie_header)) },
        placeholder = { Text(stringResource(Res.string.cookie_header_placeholder)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 6,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ExpressiveButton(onClick = onSubmit) { Text(stringResource(Res.string.save_and_login)) }
      ExpressiveFilledTonalButton(onClick = onRetry) {
        Text(stringResource(Res.string.retry_existing_session))
      }
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
          text = stringResource(Res.string.checking_login_state),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** 登录态探测失败页面。 */
@Composable
fun AuthProbeFailedScreen(
    state: AuthUiState.ProbeFailed,
    onRetry: () -> Unit,
) {
  Box(
      modifier = Modifier.fillMaxSize().padding(20.dp).testTag("auth-probe-failed-screen"),
      contentAlignment = Alignment.Center,
  ) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
            text = stringResource(Res.string.auth_probe_failed_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(Res.string.auth_probe_failed_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ExpressiveButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
      }
    }
  }
}
