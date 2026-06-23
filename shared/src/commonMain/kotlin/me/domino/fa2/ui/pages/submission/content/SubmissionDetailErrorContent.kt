package me.domino.fa2.ui.pages.submission.content

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.detail_load_failed
import fa2.shared.generated.resources.unknown_error
import me.domino.fa2.ui.components.state.StatusSurface
import me.domino.fa2.ui.components.state.StatusSurfaceVariant
import me.domino.fa2.ui.pages.submission.attachmenttext.*
import me.domino.fa2.ui.pages.submission.imageocr.*
import me.domino.fa2.ui.pages.submission.pager.*
import me.domino.fa2.ui.pages.submission.series.*
import me.domino.fa2.ui.pages.submission.translation.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SubmissionDetailErrorContent(message: String, onRetry: () -> Unit) {
  StatusSurface(
      title = stringResource(Res.string.detail_load_failed),
      body = message.ifBlank { stringResource(Res.string.unknown_error) },
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      onAction = onRetry,
      variant = StatusSurfaceVariant.Section,
  )
}
