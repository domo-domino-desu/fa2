package me.domino.fa2.ui.layouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.domino.fa2.ui.icons.FaMaterialSymbols

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteTopBar(
    title: String,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
  TopAppBar(
      title = {
        if (title.isBlank()) {
          if (onTitleClick != null) {
            IconButton(onClick = onTitleClick) {
              Icon(
                  imageVector = FaMaterialSymbols.Outlined.VerticalAlignTop,
                  contentDescription = "回到顶部",
              )
            }
          } else {
            Text(text = "")
          }
        } else {
          Text(
              text = title,
              style = MaterialTheme.typography.titleLarge,
              modifier =
                  if (onTitleClick != null) {
                    Modifier.clickable(onClick = onTitleClick)
                  } else {
                    Modifier
                  },
          )
        }
      },
      navigationIcon = { BackHomeTopBarNavigation(onBack = onBack, onGoHome = onGoHome) },
      actions = actions,
      colors =
          TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surface,
              scrolledContainerColor = MaterialTheme.colorScheme.surface,
          ),
  )
}

@Composable
fun AboutRouteTopBar(onBack: () -> Unit, onGoHome: () -> Unit, onTitleClick: (() -> Unit)? = null) {
  RouteTopBar(title = "开源许可", onBack = onBack, onGoHome = onGoHome, onTitleClick = onTitleClick)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsRouteTopBar(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    showActions: Boolean,
    saving: Boolean,
    hasUnsavedChanges: Boolean,
    validationMessage: String?,
    onResetDraft: () -> Unit,
    onSaveDraft: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
) {
  RouteTopBar(title = "设置", onBack = onBack, onGoHome = onGoHome, onTitleClick = onTitleClick) {
    if (showActions) {
      IconButton(onClick = onResetDraft, enabled = !saving && hasUnsavedChanges) {
        Icon(imageVector = FaMaterialSymbols.Filled.RestartAlt, contentDescription = "回滚到已保存配置")
      }
      IconButton(
          onClick = onSaveDraft,
          enabled = !saving && validationMessage == null && hasUnsavedChanges,
      ) {
        if (saving) {
          LoadingIndicator(
              modifier = Modifier.padding(6.dp).size(24.dp),
              color = MaterialTheme.colorScheme.primary,
          )
        } else {
          Icon(imageVector = FaMaterialSymbols.Filled.Save, contentDescription = "保存设置")
        }
      }
    }
  }
}

@Composable
fun SearchHistoryRouteTopBar(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
) {
  RouteTopBar(title = "搜索记录", onBack = onBack, onGoHome = onGoHome, onTitleClick = onTitleClick)
}

@Composable
fun SubmissionHistoryRouteTopBar(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
) {
  RouteTopBar(title = "浏览记录", onBack = onBack, onGoHome = onGoHome, onTitleClick = onTitleClick)
}

@Composable
fun BrowseRouteTopBar(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
) {
  RouteTopBar(title = "Browse", onBack = onBack, onGoHome = onGoHome, onTitleClick = onTitleClick)
}

@Composable
fun SearchRouteTopBar(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
) {
  RouteTopBar(title = "Search", onBack = onBack, onGoHome = onGoHome, onTitleClick = onTitleClick)
}

@Composable
fun JournalDetailRouteTopBar(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    shareUrl: String,
    onTitleClick: (() -> Unit)? = null,
) {
  RouteTopBar(
      title = "Journal",
      onBack = onBack,
      onGoHome = onGoHome,
      onTitleClick = onTitleClick,
  ) {
    TopBarShareAction(url = shareUrl)
  }
}

@Composable
fun UserRouteTopBar(
    title: String,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    shareUrl: String,
    onTitleClick: (() -> Unit)? = null,
) {
  RouteTopBar(title = title, onBack = onBack, onGoHome = onGoHome, onTitleClick = onTitleClick) {
    TopBarShareAction(url = shareUrl)
  }
}

@Composable
fun UserWatchlistRouteTopBar(
    title: String,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    shareUrl: String,
    onTitleClick: (() -> Unit)? = null,
) {
  RouteTopBar(title = title, onBack = onBack, onGoHome = onGoHome, onTitleClick = onTitleClick) {
    TopBarShareAction(url = shareUrl)
  }
}

@Composable
fun SubmissionRouteTopBar(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    shareUrl: String,
    downloadUrl: String?,
    onDownload: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
) {
  RouteTopBar(title = "", onBack = onBack, onGoHome = onGoHome, onTitleClick = onTitleClick) {
    if (!downloadUrl.isNullOrBlank()) {
      IconButton(onClick = onDownload) {
        Icon(imageVector = FaMaterialSymbols.Filled.Download, contentDescription = "下载")
      }
    }
    TopBarShareAction(url = shareUrl)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseFilterOverlayTopBar(
    onClose: () -> Unit,
    onApply: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
) {
  TopAppBar(
      title = {
        Text(
            text = "浏览筛选",
            style = MaterialTheme.typography.titleMedium,
            modifier =
                if (onTitleClick != null) {
                  Modifier.clickable(onClick = onTitleClick)
                } else {
                  Modifier
                },
        )
      },
      navigationIcon = {
        IconButton(onClick = onClose) {
          Icon(
              imageVector = FaMaterialSymbols.Filled.Close,
              contentDescription = "关闭筛选页面",
          )
        }
      },
      actions = {
        IconButton(onClick = onApply) {
          Icon(imageVector = FaMaterialSymbols.Filled.Done, contentDescription = "应用筛选")
        }
      },
      colors =
          TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surface,
              scrolledContainerColor = MaterialTheme.colorScheme.surface,
          ),
      windowInsets = WindowInsets(0, 0, 0, 0),
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchOverlayTopBar(
    onClose: () -> Unit,
    onApplySearch: () -> Unit,
    canSearch: Boolean,
    onTitleClick: (() -> Unit)? = null,
) {
  TopAppBar(
      title = {
        Text(
            text = "搜索筛选",
            style = MaterialTheme.typography.titleMedium,
            modifier =
                if (onTitleClick != null) {
                  Modifier.clickable(onClick = onTitleClick)
                } else {
                  Modifier
                },
        )
      },
      navigationIcon = {
        IconButton(onClick = onClose) {
          Icon(
              imageVector = FaMaterialSymbols.Filled.Close,
              contentDescription = "关闭搜索遮罩",
          )
        }
      },
      actions = {
        IconButton(onClick = onApplySearch, enabled = canSearch) {
          Icon(imageVector = FaMaterialSymbols.Filled.Search, contentDescription = "执行搜索")
        }
      },
      colors =
          TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surface,
              scrolledContainerColor = MaterialTheme.colorScheme.surface,
          ),
      windowInsets = WindowInsets(0, 0, 0, 0),
  )
}
