package me.domino.fa2.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import be.digitalia.compose.htmlconverter.htmlToAnnotatedString

/** HTML 文本渲染组件（基于 HtmlConverterCompose）。 */
@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    compactMode: Boolean = true,
    trimTrailingWhitespace: Boolean = false,
) {
  val rendered =
      remember(html, compactMode, trimTrailingWhitespace) {
        val annotated = htmlToAnnotatedString(html = html, compactMode = compactMode)
        if (trimTrailingWhitespace) {
          annotated.trimTrailingWhitespace()
        } else {
          annotated
        }
      }
  Text(
      text = rendered,
      modifier = modifier,
      style = style,
      color = color,
      maxLines = maxLines,
      overflow = overflow,
      onTextLayout = onTextLayout,
  )
}

private fun AnnotatedString.trimTrailingWhitespace(): AnnotatedString {
  val trimmedLength = text.trimEnd().length
  return if (trimmedLength == text.length) this else subSequence(0, trimmedLength)
}
