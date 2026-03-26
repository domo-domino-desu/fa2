package me.domino.fa2.ui.pages.user.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import me.domino.fa2.ui.components.HtmlText
import me.domino.fa2.ui.components.NetworkImage
import me.domino.fa2.ui.components.SkeletonBlock
import me.domino.fa2.ui.icons.FaMaterialSymbols
import org.jetbrains.compose.resources.stringResource

private const val collapsedProfilePreviewMaxLines = 5

@Composable
internal fun UserHeaderCard(
    state: UserUiState,
    onRetry: () -> Unit,
    onToggleProfileExpanded: () -> Unit,
    onToggleWatch: () -> Unit,
    onOpenWatchedBy: () -> Unit,
    onOpenShouts: () -> Unit,
    onOpenWatching: () -> Unit,
) {
  val header = state.header
  val isPureSkeleton = state.loading && header == null
  Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border =
            if (isPureSkeleton) {
              null
            } else {
              BorderStroke(
                  width = 1.dp,
                  color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
              )
            },
        modifier = Modifier.fillMaxWidth(),
    ) {
      val bannerUrl = header?.profileBannerUrl.orEmpty()

      Column(modifier = Modifier.fillMaxWidth()) {
        if (state.loading && header == null) {
          SkeletonBlock(
              modifier = Modifier.fillMaxWidth().height(126.dp),
              shape = RoundedCornerShape(0.dp),
          )
        } else if (bannerUrl.isNotBlank()) {
          NetworkImage(
              url = bannerUrl,
              modifier = Modifier.fillMaxWidth().height(126.dp),
              contentScale = ContentScale.Crop,
              showLoadingPlaceholder = false,
          )
        }

        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (state.loading && header == null) {
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
            return@Surface
          }

          if (header == null) {
            Text(
                text = state.errorMessage ?: stringResource(Res.string.load_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
            return@Surface
          }

          Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Surface(
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                  modifier = Modifier.size(54.dp),
              ) {
                if (header.avatarUrl.isNotBlank()) {
                  NetworkImage(
                      url = header.avatarUrl,
                      modifier = Modifier.fillMaxSize(),
                      contentScale = ContentScale.Crop,
                  )
                } else {
                  Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = header.displayName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier.padding(
                                horizontal = 18.dp,
                                vertical = 14.dp,
                            ),
                    )
                  }
                }
              }
              Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = header.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                  UserHeaderStatPill(
                      text =
                          stringResource(
                              Res.string.followers_count,
                              (header.watchedByCount ?: "--").toString(),
                          ),
                      onClick = onOpenWatchedBy,
                  )
                  UserHeaderStatPill(
                      text = stringResource(Res.string.shouts_count, header.shoutCount.toString()),
                      onClick = onOpenShouts,
                      enabled = header.shoutCount > 0,
                  )
                  UserHeaderStatPill(
                      text =
                          stringResource(
                              Res.string.following_count,
                              (header.watchingCount ?: "--").toString(),
                          ),
                      onClick = onOpenWatching,
                  )
                }
              }
            }

            val hasWatchAction = header.watchActionUrl.isNotBlank()
            if (hasWatchAction) {
              UserWatchActionButton(
                  isWatching = header.isWatching,
                  updating = state.watchUpdating,
                  onClick = onToggleWatch,
              )
            }
          }
          UserMetadataAndContactsRow(
              userTitle = header.userTitle,
              registeredAtText = me.domino.fa2.i18n.registeredAtText(header.registeredAt),
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
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun UserProfileExpandToggle(expanded: Boolean, onClick: () -> Unit) {
  val contentDescription =
      if (expanded) {
        stringResource(Res.string.collapse_profile)
      } else {
        stringResource(Res.string.expand_profile)
      }
  Box(
      modifier =
          Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 4.dp, bottom = 1.dp),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        imageVector =
            if (expanded) {
              FaMaterialSymbols.Outlined.ExpandLess
            } else {
              FaMaterialSymbols.Outlined.ExpandMore
            },
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
  }
}

@Composable
private fun UserHeaderStatPill(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
  Surface(
      color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
      shape = CircleShape,
      modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (leadingIcon != null) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(12.dp),
        )
      }
      Text(
          text = text,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun UserWatchActionButton(
    isWatching: Boolean,
    updating: Boolean,
    onClick: () -> Unit,
) {
  val contentDescription =
      when {
        updating -> stringResource(Res.string.processing)
        isWatching -> stringResource(Res.string.unwatch)
        else -> stringResource(Res.string.watch)
      }
  Surface(
      onClick = onClick,
      enabled = !updating,
      shape = RoundedCornerShape(18.dp),
      color =
          if (isWatching) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.surface
          },
      modifier = Modifier.size(52.dp),
  ) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      if (updating) {
        LoadingIndicator(
            modifier = Modifier.size(22.dp),
            color = MaterialTheme.colorScheme.primary,
        )
      } else {
        Icon(
            imageVector =
                if (isWatching) {
                  FaMaterialSymbols.Filled.Notifications
                } else {
                  FaMaterialSymbols.Outlined.Notifications
                },
            contentDescription = contentDescription,
        )
      }
    }
  }
}
