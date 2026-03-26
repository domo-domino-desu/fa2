package me.domino.fa2.ui.components.submission

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.domino.fa2.ui.components.TranslatableBlocksCard
import me.domino.fa2.ui.pages.submission.SubmissionTranslationUiState

@Composable
internal fun SubmissionDescriptionCard(
    translationState: SubmissionTranslationUiState,
    onTranslate: () -> Unit,
    onToggleWrapText: () -> Unit,
    requestPagerFocus: () -> Unit,
) {
  TranslatableBlocksCard(
      title = "描述",
      translationState = translationState,
      emptyText = "暂无描述",
      onTranslate = {
        onTranslate()
        requestPagerFocus()
      },
      onToggleWrapText = {
        onToggleWrapText()
        requestPagerFocus()
      },
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      originalTextStyle = MaterialTheme.typography.bodyMedium,
      originalTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
      translatedTextStyle = MaterialTheme.typography.bodyMedium,
      translatedTextColor = MaterialTheme.colorScheme.onSurface,
  )
}
