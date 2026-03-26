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
import fa2.shared.generated.resources.*
import me.domino.fa2.ui.icons.FaMaterialSymbols
import org.jetbrains.compose.resources.stringResource

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
                  contentDescription = stringResource(Res.string.scroll_to_top),
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
  RouteTopBar(
      title = stringResource(Res.string.about),
      onBack = onBack,
      onGoHome = onGoHome,
      onTitleClick = onTitleClick,
  )
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
  RouteTopBar(
      title = stringResource(Res.string.settings),
      onBack = onBack,
      onGoHome = onGoHome,
      onTitleClick = onTitleClick,
  ) {
    if (showActions) {
      IconButton(onClick = onResetDraft, enabled = !saving && hasUnsavedChanges) {
        Icon(
            imageVector = FaMaterialSymbols.Filled.RestartAlt,
            contentDescription = stringResource(Res.string.rollback_saved_settings),
        )
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
          Icon(
              imageVector = FaMaterialSymbols.Filled.Save,
              contentDescription = stringResource(Res.string.save_settings),
          )
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
  RouteTopBar(
      title = stringResource(Res.string.search_history),
      onBack = onBack,
      onGoHome = onGoHome,
      onTitleClick = onTitleClick,
  )
}

@Composable
fun SubmissionHistoryRouteTopBar(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
) {
  RouteTopBar(
      title = stringResource(Res.string.submission_history),
      onBack = onBack,
      onGoHome = onGoHome,
      onTitleClick = onTitleClick,
  )
}

@Composable
fun BrowseRouteTopBar(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
) {
  RouteTopBar(
      title = stringResource(Res.string.browse),
      onBack = onBack,
      onGoHome = onGoHome,
      onTitleClick = onTitleClick,
  )
}

@Composable
fun SearchRouteTopBar(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
) {
  RouteTopBar(
      title = stringResource(Res.string.search),
      onBack = onBack,
      onGoHome = onGoHome,
      onTitleClick = onTitleClick,
  )
}

@Composable
fun JournalDetailRouteTopBar(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    shareUrl: String,
    onTitleClick: (() -> Unit)? = null,
) {
  RouteTopBar(
      title = stringResource(Res.string.journal),
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
        Icon(
            imageVector = FaMaterialSymbols.Filled.Download,
            contentDescription = stringResource(Res.string.download),
        )
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
            text = stringResource(Res.string.browse_filters),
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
              contentDescription = stringResource(Res.string.close_filters_page),
          )
        }
      },
      actions = {
        IconButton(onClick = onApply) {
          Icon(
              imageVector = FaMaterialSymbols.Filled.Done,
              contentDescription = stringResource(Res.string.apply_filters),
          )
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
            text = stringResource(Res.string.search_filters),
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
              contentDescription = stringResource(Res.string.close_search_overlay),
          )
        }
      },
      actions = {
        IconButton(onClick = onApplySearch, enabled = canSearch) {
          Icon(
              imageVector = FaMaterialSymbols.Filled.Search,
              contentDescription = stringResource(Res.string.run_search),
          )
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
