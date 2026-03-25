package me.domino.fa2.ui.pages.user

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
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
import kotlinx.coroutines.launch
import me.domino.fa2.data.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.ui.components.HtmlText
import me.domino.fa2.ui.components.NetworkImage
import me.domino.fa2.ui.components.SkeletonBlock
import me.domino.fa2.ui.components.TranslatableHtmlBlockContent
import me.domino.fa2.ui.components.TranslateActionButton
import me.domino.fa2.ui.layouts.JournalDetailRouteTopBar
import me.domino.fa2.ui.navigation.goBackHome
import me.domino.fa2.ui.state.rememberSubmissionDescriptionTranslationState
import me.domino.fa2.util.FaUrls
import org.koin.compose.koinInject
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
    val translationService = koinInject<SubmissionDescriptionTranslationService>()
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
          JournalDetailErrorCard(message = snapshot.message, onRetry = screenModel::load)
        }

        is JournalDetailUiState.Success -> {
          JournalDetailContent(
              detail = snapshot.detail,
              translationService = translationService,
              listState = listState,
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
private fun JournalDetailErrorCard(message: String, onRetry: () -> Unit) {
  Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
  ) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(text = "加载失败", style = MaterialTheme.typography.titleMedium)
      Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(onClick = onRetry) { Text("重试") }
    }
  }
}

@Composable
private fun JournalDetailContent(
    detail: me.domino.fa2.data.model.JournalDetail,
    translationService: SubmissionDescriptionTranslationService,
    listState: LazyListState,
    onOpenAuthor: (String) -> Unit,
) {
  val translationController =
      rememberSubmissionDescriptionTranslationState(
          descriptionHtml = detail.bodyHtml,
          service = translationService,
      )

  LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(14.dp),
          border =
              BorderStroke(
                  1.dp,
                  MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
              ),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Top,
          ) {
            Text(
                text = detail.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(end = 10.dp),
            )
            TranslateActionButton(
                translating = translationController.translating,
                label = "日志",
                onTranslate = { translationController.translate() },
            )
          }
          Text(
              text = "${detail.timestampNatural} · ${detail.rating} · ${detail.commentCount} 评论",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          TranslatableHtmlBlockContent(
              blocks = translationController.blocks,
              emptyText = "暂无正文",
              originalTextStyle = MaterialTheme.typography.bodyMedium,
              originalTextColor = MaterialTheme.colorScheme.onSurface,
              translatedTextStyle = MaterialTheme.typography.bodyMedium,
              translatedTextColor = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
    item {
      JournalCommentsCard(
          commentCount = detail.commentCount,
          comments = detail.comments,
          onOpenAuthor = onOpenAuthor,
      )
    }
  }
}

@Composable
private fun JournalCommentsCard(
    commentCount: Int,
    comments: List<me.domino.fa2.data.model.PageComment>,
    onOpenAuthor: (String) -> Unit,
) {
  Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
  ) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
          text = "评论 · $commentCount",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
      )
      if (comments.isEmpty()) {
        Text(
            text = "暂无可展示评论",
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
          html = comment.bodyHtml.ifBlank { "<p>（无内容）</p>" },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
