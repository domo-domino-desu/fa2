package me.domino.fa2.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import me.domino.fa2.data.model.User
import org.jetbrains.compose.resources.stringResource

@Composable
fun UserHeaderSummaryCard(
    user: User,
    fallbackUsername: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onOpenShouts: (() -> Unit)? = null,
    onOpenWatchedBy: (() -> Unit)? = null,
    onOpenWatching: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    bodyContent: @Composable (() -> Unit)? = null,
) {
  Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        val bannerUrl = user.profileBannerUrl
        if (bannerUrl.isNotBlank()) {
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
          Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              AvatarImage(
                  url = user.avatarUrl,
                  displayName = user.displayName,
                  username = user.username.ifBlank { fallbackUsername },
                  modifier =
                      if (onClick != null) {
                        Modifier.clickable(enabled = enabled, onClick = onClick)
                      } else {
                        Modifier
                      },
                  size = 54.dp,
                  placeholderTextStyle = MaterialTheme.typography.titleMedium,
                  showLoadingPlaceholder = true,
              )
              Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier =
                        if (onClick != null) {
                          Modifier.clickable(enabled = enabled, onClick = onClick)
                        } else {
                          Modifier
                        },
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                  UserHeaderStatPill(
                      text = stringResource(Res.string.shouts_count, user.shoutCount.toString()),
                      onClick = onOpenShouts,
                      enabled = user.shoutCount > 0 && onOpenShouts != null,
                  )
                  UserHeaderStatPill(
                      text =
                          stringResource(
                              Res.string.followers_count,
                              (user.watchedByCount ?: "--").toString(),
                          ),
                      onClick = onOpenWatchedBy,
                      enabled = onOpenWatchedBy != null,
                  )
                  UserHeaderStatPill(
                      text =
                          stringResource(
                              Res.string.following_count,
                              (user.watchingCount ?: "--").toString(),
                          ),
                      onClick = onOpenWatching,
                      enabled = onOpenWatching != null,
                  )
                }
              }
            }

            trailingContent?.invoke()
          }

          bodyContent?.invoke()
        }
      }
    }
  }
}

@Composable
private fun UserHeaderStatPill(text: String, onClick: (() -> Unit)?, enabled: Boolean = true) {
  Surface(
      color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
      shape = CircleShape,
      modifier =
          if (onClick != null) {
            Modifier.clickable(enabled = enabled, onClick = onClick)
          } else {
            Modifier
          },
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
          text = text,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }
  }
}
