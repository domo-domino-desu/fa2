package me.domino.fa2.ui.components.submission

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import me.domino.fa2.ui.components.TranslatableBlocksCard
import me.domino.fa2.ui.host.LocalAppSettings
import me.domino.fa2.ui.pages.submission.SubmissionTranslationUiState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SubmissionDescriptionCard(
    translationState: SubmissionTranslationUiState,
    onTranslate: () -> Unit,
    onToggleWrapText: () -> Unit,
    requestPagerFocus: () -> Unit,
) {
  val translationEnabled = LocalAppSettings.current.translationEnabled
  TranslatableBlocksCard(
      title = stringResource(Res.string.description),
      translationState = translationState,
      emptyText = stringResource(Res.string.no_description),
      onTranslate = {
        onTranslate()
        requestPagerFocus()
      },
      onToggleWrapText = {
        onToggleWrapText()
        requestPagerFocus()
      },
      translationEnabled = translationEnabled,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      originalTextStyle = MaterialTheme.typography.bodyMedium,
      originalTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
      translatedTextStyle = MaterialTheme.typography.bodyMedium,
      translatedTextColor = MaterialTheme.colorScheme.onSurface,
  )
}
