package me.domino.fa2.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import kotlin.math.roundToInt
import me.domino.fa2.application.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.ui.pages.submission.SubmissionTranslationUiState
import me.domino.fa2.ui.state.SubmissionDescriptionDisplayBlock
import me.domino.fa2.ui.state.SubmissionDescriptionTranslationStatus
import me.domino.fa2.ui.state.rememberSubmissionDescriptionTranslationState
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TranslateActionButton(
    translating: Boolean,
    label: String,
    onTranslate: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier =
          modifier
              .padding(1.dp)
              .focusProperties { canFocus = false }
              .clickable(enabled = enabled && !translating, onClick = onTranslate),
      contentAlignment = Alignment.Center,
  ) {
    if (translating) {
      LoadingIndicator(
          modifier = Modifier.size(16.dp),
          color = MaterialTheme.colorScheme.primary,
      )
    } else {
      Icon(
          imageVector = FaMaterialSymbols.Outlined.Translate,
          contentDescription = stringResource(Res.string.translate_content_description, label),
          modifier = Modifier.size(16.dp),
          tint = translationActionTint(active = active),
      )
    }
  }
}

@Composable
internal fun WrapTextActionButton(
    label: String,
    onWrapText: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier =
          modifier
              .padding(1.dp)
              .focusProperties { canFocus = false }
              .clickable(enabled = enabled, onClick = onWrapText),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        imageVector = FaMaterialSymbols.Outlined.WrapText,
        contentDescription = stringResource(Res.string.wrap_text_content_description, label),
        modifier = Modifier.size(16.dp),
        tint = translationActionTint(active = active),
    )
  }
}

@Composable
internal fun TranslatableSectionTitleRow(
    title: String,
    translating: Boolean,
    onTranslate: () -> Unit,
    modifier: Modifier = Modifier,
    titleMaxLines: Int = 1,
    translateActive: Boolean = false,
    showWrapText: Boolean = false,
    wrapTextActive: Boolean = false,
    onWrapText: (() -> Unit)? = null,
    translationEnabled: Boolean = true,
) {
  val titleContent: @Composable () -> Unit = {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = titleMaxLines,
        overflow = TextOverflow.Ellipsis,
    )
  }
  val actionsContent: @Composable () -> Unit = {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier.padding(top = 3.dp),
    ) {
      TranslateActionButton(
          translating = translating,
          label = title,
          onTranslate = onTranslate,
          enabled = translationEnabled,
          active = translateActive,
          modifier = Modifier.padding(start = 4.dp, top = 1.dp),
      )
      if (translationEnabled && showWrapText && onWrapText != null) {
        WrapTextActionButton(
            label = title,
            onWrapText = onWrapText,
            enabled = translationEnabled,
            active = wrapTextActive,
            modifier = Modifier.padding(start = 4.dp, top = 1.dp),
        )
      }
    }
  }
  SubcomposeLayout(modifier = modifier) { constraints ->
    val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
    val gapPx = 4.dp.roundToPx()

    val actionsPlaceable =
        subcompose(TitleRowSlot.Actions, actionsContent).single().measure(looseConstraints)
    val boundedWidth = constraints.hasBoundedWidth
    val availableWidth = if (boundedWidth) constraints.maxWidth else Int.MAX_VALUE
    val titleIntrinsicWidth =
        subcompose(TitleRowSlot.TitleIntrinsic, titleContent)
            .single()
            .maxIntrinsicWidth(constraints.maxHeight)
    val canPlaceActionsAfterTitle =
        !boundedWidth || titleIntrinsicWidth + gapPx + actionsPlaceable.width <= availableWidth

    val titleMaxWidth =
        if (canPlaceActionsAfterTitle || !boundedWidth) {
          availableWidth
        } else {
          (availableWidth - gapPx - actionsPlaceable.width).coerceAtLeast(0)
        }
    val titlePlaceable =
        subcompose(TitleRowSlot.TitleMeasured, titleContent)
            .single()
            .measure(looseConstraints.copy(maxWidth = titleMaxWidth))

    val layoutWidth =
        if (boundedWidth) {
          constraints.maxWidth
        } else {
          titlePlaceable.width + gapPx + actionsPlaceable.width
        }
    val layoutHeight = maxOf(titlePlaceable.height, actionsPlaceable.height)
    val actionsX =
        if (!boundedWidth || canPlaceActionsAfterTitle) {
          (titlePlaceable.width + gapPx).coerceAtMost(layoutWidth - actionsPlaceable.width)
        } else {
          layoutWidth - actionsPlaceable.width
        }

    layout(width = layoutWidth, height = layoutHeight) {
      titlePlaceable.placeRelative(x = 0, y = 0)
      actionsPlaceable.placeRelative(x = actionsX.coerceAtLeast(0), y = 0)
    }
  }
}

private enum class TitleRowSlot {
  TitleIntrinsic,
  TitleMeasured,
  Actions,
}

@Composable
internal fun DetailSectionCardSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(14.dp),
      border =
          BorderStroke(
              width = 1.dp,
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
          ),
      modifier = modifier,
  ) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
  }
}

@Composable
internal fun TranslatableBlocksCard(
    title: String,
    translationState: SubmissionTranslationUiState,
    emptyText: String,
    onTranslate: () -> Unit,
    onToggleWrapText: () -> Unit,
    translationEnabled: Boolean = true,
    seriesProbeConfig: SubmissionSeriesProbeConfig? = null,
    modifier: Modifier = Modifier,
    titleMaxLines: Int = 1,
    supportingText: (@Composable ColumnScope.() -> Unit)? = null,
    originalTextStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    originalTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    translatedTextStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    translatedTextColor: Color = MaterialTheme.colorScheme.onSurface,
) {
  val effectiveTranslationState =
      if (translationEnabled) {
        translationState
      } else {
        translationState.copy(showTranslation = false)
      }
  val effectiveSeriesProbeConfig =
      remember(seriesProbeConfig, translationState.sourceKey) {
        seriesProbeConfig?.copy(
            sourceKey = translationState.sourceKey,
            sourceBlocks = translationState.rawVariant.sourceBlocks,
        )
      }
  val blockTrailingActions = rememberSubmissionSeriesTrailingActions(effectiveSeriesProbeConfig)
  DetailSectionCardSurface(modifier = modifier) {
    TranslatableSectionTitleRow(
        title = title,
        translating = effectiveTranslationState.translating,
        onTranslate = onTranslate,
        modifier = Modifier.fillMaxWidth(),
        titleMaxLines = titleMaxLines,
        translateActive = effectiveTranslationState.showTranslation,
        showWrapText = !effectiveTranslationState.showTranslation,
        wrapTextActive = effectiveTranslationState.isWrapped,
        onWrapText = onToggleWrapText,
        translationEnabled = translationEnabled,
    )
    supportingText?.invoke(this)
    TranslatableHtmlBlockContent(
        blocks = effectiveTranslationState.blocks,
        emptyText = emptyText,
        originalTextStyle = originalTextStyle,
        originalTextColor = originalTextColor,
        translatedTextStyle = translatedTextStyle,
        translatedTextColor = translatedTextColor,
        blockTrailingActions = blockTrailingActions,
    )
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
    blockTrailingActions: Map<Int, SubmissionSeriesTrailingAction> = emptyMap(),
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
      val shouldTrimOriginalTrailingWhitespace =
          block.status == SubmissionDescriptionTranslationStatus.SUCCESS &&
              block.translated.orEmpty().isNotBlank()
      val originalText = block.originalText
      OriginalBlockWithTrailingAction(
          originalText = originalText,
          originalHtml = block.originalHtml,
          shouldTrimOriginalTrailingWhitespace = shouldTrimOriginalTrailingWhitespace,
          textStyle = originalTextStyle,
          textColor = originalTextColor,
          selectable = selectable,
          trailingAction = blockTrailingActions[index],
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
              text = stringResource(Res.string.translation_empty),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        SubmissionDescriptionTranslationStatus.FAILURE -> {
          Text(
              text = stringResource(Res.string.translation_failed),
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
private fun OriginalBlockWithTrailingAction(
    originalText: String?,
    originalHtml: String,
    shouldTrimOriginalTrailingWhitespace: Boolean,
    textStyle: TextStyle,
    textColor: Color,
    selectable: Boolean,
    trailingAction: SubmissionSeriesTrailingAction?,
) {
  if (trailingAction == null) {
    OriginalBlockText(
        originalText = originalText,
        originalHtml = originalHtml,
        shouldTrimOriginalTrailingWhitespace = shouldTrimOriginalTrailingWhitespace,
        textStyle = textStyle,
        textColor = textColor,
        selectable = selectable,
    )
    return
  }

  val iconSize = 16.dp
  val iconGap = 6.dp
  val reservedEndSpace = iconSize + iconGap + 2.dp
  var layoutResult by
      remember(originalText, originalHtml) { mutableStateOf<TextLayoutResult?>(null) }

  Box(modifier = Modifier.fillMaxWidth()) {
    Box(modifier = Modifier.fillMaxWidth().padding(end = reservedEndSpace)) {
      OriginalBlockText(
          originalText = originalText,
          originalHtml = originalHtml,
          shouldTrimOriginalTrailingWhitespace = shouldTrimOriginalTrailingWhitespace,
          textStyle = textStyle,
          textColor = textColor,
          selectable = selectable,
          onTextLayout = { result -> layoutResult = result },
      )
    }
    val lastLayout = layoutResult
    Box(
        modifier =
            Modifier.offset {
                  val result = lastLayout
                  if (result == null || result.lineCount <= 0) {
                    IntOffset.Zero
                  } else {
                    val lastLine = result.lineCount - 1
                    val x = (result.getLineRight(lastLine) + iconGap.toPx()).roundToInt()
                    val y =
                        (result.getLineBottom(lastLine) - iconSize.toPx())
                            .roundToInt()
                            .coerceAtLeast(0)
                    IntOffset(x = x, y = y)
                  }
                }
                .clickable(
                    enabled = trailingAction.state != SubmissionSeriesTrailingActionState.LOADING,
                    onClick = trailingAction.onClick,
                )
                .focusProperties { canFocus = false },
    ) {
      when (trailingAction.state) {
        SubmissionSeriesTrailingActionState.IDLE ->
            Icon(
                imageVector = FaMaterialSymbols.Outlined.Troubleshoot,
                contentDescription = trailingAction.contentDescription,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

        SubmissionSeriesTrailingActionState.LOADING ->
            LoadingIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.primary,
            )

        SubmissionSeriesTrailingActionState.RESOLVED ->
            Icon(
                imageVector = FaMaterialSymbols.Outlined.ArrowCircleRight,
                contentDescription = trailingAction.contentDescription,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
      }
    }
  }
}

@Composable
private fun OriginalBlockText(
    originalText: String?,
    originalHtml: String,
    shouldTrimOriginalTrailingWhitespace: Boolean,
    textStyle: TextStyle,
    textColor: Color,
    selectable: Boolean,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
  val content: @Composable () -> Unit = {
    if (originalText != null) {
      Text(
          text =
              if (shouldTrimOriginalTrailingWhitespace) {
                originalText.trimEnd()
              } else {
                originalText
              },
          style = textStyle,
          color = textColor,
          onTextLayout = onTextLayout,
      )
    } else {
      HtmlText(
          html = originalHtml,
          style = textStyle,
          color = textColor,
          trimTrailingWhitespace = shouldTrimOriginalTrailingWhitespace,
          onTextLayout = onTextLayout,
      )
    }
  }

  if (selectable) {
    SelectionContainer { content() }
  } else {
    content()
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
    translationEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    onTranslateRequested: () -> Unit = {},
) {
  val translationController =
      rememberSubmissionDescriptionTranslationState(
          descriptionHtml = sourceHtml,
          service = translationService,
      )

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    TranslatableSectionTitleRow(
        title = title,
        translating = translationController.translating,
        onTranslate = {
          if (translationEnabled) {
            translationController.translate()
            onTranslateRequested()
          }
        },
        modifier = Modifier.fillMaxWidth(),
        translationEnabled = translationEnabled,
    )

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

@Composable
private fun translationActionTint(active: Boolean): Color =
    if (active) {
      MaterialTheme.colorScheme.primary
    } else {
      MaterialTheme.colorScheme.outline
    }
