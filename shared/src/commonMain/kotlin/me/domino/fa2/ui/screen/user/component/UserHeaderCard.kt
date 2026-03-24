package me.domino.fa2.ui.screen.user

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.domino.fa2.ui.component.HtmlText
import me.domino.fa2.ui.component.NetworkImage
import me.domino.fa2.ui.component.SkeletonBlock

private const val collapsedProfilePreviewChars = 960

@Composable
internal fun UserHeaderCard(
    state: UserUiState,
    onRetry: () -> Unit,
    onToggleProfileExpanded: () -> Unit,
    onToggleWatch: () -> Unit,
    onOpenWatchedBy: () -> Unit,
    onOpenWatching: () -> Unit,
) {
    val header = state.header
    val isPureSkeleton = state.loading && header == null
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            border = if (isPureSkeleton) {
                null
            } else {
                BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .padding(top = 6.dp, bottom = 4.dp),
        ) {
            val bannerUrl = header?.profileBannerUrl.orEmpty()

            Column(modifier = Modifier.fillMaxWidth()) {
                if (state.loading && header == null) {
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(126.dp),
                        shape = RoundedCornerShape(0.dp),
                    )
                } else if (bannerUrl.isNotBlank()) {
                    NetworkImage(
                        url = bannerUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(126.dp),
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
                            SkeletonBlock(
                                modifier = Modifier.size(54.dp),
                                shape = CircleShape,
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                SkeletonBlock(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .height(22.dp),
                                )
                                SkeletonBlock(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(14.dp),
                                )
                            }
                        }
                        SkeletonBlock(
                            modifier = Modifier
                                .width(240.dp)
                                .height(14.dp),
                        )
                        repeat(3) {
                            SkeletonBlock(
                                modifier = Modifier
                                    .fillMaxWidth(if (it == 2) 0.8f else 1f)
                                    .height(13.dp),
                            )
                        }
                        return@Surface
                    }

                    if (header == null) {
                        Text(
                            text = state.errorMessage ?: "加载用户信息失败",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = onRetry) {
                            Text("重试")
                        }
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
                                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                        )
                                    }
                                }
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
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
                                        text = "关注者 ${header.watchedByCount ?: "--"}",
                                        onClick = onOpenWatchedBy,
                                    )
                                    UserHeaderStatPill(
                                        text = "已关注 ${header.watchingCount ?: "--"}",
                                        onClick = onOpenWatching,
                                    )
                                }
                            }
                        }

                        val hasWatchAction = header.watchActionUrl.isNotBlank()
                        if (hasWatchAction) {
                            OutlinedButton(
                                onClick = onToggleWatch,
                                enabled = !state.watchUpdating,
                            ) {
                                Text(
                                    if (state.watchUpdating) {
                                        "处理中..."
                                    } else if (header.isWatching) {
                                        "Unwatch"
                                    } else {
                                        "Watch"
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        text = listOf(header.userTitle, "Registered: ${header.registeredAt}")
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (header.profileHtml.isNotBlank()) {
                        val shouldCollapse = header.profileHtml.length > collapsedProfilePreviewChars
                        HtmlText(
                            html = header.profileHtml,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (state.profileExpanded || !shouldCollapse) Int.MAX_VALUE else 14,
                        )
                        if (shouldCollapse) {
                            AssistChip(
                                onClick = onToggleProfileExpanded,
                                label = {
                                    Text(if (state.profileExpanded) "收起简介" else "展开简介")
                                },
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
private fun UserHeaderStatPill(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
        shape = CircleShape,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
