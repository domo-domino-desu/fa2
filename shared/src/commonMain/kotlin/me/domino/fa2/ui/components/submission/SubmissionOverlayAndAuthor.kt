package me.domino.fa2.ui.components.submission

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import me.domino.fa2.ui.components.NetworkImage
import me.domino.fa2.util.isGifUrl

@Composable
internal fun SubmissionZoomImageOverlay(imageUrl: String, onDismiss: () -> Unit) {
  Box(
    modifier =
      Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f))
  ) {
    val normalizedUrl = imageUrl.trim()
    if (isGifUrl(normalizedUrl)) {
      NetworkImage(
        url = normalizedUrl,
        modifier = Modifier.fillMaxSize().padding(12.dp).clickable { onDismiss() },
        contentScale = ContentScale.Fit,
        showLoadingPlaceholder = false,
      )
    } else {
      CoilZoomAsyncImage(
        model = normalizedUrl,
        contentDescription = "查看原图",
        modifier = Modifier.fillMaxSize().padding(12.dp),
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.High,
        onTap = { onDismiss() },
      )
    }
    IconButton(
      onClick = onDismiss,
      modifier =
        Modifier.align(Alignment.TopEnd).padding(12.dp).focusProperties { canFocus = false },
    ) {
      Icon(
        imageVector = Icons.Filled.Close,
        contentDescription = "关闭预览",
        tint = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
internal fun SubmissionAuthorRow(
  authorDisplayName: String,
  author: String,
  authorAvatarUrl: String,
  timestamp: String,
  onOpenAuthor: (String) -> Unit,
) {
  val normalizedAuthor = author.trim()
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 16.dp)
        .then(
          if (normalizedAuthor.isNotBlank()) {
            Modifier.clickable { onOpenAuthor(normalizedAuthor) }
          } else {
            Modifier
          }
        ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
    ) {
      if (authorAvatarUrl.isNotBlank()) {
        NetworkImage(
          url = authorAvatarUrl,
          modifier = Modifier.size(40.dp).clip(CircleShape),
          contentScale = ContentScale.Crop,
          showLoadingPlaceholder = false,
        )
      } else {
        Text(
          text = authorDisplayName.firstOrNull()?.uppercase() ?: "?",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
      }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = authorDisplayName,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = timestamp,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
