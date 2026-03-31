package me.domino.fa2.ui.pages.user.watchlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import me.domino.fa2.data.model.WatchlistUser
import me.domino.fa2.ui.components.NetworkImage
import me.domino.fa2.ui.components.SkeletonBlock
import me.domino.fa2.ui.components.StatusSurface
import me.domino.fa2.ui.components.StatusSurfaceVariant

@Composable
fun WatchlistSkeleton() {
  Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    repeat(8) {
      SkeletonBlock(
          modifier = Modifier.fillMaxWidth().height(76.dp),
          shape = RoundedCornerShape(14.dp),
      )
    }
  }
}

@Composable
fun WatchlistUserCard(item: WatchlistUser, onClick: () -> Unit) {
  Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable(onClick = onClick),
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          modifier = Modifier.size(42.dp),
      ) {
        NetworkImage(
            url = "https://a.furaffinity.net/${item.username.lowercase()}.gif",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            showLoadingPlaceholder = true,
        )
      }
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "~${item.username}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
fun WatchlistStatusCard(title: String, body: String, onRetry: () -> Unit) {
  StatusSurface(
      title = title,
      body = body,
      modifier = Modifier.padding(horizontal = 12.dp),
      onAction = onRetry,
      variant = StatusSurfaceVariant.Section,
  )
}
