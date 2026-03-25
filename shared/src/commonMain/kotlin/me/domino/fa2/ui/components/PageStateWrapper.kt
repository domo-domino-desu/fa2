package me.domino.fa2.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.domino.fa2.data.model.PageState

/** 统一渲染 `PageState` 的基础组件。 */
@Composable
fun <T> PageStateWrapper(
    /** 当前页面状态。 */
    state: PageState<T>,
    /** 重试回调。 */
    onRetry: () -> Unit,
    /** 页面内容渲染器。 */
    content: @Composable () -> Unit,
) {
  when (state) {
    PageState.Loading -> {
      content()
    }

    PageState.CfChallenge -> {
      StatusCard(
          title = "需要 Cloudflare 验证",
          body = "请先完成挑战并更新 cookie（含 cf_clearance）。",
          onRetry = onRetry,
          modifier = Modifier.testTag("cf-challenge-status"),
      )
    }

    is PageState.MatureBlocked -> {
      StatusCard(title = "页面被拦截", body = state.reason, onRetry = onRetry)
    }

    is PageState.Error -> {
      StatusCard(
          title = "加载失败",
          body = state.exception.message ?: state.exception::class.simpleName ?: "未知错误",
          onRetry = onRetry,
      )
    }

    is PageState.Success -> {
      content()
    }
  }
}

/** 状态消息卡片。 */
@Composable
private fun StatusCard(
    /** 标题。 */
    title: String,
    /** 正文。 */
    body: String,
    /** 重试回调。 */
    onRetry: (() -> Unit)?,
    /** 组件修饰符。 */
    modifier: Modifier = Modifier,
) {
  Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(14.dp),
      border =
          BorderStroke(
              width = 1.dp,
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
          ),
      modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
  ) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(text = title, style = MaterialTheme.typography.titleMedium)
      Text(
          text = body,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (onRetry != null) {
        Button(onClick = onRetry) { Text("重试") }
      }
    }
  }
}
