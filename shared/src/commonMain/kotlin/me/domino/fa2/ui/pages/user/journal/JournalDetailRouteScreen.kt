package me.domino.fa2.ui.pages.user.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import fa2.shared.generated.resources.*
import kotlinx.coroutines.launch
import me.domino.fa2.application.submissionseries.SubmissionSeriesResolvedSeries
import me.domino.fa2.ui.components.DetailSectionCardSurface
import me.domino.fa2.ui.components.ExpressiveFilledTonalButton
import me.domino.fa2.ui.components.HtmlText
import me.domino.fa2.ui.components.NetworkImage
import me.domino.fa2.ui.components.SkeletonBlock
import me.domino.fa2.ui.components.SubmissionSeriesProbeConfig
import me.domino.fa2.ui.components.TranslatableBlocksCard
import me.domino.fa2.ui.host.LocalAppI18n
import me.domino.fa2.ui.host.LocalAppSettings
import me.domino.fa2.ui.layouts.JournalDetailRouteTopBar
import me.domino.fa2.ui.navigation.goBackHome
import me.domino.fa2.ui.navigation.openSubmissionSeries
import me.domino.fa2.ui.pages.user.route.UserChildRoute
import me.domino.fa2.ui.pages.user.route.UserRouteScreen
import me.domino.fa2.util.FaUrls
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf

/** Journal 详情路由页面。 */
class JournalDetailRouteScreen(
    /** Journal ID。 */
    private val journalId: Int,
    /** Journal URL 兜底。 */
    private val journalUrl: String? = null,
) : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel =
        koinScreenModel<JournalDetailScreenModel> { parametersOf(journalId, journalUrl) }
    val appI18n = LocalAppI18n.current
    val settings = LocalAppSettings.current
    val listState: LazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val state by screenModel.state.collectAsState()
    val shareUrl =
        when (val snapshot = state) {
          is JournalDetailUiState.Success -> snapshot.detail.journalUrl
          JournalDetailUiState.Loading,
          is JournalDetailUiState.Error -> {
            journalUrl?.trim().takeUnless { it.isNullOrBlank() }
                ?: if (journalId > 0) FaUrls.journal(journalId) else ""
          }
        }

    Column(modifier = Modifier.fillMaxSize()) {
      JournalDetailRouteTopBar(
          onBack = { navigator.pop() },
          onGoHome = { navigator.goBackHome() },
          shareUrl = shareUrl,
          onTitleClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
      )

      when (val snapshot = state) {
        JournalDetailUiState.Loading -> {
          JournalDetailSkeleton()
        }

        is JournalDetailUiState.Error -> {
          JournalDetailErrorCard(
              title = stringResource(Res.string.load_failed),
              retryText = stringResource(Res.string.retry),
              message = snapshot.message,
              onRetry = screenModel::load,
          )
        }

        is JournalDetailUiState.Success -> {
          JournalDetailContent(
              state = snapshot,
              listState = listState,
              onTranslate = {
                if (settings.translationEnabled) {
                  screenModel.translateCurrent()
                }
              },
              onToggleWrapText = {
                if (settings.translationEnabled) {
                  screenModel.toggleWrapTextCurrent()
                }
              },
              translationEnabled = settings.translationEnabled,
              onOpenSubmissionSeries = { series -> navigator.openSubmissionSeries(series) },
              onOpenAuthor = { author ->
                val normalized = author.trim()
                if (normalized.isNotBlank()) {
                  navigator.push(
                      UserRouteScreen(
                          username = normalized,
                          initialChildRoute = UserChildRoute.Gallery,
                      )
                  )
                }
              },
          )
        }
      }
    }
  }
}

@Composable
private fun JournalDetailSkeleton() {
  Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    SkeletonBlock(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        shape = RoundedCornerShape(14.dp),
    )
    SkeletonBlock(
        modifier = Modifier.fillMaxWidth().height(168.dp),
        shape = RoundedCornerShape(14.dp),
    )
  }
}

@Composable
private fun JournalDetailErrorCard(
    title: String,
    retryText: String,
    message: String,
    onRetry: () -> Unit,
) {
  DetailSectionCardSurface(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
  ) {
    Text(text = title, style = MaterialTheme.typography.titleMedium)
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ExpressiveFilledTonalButton(onClick = onRetry) { Text(retryText) }
  }
}

@Composable
private fun JournalDetailContent(
    state: JournalDetailUiState.Success,
    listState: LazyListState,
    onTranslate: () -> Unit,
    onToggleWrapText: () -> Unit,
    translationEnabled: Boolean,
    onOpenSubmissionSeries: (SubmissionSeriesResolvedSeries) -> Unit,
    onOpenAuthor: (String) -> Unit,
) {
  val appI18n = LocalAppI18n.current
  val detail = state.detail

  LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      TranslatableBlocksCard(
          title = detail.title,
          translationState = state.bodyTranslationState,
          emptyText = stringResource(Res.string.no_content),
          onTranslate = onTranslate,
          onToggleWrapText = onToggleWrapText,
          seriesProbeConfig =
              SubmissionSeriesProbeConfig(
                  baseUrl = detail.journalUrl,
                  onOpenSeries = onOpenSubmissionSeries,
              ),
          translationEnabled = translationEnabled,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          titleMaxLines = 2,
          supportingText = {
            Text(
                text =
                    "${detail.timestampNatural} · ${detail.rating} · " +
                        stringResource(Res.string.comments_inline_count, detail.commentCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          originalTextStyle = MaterialTheme.typography.bodyMedium,
          originalTextColor = MaterialTheme.colorScheme.onSurface,
          translatedTextStyle = MaterialTheme.typography.bodyMedium,
          translatedTextColor = MaterialTheme.colorScheme.onSurface,
      )
    }
    item {
      JournalCommentsCard(
          commentCount = detail.commentCount,
          comments = detail.comments,
          emptyText = stringResource(Res.string.no_displayable_comments),
          onOpenAuthor = onOpenAuthor,
      )
    }
  }
}

@Composable
private fun JournalCommentsCard(
    commentCount: Int,
    comments: List<me.domino.fa2.data.model.PageComment>,
    emptyText: String,
    onOpenAuthor: (String) -> Unit,
) {
  DetailSectionCardSurface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
    Text(
        text = stringResource(Res.string.comments, commentCount),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    if (comments.isEmpty()) {
      Text(
          text = emptyText,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      comments.take(40).forEachIndexed { index, comment ->
        JournalCommentItem(comment = comment, onOpenAuthor = onOpenAuthor)
        if (index != minOf(comments.lastIndex, 39)) {
          HorizontalDivider(
              thickness = 1.dp,
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
          )
        }
      }
    }
  }
}

@Composable
private fun JournalCommentItem(
    comment: me.domino.fa2.data.model.PageComment,
    onOpenAuthor: (String) -> Unit,
) {
  val normalizedAuthor = comment.author.trim()
  val indentation = (comment.depth.coerceIn(0, 6) * 10).dp
  Column(
      modifier = Modifier.fillMaxWidth().padding(start = indentation),
      verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    val authorClickableModifier =
        if (normalizedAuthor.isNotBlank()) {
          Modifier.clickable { onOpenAuthor(normalizedAuthor) }
        } else {
          Modifier
        }
    Row(
        modifier = authorClickableModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
          modifier = Modifier.size(30.dp),
      ) {
        if (comment.authorAvatarUrl.isNotBlank()) {
          NetworkImage(
              url = comment.authorAvatarUrl,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
              showLoadingPlaceholder = false,
          )
        } else {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = comment.authorDisplayName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = comment.authorDisplayName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = comment.timestampNatural,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    SelectionContainer {
      HtmlText(
          html = comment.bodyHtml.ifBlank { stringResource(Res.string.empty_comment_html) },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
