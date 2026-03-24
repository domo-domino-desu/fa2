package me.domino.fa2.ui.pages.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** 登录页主界面。 */
@Composable
fun AuthScreen(
  /** 当前登录页面状态。 */
  state: AuthUiState.AuthInvalid,
  /** 当前 Cookie 输入值。 */
  cookieDraft: String,
  /** 输入变更回调。 */
  onCookieDraftChange: (String) -> Unit,
  /** 提交回调。 */
  onSubmit: () -> Unit,
  /** 重试探测回调。 */
  onRetry: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(text = "登录到 FurAffinity", style = MaterialTheme.typography.headlineSmall)
    Text(state.message)
    OutlinedTextField(
      value = cookieDraft,
      onValueChange = onCookieDraftChange,
      label = { Text("Cookie Header") },
      placeholder = { Text("a=...; b=...") },
      modifier = Modifier.fillMaxWidth(),
      minLines = 6,
      textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    )
    Button(onClick = onSubmit) { Text("保存并登录") }
    Button(onClick = onRetry) { Text("重试现有登录态") }
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
      LoadingIndicator(modifier = Modifier.size(56.dp), color = MaterialTheme.colorScheme.primary)
      Text(
        text = "正在检查登录态…",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
