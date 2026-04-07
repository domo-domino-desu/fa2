package me.domino.fa2.ui.pages.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import me.domino.fa2.ui.components.ExpressiveButton
import me.domino.fa2.ui.components.ExpressiveIconButton
import me.domino.fa2.ui.components.ExpressiveTextButton
import me.domino.fa2.ui.components.settings.SettingsAccountHeader
import me.domino.fa2.ui.components.settings.SettingsGroup
import me.domino.fa2.ui.components.settings.SettingsListItem
import me.domino.fa2.ui.icons.FaMaterialSymbols
import org.jetbrains.compose.resources.stringResource

/** More 页面。 */
@Composable
fun MoreScreen(
    /** More 页面状态。 */
    state: MoreUiState,
    /** 打开当前用户页面。 */
    onOpenUser: (() -> Unit)?,
    /** 打开当前用户已关注列表。 */
    onOpenFollowing: (() -> Unit)?,
    /** 打开关注推荐页面。 */
    onOpenWatchRecommendations: (() -> Unit)?,
    /** 打开当前用户收藏列表。 */
    onOpenFavorites: (() -> Unit)?,
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
          text = stringResource(Res.string.loading_account_info),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
      )
    }

    is MoreUiState.Ready -> {
      MoreContent(
          state = state,
          onOpenUser = onOpenUser,
          onOpenFollowing = onOpenFollowing,
          onOpenWatchRecommendations = onOpenWatchRecommendations,
          onOpenFavorites = onOpenFavorites,
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
    onOpenUser: (() -> Unit)?,
    /** 打开当前用户已关注列表。 */
    onOpenFollowing: (() -> Unit)?,
    /** 打开关注推荐页。 */
    onOpenWatchRecommendations: (() -> Unit)?,
    /** 打开当前用户收藏列表。 */
    onOpenFavorites: (() -> Unit)?,
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
  var logoutDialogVisible by remember { mutableStateOf(false) }
  LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("more-screen"),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      SettingsAccountHeader(
          title = state.username ?: stringResource(Res.string.account_center),
          subtitle = stringResource(Res.string.account_center),
          onClick = { onOpenUser?.invoke() },
          enabled = onOpenUser != null,
          modifier = Modifier.padding(top = 4.dp),
      )
    }
    item {
      SettingsGroup(titleHorizontalPadding = 0.dp, containerHorizontalPadding = 0.dp) {
        SettingsListItem(
            icon = FaMaterialSymbols.Filled.Notifications,
            title = stringResource(Res.string.following),
            subtitle = stringResource(Res.string.following_more_summary),
            onClick = { onOpenFollowing?.invoke() },
            enabled = onOpenFollowing != null,
            modifier = Modifier.testTag("more-following"),
        )
        SettingsListItem(
            icon = FaMaterialSymbols.Outlined.Troubleshoot,
            title = stringResource(Res.string.following_recommendation),
            subtitle = stringResource(Res.string.following_recommendation_summary),
            onClick = { onOpenWatchRecommendations?.invoke() },
            enabled = onOpenWatchRecommendations != null,
            modifier = Modifier.testTag("more-following-recommendation"),
        )
        SettingsListItem(
            icon = FaMaterialSymbols.Filled.Favorite,
            title = stringResource(Res.string.favorites),
            subtitle = stringResource(Res.string.favorite_more_summary),
            onClick = { onOpenFavorites?.invoke() },
            enabled = onOpenFavorites != null,
            modifier = Modifier.testTag("more-favorites"),
        )
        SettingsListItem(
            icon = FaMaterialSymbols.Filled.History,
            title = stringResource(Res.string.submission_history),
            subtitle =
                if (state.submissionHistoryCount > 0) {
                  stringResource(Res.string.record_count, state.submissionHistoryCount)
                } else {
                  stringResource(Res.string.no_records)
                },
            onClick = onOpenSubmissionHistory,
        )
        SettingsListItem(
            icon = FaMaterialSymbols.Filled.Search,
            title = stringResource(Res.string.search_history),
            subtitle =
                if (state.searchHistoryCount > 0) {
                  stringResource(Res.string.record_count, state.searchHistoryCount)
                } else {
                  stringResource(Res.string.no_records)
                },
            onClick = onOpenSearchHistory,
        )
        SettingsListItem(
            icon = FaMaterialSymbols.Filled.Settings,
            title = stringResource(Res.string.settings),
            subtitle = stringResource(Res.string.settings_summary),
            onClick = onOpenSettings,
        )
        SettingsListItem(
            icon = FaMaterialSymbols.Filled.Info,
            title = stringResource(Res.string.about),
            subtitle = stringResource(Res.string.about_summary),
            onClick = onOpenAbout,
        )
        SettingsListItem(
            icon = FaMaterialSymbols.AutoMirrored.Filled.Logout,
            title = stringResource(Res.string.logout),
            subtitle =
                if (state.loggingOut) {
                  stringResource(Res.string.clearing_cookie)
                } else {
                  stringResource(Res.string.clear_cookie)
                },
            onClick = {
              if (!state.loggingOut) {
                logoutDialogVisible = true
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

  if (logoutDialogVisible) {
    AlertDialog(
        onDismissRequest = { logoutDialogVisible = false },
        title = {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(stringResource(Res.string.logout_confirm_title))
            ExpressiveIconButton(
                onClick = { logoutDialogVisible = false },
                enabled = !state.loggingOut,
            ) {
              Icon(
                  imageVector = FaMaterialSymbols.Filled.Close,
                  contentDescription = stringResource(Res.string.close),
              )
            }
          }
        },
        text = { Text(stringResource(Res.string.logout_confirm_body)) },
        confirmButton = {
          ExpressiveButton(
              onClick = {
                logoutDialogVisible = false
                onLogout()
              },
              enabled = !state.loggingOut,
          ) {
            Text(stringResource(Res.string.logout_confirm_action))
          }
        },
        dismissButton = {
          ExpressiveTextButton(
              onClick = { logoutDialogVisible = false },
              enabled = !state.loggingOut,
          ) {
            Text(stringResource(Res.string.cancel))
          }
        },
    )
  }
}
