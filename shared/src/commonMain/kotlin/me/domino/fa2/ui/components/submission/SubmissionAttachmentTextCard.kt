package me.domino.fa2.ui.components.submission

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import me.domino.fa2.ui.components.TranslatableHtmlBlockContent
import me.domino.fa2.ui.components.TranslateActionButton
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.ui.pages.submission.SubmissionAttachmentTextUiState
import me.domino.fa2.ui.pages.submission.SubmissionTranslationUiState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SubmissionAttachmentTextCard(
    attachmentTextState: SubmissionAttachmentTextUiState,
    translationState: SubmissionTranslationUiState?,
    onLoadAttachmentText: () -> Unit,
    onTranslate: () -> Unit,
    requestPagerFocus: () -> Unit,
) {
  val clickable =
      attachmentTextState is SubmissionAttachmentTextUiState.Idle ||
          attachmentTextState is SubmissionAttachmentTextUiState.Error

  Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(14.dp),
      border =
          BorderStroke(
              width = 1.dp,
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
          ),
      modifier =
          Modifier.fillMaxWidth()
              .padding(horizontal = 16.dp)
              .then(
                  if (clickable) Modifier.clickable(onClick = onLoadAttachmentText) else Modifier
              ),
  ) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      when (attachmentTextState) {
        is SubmissionAttachmentTextUiState.Success -> {
          AttachmentCardHeader(
              fileName = attachmentTextState.fileName,
              translateButton = {
                TranslateActionButton(
                    translating = translationState?.translating == true,
                    label = "附件文本",
                    onTranslate = {
                      onTranslate()
                      requestPagerFocus()
                    },
                )
              },
          )
          TranslatableHtmlBlockContent(
              blocks = translationState?.blocks ?: emptyList(),
              emptyText = "附件内容为空",
              originalTextStyle = MaterialTheme.typography.bodyMedium,
              originalTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
              translatedTextStyle = MaterialTheme.typography.bodyMedium,
              translatedTextColor = MaterialTheme.colorScheme.onSurface,
          )
        }

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
                  text = progress?.message ?: "准备解析附件",
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
                text = "点击解析附件中的可翻译文本",
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
              text = "点击重试",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun AttachmentCardHeader(
    fileName: String,
    translateButton: @Composable (() -> Unit)? = null,
) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
          text = "附件文本",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
      )
      Text(
          text = fileName,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    translateButton?.invoke()
  }
}
