package me.domino.fa2.ui.components.html

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import me.domino.fa2.ui.components.media.AvatarImage

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
              val labelStyle =
                  style.copy(color = textColor, fontWeight = style.fontWeight ?: FontWeight.Medium)
              val labelWidthPx =
                  textMeasurer
                      .measure(
                          text = AnnotatedString(labelText),
                          style = labelStyle,
                          maxLines = 1,
                      )
                      .size
                      .width
                      .toFloat()
              val gapPx = with(density) { 6.dp.toPx() }
              val avatarSizePx = (lineHeightPx - with(density) { 2.dp.toPx() }).coerceAtLeast(1f)
              val widthPx =
                  (avatarSizePx + gapPx + labelWidthPx).coerceAtLeast(avatarSizePx + gapPx)
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
                    textStyle = labelStyle,
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
    textStyle: TextStyle,
) {
  Row(
      modifier = Modifier.fillMaxSize(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    AvatarImage(
        url = placeholder.imageUrl,
        displayName = text,
        username = placeholder.username,
        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
        shape = CircleShape,
        placeholderTextStyle = textStyle,
        showLoadingPlaceholder = false,
    )

    Text(
        text = text,
        modifier = Modifier.weight(1f),
        style = textStyle,
        color = textColor,
        maxLines = 1,
        softWrap = false,
    )
  }
}

@Composable
private fun HtmlInlineAvatarImage(placeholder: HtmlInlinePlaceholder) {
  AvatarImage(
      url = placeholder.imageUrl,
      displayName = placeholder.displayText,
      username = placeholder.username,
      modifier = Modifier.fillMaxSize(),
      shape = RoundedCornerShape(4.dp),
      showLoadingPlaceholder = false,
  )
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
