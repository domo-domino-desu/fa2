package me.domino.fa2.ui.pages.submission.content

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import me.domino.fa2.domain.submissionseries.SubmissionSeriesResolvedSeries
import me.domino.fa2.ui.app.LocalAppSettings
import me.domino.fa2.ui.components.html.TranslatableBlocksCard
import me.domino.fa2.ui.pages.submission.attachmenttext.*
import me.domino.fa2.ui.pages.submission.imageocr.*
import me.domino.fa2.ui.pages.submission.pager.*
import me.domino.fa2.ui.pages.submission.series.*
import me.domino.fa2.ui.pages.submission.translation.*
import me.domino.fa2.ui.pages.submission.translation.SubmissionTranslationUiState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SubmissionDescriptionCard(
    translationState: SubmissionTranslationUiState,
    submissionUrl: String,
    onTranslate: () -> Unit,
    onToggleWrapText: () -> Unit,
    onOpenSubmissionSeries: (SubmissionSeriesResolvedSeries) -> Unit,
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
      seriesProbeConfig =
          SubmissionSeriesProbeConfig(
              baseUrl = submissionUrl,
              onOpenSeries = onOpenSubmissionSeries,
          ),
      translationEnabled = translationEnabled,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      originalTextStyle = MaterialTheme.typography.bodyMedium,
      originalTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
      translatedTextStyle = MaterialTheme.typography.bodyMedium,
      translatedTextColor = MaterialTheme.colorScheme.onSurface,
  )
}
