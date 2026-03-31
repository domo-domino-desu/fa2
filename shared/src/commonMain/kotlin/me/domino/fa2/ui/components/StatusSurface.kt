package me.domino.fa2.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.retry
import org.jetbrains.compose.resources.stringResource

internal enum class StatusSurfaceVariant {
  Page,
  Section,
  Inline,
}

@Composable
internal fun StatusSurface(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    variant: StatusSurfaceVariant = StatusSurfaceVariant.Section,
) {
  val shape =
      when (variant) {
        StatusSurfaceVariant.Page -> RoundedCornerShape(20.dp)
        StatusSurfaceVariant.Section -> RoundedCornerShape(14.dp)
        StatusSurfaceVariant.Inline -> RoundedCornerShape(12.dp)
      }
  val contentPadding =
      when (variant) {
        StatusSurfaceVariant.Page -> 20.dp
        StatusSurfaceVariant.Section -> 14.dp
        StatusSurfaceVariant.Inline -> 12.dp
      }
  val titleStyle =
      when (variant) {
        StatusSurfaceVariant.Page -> MaterialTheme.typography.headlineSmall
        StatusSurfaceVariant.Section -> MaterialTheme.typography.titleMedium
        StatusSurfaceVariant.Inline -> MaterialTheme.typography.titleSmall
      }
  val bodyStyle =
      when (variant) {
        StatusSurfaceVariant.Page -> MaterialTheme.typography.bodyMedium
        StatusSurfaceVariant.Section -> MaterialTheme.typography.bodyMedium
        StatusSurfaceVariant.Inline -> MaterialTheme.typography.bodySmall
      }

  Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = shape,
      border =
          BorderStroke(
              width = 1.dp,
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
          ),
      modifier = modifier.fillMaxWidth(),
  ) {
    Column(
        modifier = Modifier.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(text = title, style = titleStyle)
      if (body.isNotBlank()) {
        Text(
            text = body,
            style = bodyStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (onAction != null) {
        val resolvedActionLabel = actionLabel ?: stringResource(Res.string.retry)
        if (variant == StatusSurfaceVariant.Page) {
          ExpressiveButton(onClick = onAction) { Text(resolvedActionLabel) }
        } else {
          ExpressiveFilledTonalButton(onClick = onAction) { Text(resolvedActionLabel) }
        }
      }
    }
  }
}

@Composable
internal fun HardFallbackScreen(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
  Box(
      modifier = modifier.fillMaxSize().padding(20.dp),
      contentAlignment = Alignment.Center,
  ) {
    StatusSurface(
        title = title,
        body = body,
        modifier = Modifier.widthIn(max = 560.dp),
        actionLabel = actionLabel,
        onAction = onAction,
        variant = StatusSurfaceVariant.Page,
    )
  }
}
