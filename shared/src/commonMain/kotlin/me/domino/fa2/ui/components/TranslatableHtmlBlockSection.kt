package me.domino.fa2.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.domino.fa2.data.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.ui.state.SubmissionDescriptionDisplayBlock
import me.domino.fa2.ui.state.SubmissionDescriptionTranslationStatus
import me.domino.fa2.ui.state.rememberSubmissionDescriptionTranslationState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TranslateActionButton(
    translating: Boolean,
    label: String,
    onTranslate: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier =
          modifier
              .padding(1.dp)
              .clickable(enabled = !translating) { onTranslate() }
              .focusProperties { canFocus = false },
  ) {
    if (translating) {
      LoadingIndicator(
          modifier = Modifier.size(16.dp),
          color = MaterialTheme.colorScheme.primary,
      )
    } else {
      Icon(
          imageVector = FaMaterialSymbols.Outlined.Translate,
          contentDescription = "翻译$label",
          modifier = Modifier.size(16.dp),
      )
    }
  }
}

@Composable
internal fun TranslatableHtmlBlockContent(
    blocks: List<SubmissionDescriptionDisplayBlock>,
    emptyText: String,
    originalTextStyle: TextStyle,
    originalTextColor: Color,
    translatedTextStyle: TextStyle,
    translatedTextColor: Color,
    modifier: Modifier = Modifier,
    selectable: Boolean = true,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (blocks.isEmpty()) {
      Text(
          text = emptyText,
          style = originalTextStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      return@Column
    }

    blocks.forEachIndexed { index, block ->
      if (selectable) {
        SelectionContainer {
          HtmlText(
              html = block.originalHtml,
              style = originalTextStyle,
              color = originalTextColor,
          )
        }
      } else {
        HtmlText(
            html = block.originalHtml,
            style = originalTextStyle,
            color = originalTextColor,
        )
      }

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
            if (selectable) {
              SelectionContainer {
                Text(
                    text = translated,
                    style = translatedTextStyle,
                    color = translatedTextColor,
                )
              }
            } else {
              Text(
                  text = translated,
                  style = translatedTextStyle,
                  color = translatedTextColor,
              )
            }
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
          block.status != SubmissionDescriptionTranslationStatus.IDLE && index != blocks.lastIndex
      ) {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TranslatableHtmlBlockSection(
    title: String,
    sourceHtml: String,
    emptyText: String,
    translationService: SubmissionDescriptionTranslationService,
    originalTextStyle: TextStyle,
    originalTextColor: Color,
    translatedTextStyle: TextStyle,
    translatedTextColor: Color,
    modifier: Modifier = Modifier,
    onTranslateRequested: () -> Unit = {},
) {
  val translationController =
      rememberSubmissionDescriptionTranslationState(
          descriptionHtml = sourceHtml,
          service = translationService,
      )

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
      )
      TranslateActionButton(
          translating = translationController.translating,
          label = title,
          onTranslate = {
            translationController.translate()
            onTranslateRequested()
          },
      )
    }

    TranslatableHtmlBlockContent(
        blocks = translationController.blocks,
        emptyText = emptyText,
        originalTextStyle = originalTextStyle,
        originalTextColor = originalTextColor,
        translatedTextStyle = translatedTextStyle,
        translatedTextColor = translatedTextColor,
    )
  }
}
