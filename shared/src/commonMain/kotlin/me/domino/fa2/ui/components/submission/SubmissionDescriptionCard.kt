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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.domino.fa2.data.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.ui.components.HtmlText
import me.domino.fa2.ui.state.SubmissionDescriptionTranslationStatus
import me.domino.fa2.ui.state.rememberSubmissionDescriptionTranslationState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SubmissionDescriptionCard(
    descriptionHtml: String,
    translationService: SubmissionDescriptionTranslationService,
    requestPagerFocus: () -> Unit,
) {
  val translationController =
      rememberSubmissionDescriptionTranslationState(
          descriptionHtml = descriptionHtml,
          service = translationService,
      )
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
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Start,
          verticalAlignment = Alignment.CenterVertically,
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
        Box(
            modifier =
                Modifier.padding(start = 3.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !translationController.translating) {
                      translationController.translate()
                      requestPagerFocus()
                    }
                    .padding(1.dp)
                    .focusProperties { canFocus = false }
        ) {
          if (translationController.translating) {
            LoadingIndicator(
                modifier = Modifier.size(14.dp),
                color = MaterialTheme.colorScheme.primary,
            )
          } else {
            Icon(
                imageVector = Icons.Outlined.Translate,
                contentDescription = "翻译描述",
                modifier = Modifier.size(17.dp),
            )
          }
        }
      }
      if (translationController.blocks.isEmpty()) {
        Text(
            text = "暂无描述",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return@Column
      }

      translationController.blocks.forEachIndexed { index, block ->
        HtmlText(
            html = block.originalHtml,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (block.status) {
          SubmissionDescriptionTranslationStatus.IDLE -> Unit
          SubmissionDescriptionTranslationStatus.PENDING -> {
            Text(
                text = "……",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          SubmissionDescriptionTranslationStatus.SUCCESS -> {
            val translated = block.translated.orEmpty()
            if (translated.isNotBlank()) {
              Text(
                  text = translated,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }

          SubmissionDescriptionTranslationStatus.EMPTY -> {
            Text(
                text = "翻译结果为空",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          SubmissionDescriptionTranslationStatus.FAILURE -> {
            Text(
                text = "翻译失败",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
          }
        }

        if (
            block.status != SubmissionDescriptionTranslationStatus.IDLE &&
                index != translationController.blocks.lastIndex
        ) {
          HorizontalDivider(
              thickness = 1.dp,
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
          )
        }
      }
    }
  }
}
