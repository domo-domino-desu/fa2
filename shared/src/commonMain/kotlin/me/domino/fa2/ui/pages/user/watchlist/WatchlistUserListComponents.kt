package me.domino.fa2.ui.pages.user.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.domino.fa2.data.model.WatchlistUser
import me.domino.fa2.ui.components.SkeletonBlock
import me.domino.fa2.ui.components.StatusSurface
import me.domino.fa2.ui.components.StatusSurfaceVariant
import me.domino.fa2.ui.components.UserHorizontalCard

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
  UserHorizontalCard(user = item, onClick = onClick)
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
