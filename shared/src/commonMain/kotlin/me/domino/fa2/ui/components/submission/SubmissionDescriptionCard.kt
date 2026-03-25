package me.domino.fa2.ui.components.submission

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.domino.fa2.ui.components.TranslatableHtmlBlockContent
import me.domino.fa2.ui.components.TranslateActionButton
import me.domino.fa2.ui.pages.submission.SubmissionTranslationUiState

@Composable
internal fun SubmissionDescriptionCard(
    translationState: SubmissionTranslationUiState,
    onTranslate: () -> Unit,
    requestPagerFocus: () -> Unit,
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
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Start,
      ) {
        Text(
            text = "描述",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "·",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        TranslateActionButton(
            translating = translationState.translating,
            label = "描述",
            onTranslate = {
              onTranslate()
              requestPagerFocus()
            },
            modifier = Modifier.padding(start = 3.dp, top = 1.dp),
        )
      }
      TranslatableHtmlBlockContent(
          blocks = translationState.blocks,
          emptyText = "暂无描述",
          originalTextStyle = MaterialTheme.typography.bodyMedium,
          originalTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
          translatedTextStyle = MaterialTheme.typography.bodyMedium,
          translatedTextColor = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
