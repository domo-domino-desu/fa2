package me.domino.fa2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

@Composable
internal fun rememberHtmlInlineContent(
    placeholders: List<HtmlInlinePlaceholder>,
    style: TextStyle,
    textColor: Color,
): Map<String, InlineTextContent> {
  if (placeholders.isEmpty()) return emptyMap()

  val localTextStyle = LocalTextStyle.current
  val density = LocalDensity.current
  val textMeasurer = rememberTextMeasurer()
  val resolvedFontSize = resolveFontSize(style = style, fallback = localTextStyle)
  val resolvedLineHeight = resolveLineHeight(style = style, fallback = localTextStyle)

  return remember(placeholders, style, textColor, localTextStyle, density, textMeasurer) {
    val lineHeightPx =
        resolvedLineHeight
            .toPxWithFontSize(density = density, fontSize = resolvedFontSize)
            .coerceAtLeast(1f)
    val avatarPlaceholderSize = with(density) { lineHeightPx.toSp() }
    placeholders.associate { placeholder ->
      val inlineTextContent =
          when (placeholder.kind) {
            HtmlInlinePlaceholderKind.User -> {
              val labelText = placeholder.displayText.ifBlank { placeholder.username }
              val labelWidthPx =
                  textMeasurer
                      .measure(
                          text = AnnotatedString(labelText),
                          style = style.copy(color = textColor),
                          maxLines = 1,
                      )
                      .size
                      .width
                      .toFloat()
              val gapPx = with(density) { 6.dp.toPx() }
              val avatarSizePx = (lineHeightPx - with(density) { 2.dp.toPx() }).coerceAtLeast(1f)
              val maxWidthPx = lineHeightPx * 10f
              val widthPx =
                  (avatarSizePx + gapPx + labelWidthPx)
                      .coerceAtMost(maxWidthPx)
                      .coerceAtLeast(avatarSizePx + gapPx)
              InlineTextContent(
                  placeholder =
                      Placeholder(
                          width = with(density) { widthPx.toSp() },
                          height = avatarPlaceholderSize,
                          placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                      ),
              ) {
                HtmlInlineUserChip(
                    placeholder = placeholder,
                    text = labelText,
                    textColor = textColor,
                    style = style,
                )
              }
            }

            HtmlInlinePlaceholderKind.Avatar ->
                InlineTextContent(
                    placeholder =
                        Placeholder(
                            width = avatarPlaceholderSize,
                            height = avatarPlaceholderSize,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                        ),
                ) {
                  HtmlInlineAvatarImage(placeholder = placeholder)
                }
          }
      placeholder.inlineId to inlineTextContent
    }
  }
}

@Composable
private fun HtmlInlineUserChip(
    placeholder: HtmlInlinePlaceholder,
    text: String,
    textColor: Color,
    style: TextStyle,
) {
  Row(
      modifier = Modifier.fillMaxSize(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(
        modifier = Modifier.fillMaxHeight().aspectRatio(1f).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      if (placeholder.imageUrl.isNotBlank()) {
        NetworkImage(
            url = placeholder.imageUrl,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            showLoadingPlaceholder = false,
        )
      } else {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              text = placeholder.username.firstOrNull()?.uppercase() ?: "?",
              style = style,
              color = textColor,
              maxLines = 1,
          )
        }
      }
    }

    Text(
        text = text,
        modifier = Modifier.weight(1f),
        style = style,
        color = textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        fontWeight = style.fontWeight ?: FontWeight.Medium,
    )
  }
}

@Composable
private fun HtmlInlineAvatarImage(placeholder: HtmlInlinePlaceholder) {
  if (placeholder.imageUrl.isNotBlank()) {
    NetworkImage(
        url = placeholder.imageUrl,
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
        contentScale = ContentScale.Crop,
        showLoadingPlaceholder = false,
    )
  } else {
    Box(
        modifier =
            Modifier.fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
    )
  }
}

private fun resolveFontSize(style: TextStyle, fallback: TextStyle): TextUnit {
  return when {
    style.fontSize.isSpecified -> style.fontSize
    fallback.fontSize.isSpecified -> fallback.fontSize
    else -> TextUnit.Unspecified
  }.takeIf { it.isSpecified } ?: 14.sp
}

private fun resolveLineHeight(style: TextStyle, fallback: TextStyle): TextUnit {
  if (style.lineHeight.isSpecified) return style.lineHeight
  if (fallback.lineHeight.isSpecified) return fallback.lineHeight
  val fontSize = resolveFontSize(style = style, fallback = fallback)
  return fontSize * 1.35f
}

private fun TextUnit.toPxWithFontSize(
    density: Density,
    fontSize: TextUnit,
): Float {
  return when (type) {
    TextUnitType.Sp -> with(density) { toPx() }
    TextUnitType.Em -> with(density) { fontSize.toPx() } * value
    else -> with(density) { fontSize.toPx() }
  }
}
