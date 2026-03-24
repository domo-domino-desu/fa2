package me.domino.fa2.ui.components.submission

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.domino.fa2.data.model.PageComment
import me.domino.fa2.ui.components.HtmlText
import me.domino.fa2.ui.components.NetworkImage

@Composable
internal fun SubmissionCommentsCard(
  commentCount: Int,
  comments: List<PageComment>,
  onOpenAuthor: (String) -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(14.dp),
    border =
      BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
      ),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
          SubmissionCommentItem(comment = comment, onOpenAuthor = onOpenAuthor)
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
private fun SubmissionCommentItem(comment: PageComment, onOpenAuthor: (String) -> Unit) {
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
            modifier = Modifier.fillMaxSize().clip(CircleShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
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
    HtmlText(
      html = comment.bodyHtml.ifBlank { "<p>（无内容）</p>" },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}
