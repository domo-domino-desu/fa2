package me.domino.fa2.ui.pages.user

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import me.domino.fa2.ui.components.html.HtmlText
import me.domino.fa2.ui.components.state.SkeletonBlock
import me.domino.fa2.ui.components.state.StatusSurface
import me.domino.fa2.ui.components.state.StatusSurfaceVariant
import me.domino.fa2.ui.components.user.UserHeaderSummaryCard
import me.domino.fa2.ui.icons.FaMaterialSymbols
import org.jetbrains.compose.resources.stringResource

private const val collapsedProfilePreviewMaxLines = 5

@Composable
internal fun UserHeaderCard(
    state: UserUiState,
    onRetry: () -> Unit,
    onToggleProfileExpanded: () -> Unit,
    onToggleWatch: () -> Unit,
    onHideFromRecommendations: () -> Unit,
    onUnhideFromRecommendations: () -> Unit,
    navigationActions: UserHeaderNavigationActions,
) {
  val header = state.header
  val isPureSkeleton = state.loading && header == null
  if (isPureSkeleton) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
      Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          SkeletonBlock(
              modifier = Modifier.fillMaxWidth().height(126.dp),
              shape = RoundedCornerShape(0.dp),
          )
          Column(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              SkeletonBlock(modifier = Modifier.size(54.dp), shape = CircleShape)
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBlock(modifier = Modifier.width(160.dp).height(22.dp))
                SkeletonBlock(modifier = Modifier.width(120.dp).height(14.dp))
              }
            }
            SkeletonBlock(modifier = Modifier.width(240.dp).height(14.dp))
            repeat(3) {
              SkeletonBlock(
                  modifier = Modifier.fillMaxWidth(if (it == 2) 0.8f else 1f).height(13.dp)
              )
            }
          }
        }
      }
    }
    return
  }

  if (header == null) {
    StatusSurface(
        title = stringResource(Res.string.load_failed),
        body = state.errorMessage ?: stringResource(Res.string.load_failed),
        onAction = onRetry,
        variant = StatusSurfaceVariant.Inline,
    )
    return
  }

  val hasWatchAction = header.watchActionUrl.isNotBlank()
  UserHeaderSummaryCard(
      user = header,
      fallbackUsername = state.username,
      onOpenShouts = navigationActions.onOpenShouts,
      onOpenWatchedBy = navigationActions.onOpenWatchedBy,
      onOpenWatching = navigationActions.onOpenWatching,
      trailingContent = {
        if (hasWatchAction || state.recommendationHidden) {
          UserWatchActionButton(
              isWatching = header.isWatching,
              recommendationHidden = state.recommendationHidden,
              updating = state.watchUpdating || state.recommendationHideUpdating,
              watchActionAvailable = hasWatchAction,
              onToggleWatch = onToggleWatch,
              onHideFromRecommendations = onHideFromRecommendations,
              onUnhideFromRecommendations = onUnhideFromRecommendations,
          )
        }
      },
      bodyContent = {
        UserMetadataAndContactsRow(
            userTitle = header.userTitle,
            registeredAtText = me.domino.fa2.ui.i18n.registeredAtText(header.registeredAt),
            contacts = header.contacts,
        )

        if (header.profileHtml.isNotBlank()) {
          var shouldCollapse by remember(header.profileHtml) { mutableStateOf(false) }
          HtmlText(
              html = header.profileHtml,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines =
                  if (state.profileExpanded) Int.MAX_VALUE else collapsedProfilePreviewMaxLines,
              onTextLayout = { layoutResult ->
                val nextShouldCollapse =
                    if (state.profileExpanded) {
                      layoutResult.lineCount > collapsedProfilePreviewMaxLines
                    } else {
                      layoutResult.hasVisualOverflow
                    }
                if (shouldCollapse != nextShouldCollapse) {
                  shouldCollapse = nextShouldCollapse
                }
              },
          )
          if (shouldCollapse) {
            UserProfileExpandToggle(
                expanded = state.profileExpanded,
                onClick = onToggleProfileExpanded,
            )
          }
        }

        if (!state.errorMessage.isNullOrBlank()) {
          StatusSurface(
              title = stringResource(Res.string.load_failed),
              body = state.errorMessage,
              variant = StatusSurfaceVariant.Inline,
          )
        }
      },
  )
}

@Composable
private fun UserProfileExpandToggle(expanded: Boolean, onClick: () -> Unit) {
  val contentDescription =
      if (expanded) {
        stringResource(Res.string.collapse_profile)
      } else {
        stringResource(Res.string.expand_profile)
      }
  val stateDescription =
      if (expanded) {
        stringResource(Res.string.accessibility_expanded)
      } else {
        stringResource(Res.string.accessibility_collapsed)
      }
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .padding(top = 4.dp, bottom = 1.dp)
              .clickable(onClick = onClick)
              .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = stateDescription
              },
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        imageVector =
            if (expanded) {
              FaMaterialSymbols.Outlined.ExpandLess
            } else {
              FaMaterialSymbols.Outlined.ExpandMore
            },
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
  }
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
private fun UserWatchActionButton(
    isWatching: Boolean,
    recommendationHidden: Boolean,
    updating: Boolean,
    watchActionAvailable: Boolean,
    onToggleWatch: () -> Unit,
    onHideFromRecommendations: () -> Unit,
    onUnhideFromRecommendations: () -> Unit,
) {
  val action =
      remember(isWatching, recommendationHidden, updating, watchActionAvailable) {
        resolveUserWatchButtonAction(
            isWatching = isWatching,
            recommendationHidden = recommendationHidden,
            updating = updating,
            watchActionAvailable = watchActionAvailable,
        )
      }
  val contentDescription = stringResource(action.contentDescription)
  val stateDescription =
      when {
        updating -> stringResource(Res.string.processing)
        isWatching -> stringResource(Res.string.accessibility_watch_state_watching)
        else -> stringResource(Res.string.accessibility_watch_state_not_watching)
      }
  Surface(
      modifier =
          Modifier.size(52.dp)
              .combinedClickable(
                  enabled = action.enabled,
                  onClick = {
                    when (action.clickAction) {
                      UserWatchButtonClickAction.ToggleWatch -> onToggleWatch()
                      UserWatchButtonClickAction.UnhideRecommendation ->
                          onUnhideFromRecommendations()
                      UserWatchButtonClickAction.None -> Unit
                    }
                  },
                  onLongClick =
                      if (action.longClickHidesRecommendation) {
                        onHideFromRecommendations
                      } else {
                        null
                      },
              )
              .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = stateDescription
              },
      shape = RoundedCornerShape(16.dp),
      color =
          if (isWatching || recommendationHidden) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.surfaceVariant
          },
  ) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      if (updating) {
        LoadingIndicator(
            modifier = Modifier.size(22.dp),
            color = MaterialTheme.colorScheme.primary,
        )
      } else {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
        )
      }
    }
  }
}

internal enum class UserWatchButtonClickAction {
  ToggleWatch,
  UnhideRecommendation,
  None,
}

internal data class UserWatchButtonAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val contentDescription: org.jetbrains.compose.resources.StringResource,
    val clickAction: UserWatchButtonClickAction,
    val longClickHidesRecommendation: Boolean,
    val enabled: Boolean,
)

internal fun resolveUserWatchButtonAction(
    isWatching: Boolean,
    recommendationHidden: Boolean,
    updating: Boolean,
    watchActionAvailable: Boolean,
): UserWatchButtonAction {
  if (updating) {
    return UserWatchButtonAction(
        icon =
            if (isWatching || recommendationHidden) {
              FaMaterialSymbols.Filled.Notifications
            } else {
              FaMaterialSymbols.Outlined.Notifications
            },
        contentDescription = Res.string.processing,
        clickAction = UserWatchButtonClickAction.None,
        longClickHidesRecommendation = false,
        enabled = false,
    )
  }
  return when {
    isWatching ->
        UserWatchButtonAction(
            icon = FaMaterialSymbols.Filled.Notifications,
            contentDescription = Res.string.unwatch,
            clickAction =
                if (watchActionAvailable) {
                  UserWatchButtonClickAction.ToggleWatch
                } else {
                  UserWatchButtonClickAction.None
                },
            longClickHidesRecommendation = false,
            enabled = watchActionAvailable,
        )

    recommendationHidden ->
        UserWatchButtonAction(
            icon = FaMaterialSymbols.Filled.VisibilityOff,
            contentDescription = Res.string.following_recommendation_unblock,
            clickAction = UserWatchButtonClickAction.UnhideRecommendation,
            longClickHidesRecommendation = false,
            enabled = true,
        )

    else ->
        UserWatchButtonAction(
            icon = FaMaterialSymbols.Outlined.Notifications,
            contentDescription = Res.string.watch,
            clickAction =
                if (watchActionAvailable) {
                  UserWatchButtonClickAction.ToggleWatch
                } else {
                  UserWatchButtonClickAction.None
                },
            longClickHidesRecommendation = watchActionAvailable,
            enabled = watchActionAvailable,
        )
  }
}
