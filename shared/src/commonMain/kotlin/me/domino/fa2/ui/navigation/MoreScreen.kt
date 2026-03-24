package me.domino.fa2.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.domino.fa2.ui.component.SettingsAccountHeader
import me.domino.fa2.ui.component.SettingsGroup
import me.domino.fa2.ui.component.SettingsListItem

/** More 页面。 */
@Composable
fun MoreScreen(
  /** More 页面状态。 */
  state: MoreUiState,
  /** 打开当前用户页面。 */
  onOpenUser: () -> Unit,
  /** 打开设置页。 */
  onOpenSettings: () -> Unit,
  /** 打开投稿浏览记录。 */
  onOpenSubmissionHistory: () -> Unit,
  /** 打开搜索记录。 */
  onOpenSearchHistory: () -> Unit,
  /** 打开 About 页面。 */
  onOpenAbout: () -> Unit,
  /** 退出登录回调。 */
  onLogout: () -> Unit,
) {
  when (state) {
    MoreUiState.Loading -> {
      Text(
        text = "正在加载账号信息...",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
      )
    }

    is MoreUiState.Ready -> {
      MoreContent(
        state = state,
        onOpenUser = onOpenUser,
        onOpenSettings = onOpenSettings,
        onOpenSubmissionHistory = onOpenSubmissionHistory,
        onOpenSearchHistory = onOpenSearchHistory,
        onOpenAbout = onOpenAbout,
        onLogout = onLogout,
      )
    }
  }
}

/** More 页面内容。 */
@Composable
private fun MoreContent(
  /** 可展示状态。 */
  state: MoreUiState.Ready,
  /** 打开用户页回调。 */
  onOpenUser: () -> Unit,
  /** 打开设置页回调。 */
  onOpenSettings: () -> Unit,
  /** 打开投稿浏览记录。 */
  onOpenSubmissionHistory: () -> Unit,
  /** 打开搜索记录。 */
  onOpenSearchHistory: () -> Unit,
  /** 打开 About 页面。 */
  onOpenAbout: () -> Unit,
  /** 退出登录回调。 */
  onLogout: () -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      SettingsAccountHeader(
        title = state.username,
        subtitle = "账号中心",
        onClick = onOpenUser,
        modifier = Modifier.padding(top = 4.dp),
      )
    }
    item {
      SettingsGroup(titleHorizontalPadding = 0.dp, containerHorizontalPadding = 0.dp) {
        SettingsListItem(
          icon = Icons.Filled.History,
          title = "浏览记录",
          subtitle =
            if (state.submissionHistoryCount > 0) {
              "共 ${state.submissionHistoryCount} 条，点击查看"
            } else {
              "暂无记录"
            },
          onClick = onOpenSubmissionHistory,
        )
        SettingsListItem(
          icon = Icons.Filled.Search,
          title = "搜索记录",
          subtitle =
            if (state.searchHistoryCount > 0) {
              "共 ${state.searchHistoryCount} 条，点击查看"
            } else {
              "暂无记录"
            },
          onClick = onOpenSearchHistory,
        )
        SettingsListItem(
          icon = Icons.Filled.Settings,
          title = "设置",
          subtitle = "主题、瀑布流列宽与翻译设置",
          onClick = onOpenSettings,
        )
        SettingsListItem(
          icon = Icons.Filled.Info,
          title = "开源许可",
          subtitle = "查看本应用使用的开源库与许可证",
          onClick = onOpenAbout,
        )
        SettingsListItem(
          icon = Icons.AutoMirrored.Filled.Logout,
          title = "退出登录",
          subtitle =
            if (state.loggingOut) {
              "正在清除 Cookie..."
            } else {
              "清除 Cookie"
            },
          onClick = {
            if (!state.loggingOut) {
              onLogout()
            }
          },
          showDivider = false,
        )
      }
    }
    state.errorMessage
      ?.takeIf { value -> value.isNotBlank() }
      ?.let { message ->
        item {
          Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 0.dp),
          )
        }
      }
  }
}
