package me.domino.fa2.ui.components.submission

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import kotlin.math.roundToInt
import me.domino.fa2.application.submissionseries.SubmissionSeriesResolvedSeries
import me.domino.fa2.ui.components.DetailSectionCardSurface
import me.domino.fa2.ui.components.SubmissionSeriesProbeConfig
import me.domino.fa2.ui.components.TranslatableBlocksCard
import me.domino.fa2.ui.components.TranslatableSectionTitleRow
import me.domino.fa2.ui.host.LocalAppI18n
import me.domino.fa2.ui.host.LocalAppSettings
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.ui.pages.submission.SubmissionAttachmentTextUiState
import me.domino.fa2.ui.pages.submission.SubmissionTranslationUiState
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SubmissionAttachmentTextCard(
    attachmentTextState: SubmissionAttachmentTextUiState,
    translationState: SubmissionTranslationUiState?,
    submissionUrl: String,
    onLoadAttachmentText: () -> Unit,
    onTranslate: () -> Unit,
    onToggleWrapText: () -> Unit,
    onOpenSubmissionSeries: (SubmissionSeriesResolvedSeries) -> Unit,
    requestPagerFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val appI18n = LocalAppI18n.current
  val translationEnabled = LocalAppSettings.current.translationEnabled
  val clickable =
      attachmentTextState is SubmissionAttachmentTextUiState.Idle ||
          attachmentTextState is SubmissionAttachmentTextUiState.Error

  if (attachmentTextState is SubmissionAttachmentTextUiState.Success) {
    translationState?.let { state ->
      TranslatableBlocksCard(
          title = stringResource(Res.string.attachment_text),
          translationState = state,
          emptyText = stringResource(Res.string.attachment_text_empty),
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
          modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
          supportingText = {
            Text(
                text = attachmentTextState.fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          originalTextStyle = MaterialTheme.typography.bodyMedium,
          originalTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
          translatedTextStyle = MaterialTheme.typography.bodyMedium,
          translatedTextColor = MaterialTheme.colorScheme.onSurface,
      )
    }
    return
  }

  DetailSectionCardSurface(
      modifier =
          modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .then(
                  if (clickable) Modifier.clickable(onClick = onLoadAttachmentText) else Modifier
              ),
  ) {
    when (attachmentTextState) {
      is SubmissionAttachmentTextUiState.Loading -> {
        AttachmentCardHeader(fileName = attachmentTextState.fileName)
        val progress = attachmentTextState.progress
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          LoadingIndicator(
              modifier = Modifier.size(24.dp),
              color = MaterialTheme.colorScheme.primary,
          )
          Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
                text = progress?.message ?: stringResource(Res.string.preparing_attachment),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            progress
                ?.currentItemLabel
                ?.takeIf { it.isNotBlank() }
                ?.let { label ->
                  Text(
                      text = label,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
          }
          Text(
              text =
                  "${(((progress?.overallFraction ?: 0f) * 100f).roundToInt()).coerceIn(0, 100)}%",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
          )
        }
      }

      is SubmissionAttachmentTextUiState.Idle -> {
        AttachmentCardHeader(fileName = attachmentTextState.fileName)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.size(22.dp)) {
            Icon(
                imageVector = FaMaterialSymbols.Outlined.Subject,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
          }
          Text(
              text = stringResource(Res.string.extract_attachment_text),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      is SubmissionAttachmentTextUiState.Error -> {
        AttachmentCardHeader(fileName = attachmentTextState.fileName)
        Text(
            text = attachmentTextState.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(Res.string.tap_to_retry),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is SubmissionAttachmentTextUiState.Success -> Unit
    }
  }
}

@Composable
private fun AttachmentCardHeader(
    fileName: String,
    translationState: SubmissionTranslationUiState? = null,
    onTranslate: (() -> Unit)? = null,
    onToggleWrapText: (() -> Unit)? = null,
) {
  val translationEnabled = LocalAppSettings.current.translationEnabled
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    if (translationState != null && onTranslate != null) {
      TranslatableSectionTitleRow(
          title = stringResource(Res.string.attachment_text),
          translating = translationState.translating,
          onTranslate = onTranslate,
          modifier = Modifier.fillMaxWidth(),
          translateActive = translationState.showTranslation,
          showWrapText = !translationState.showTranslation,
          wrapTextActive = translationState.isWrapped,
          onWrapText = onToggleWrapText,
          translationEnabled = translationEnabled,
      )
    } else {
      Text(
          text = stringResource(Res.string.attachment_text),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
      )
    }
    Text(
        text = fileName,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
