package me.domino.fa2.ui.components.submission

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.domino.fa2.ui.components.accessibleClickableSummary

@Composable
internal fun SubmissionInfoMetricChip(metric: SubmissionInfoMetric) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      modifier =
          if (metric.onClick != null) {
            Modifier.clickable(onClick = metric.onClick)
                .accessibleClickableSummary(title = metric.text)
          } else {
            Modifier
          },
  ) {
    Icon(
        imageVector = metric.icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(12.dp),
    )
    Text(
        text = metric.text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
