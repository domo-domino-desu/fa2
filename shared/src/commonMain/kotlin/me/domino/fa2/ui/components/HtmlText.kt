package me.domino.fa2.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    compactMode: Boolean = true,
) {
  val rendered =
      remember(html, compactMode) { htmlToAnnotatedString(html = html, compactMode = compactMode) }
  Text(
      text = rendered,
      modifier = modifier,
      style = style,
      color = color,
      maxLines = maxLines,
      overflow = overflow,
  )
}
