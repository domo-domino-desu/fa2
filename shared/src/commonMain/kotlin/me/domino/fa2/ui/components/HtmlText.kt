package me.domino.fa2.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import be.digitalia.compose.htmlconverter.htmlToAnnotatedString
import com.fleeksoft.ksoup.Ksoup

private const val composeInlineContentTag = "androidx.compose.foundation.text.inlineContent"
private const val faInlineTokenPrefix = "__FA2_INLINE_ICONUSERNAME_"
private const val faInlineTokenSuffix = "__"
private const val fallbackAvatarAltText = "avatar"

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
  val resolvedColor =
      when {
        color.isSpecified -> color
        style.color.isSpecified -> style.color
        else -> LocalContentColor.current
      }
  val renderedStyle = if (color.isSpecified) style.copy(color = color) else style
  val processed =
      remember(html, compactMode, trimTrailingWhitespace) {
        val preprocessed = preprocessHtmlForFaInlinePlaceholders(html)
        val annotated = htmlToAnnotatedString(html = preprocessed.html, compactMode = compactMode)
        val resolved = replaceHtmlInlineTokens(annotated, preprocessed.placeholders)
        if (trimTrailingWhitespace) {
          resolved.copy(annotated = resolved.annotated.trimTrailingWhitespace())
        } else {
          resolved
        }
      }
  val inlineContent =
      rememberHtmlInlineContent(
          placeholders = processed.placeholders,
          style = renderedStyle,
          textColor = resolvedColor,
      )

  BasicText(
      text = processed.annotated,
      modifier = modifier,
      style = renderedStyle,
      maxLines = maxLines,
      overflow = overflow,
      onTextLayout = onTextLayout,
      inlineContent = inlineContent,
  )
}

internal fun preprocessHtmlForFaInlinePlaceholders(html: String): HtmlInlinePreprocessResult {
  if (!html.contains("iconusername")) {
    return HtmlInlinePreprocessResult(html = html, placeholders = emptyList())
  }

  val document = Ksoup.parseBodyFragment(html)
  val body = document.body()
  val placeholders = mutableListOf<HtmlInlinePlaceholder>()
  body.select("a.iconusername").forEachIndexed { index, anchor ->
    val image = anchor.selectFirst("img") ?: return@forEachIndexed
    val imageUrl = image.attr("src").normalizeHtmlInlineText()
    if (imageUrl.isBlank()) return@forEachIndexed

    val href = anchor.attr("href").normalizeHtmlInlineText().ifBlank { null }
    val visibleText =
        anchor.clone().apply { select("img").remove() }.text().normalizeHtmlInlineText()
    val username =
        extractInlineUsername(
            href = href,
            imageTitle = image.attr("title"),
            imageAlt = image.attr("alt"),
        )
    val displayText = visibleText.ifBlank { username }
    val kind =
        if (visibleText.isNotBlank()) {
          HtmlInlinePlaceholderKind.User
        } else {
          HtmlInlinePlaceholderKind.Avatar
        }
    val token = "$faInlineTokenPrefix$index$faInlineTokenSuffix"
    val inlineId = "fa-inline-$index"
    placeholders +=
        HtmlInlinePlaceholder(
            token = token,
            inlineId = inlineId,
            kind = kind,
            username = username.ifBlank { displayText.ifBlank { fallbackAvatarAltText } },
            displayText = displayText.ifBlank { fallbackAvatarAltText },
            imageUrl = imageUrl,
            linkUrl = href,
        )
    anchor.text(token)
  }

  if (placeholders.isEmpty()) {
    return HtmlInlinePreprocessResult(html = html, placeholders = emptyList())
  }
  return HtmlInlinePreprocessResult(html = body.html(), placeholders = placeholders)
}

internal fun replaceHtmlInlineTokens(
    annotated: AnnotatedString,
    placeholders: List<HtmlInlinePlaceholder>,
): HtmlInlineAnnotatedStringResult {
  if (placeholders.isEmpty()) {
    return HtmlInlineAnnotatedStringResult(annotated = annotated, placeholders = emptyList())
  }

  val occurrences =
      placeholders
          .mapNotNull { placeholder ->
            val start = annotated.text.indexOf(placeholder.token)
            if (start < 0) null
            else HtmlInlineOccurrence(placeholder, start, start + placeholder.token.length)
          }
          .sortedBy { it.start }
  if (occurrences.isEmpty()) {
    return HtmlInlineAnnotatedStringResult(annotated = annotated, placeholders = emptyList())
  }

  val originalText = annotated.text
  val remappedBoundaries = IntArray(originalText.length + 1)
  val textBuilder = StringBuilder(originalText.length)
  var originalIndex = 0

  occurrences.forEach { occurrence ->
    if (occurrence.start > originalIndex) {
      remapPlainSegment(
          boundaryMap = remappedBoundaries,
          originalStart = originalIndex,
          originalEnd = occurrence.start,
          newStart = textBuilder.length,
      )
      textBuilder.append(originalText, originalIndex, occurrence.start)
    }

    val replacementText = occurrence.placeholder.alternateText
    remappedBoundaries[occurrence.start] = textBuilder.length
    for (boundary in occurrence.start + 1..occurrence.end) {
      remappedBoundaries[boundary] = textBuilder.length + replacementText.length
    }
    textBuilder.append(replacementText)
    originalIndex = occurrence.end
  }

  remapPlainSegment(
      boundaryMap = remappedBoundaries,
      originalStart = originalIndex,
      originalEnd = originalText.length,
      newStart = textBuilder.length,
  )
  if (originalIndex < originalText.length) {
    textBuilder.append(originalText, originalIndex, originalText.length)
  }

  val builder = AnnotatedString.Builder(textBuilder.toString())
  annotated.spanStyles.forEach { range ->
    builder.addMappedStyle(range.item, range.start, range.end, remappedBoundaries)
  }
  annotated.paragraphStyles.forEach { range ->
    builder.addMappedParagraphStyle(range.item, range.start, range.end, remappedBoundaries)
  }
  annotated.getStringAnnotations(0, annotated.length).forEach { range ->
    builder.addMappedStringAnnotation(
        range.tag,
        range.item,
        range.start,
        range.end,
        remappedBoundaries,
    )
  }
  annotated.getLinkAnnotations(0, annotated.length).forEach { range ->
    builder.addMappedLink(range.item, range.start, range.end, remappedBoundaries)
  }
  occurrences.forEach { occurrence ->
    val mappedStart = remappedBoundaries[occurrence.start]
    val mappedEnd = remappedBoundaries[occurrence.end]
    if (mappedEnd > mappedStart) {
      builder.addStringAnnotation(
          tag = composeInlineContentTag,
          annotation = occurrence.placeholder.inlineId,
          start = mappedStart,
          end = mappedEnd,
      )
    }
  }

  return HtmlInlineAnnotatedStringResult(
      annotated = builder.toAnnotatedString(),
      placeholders = occurrences.map { it.placeholder },
  )
}

private fun remapPlainSegment(
    boundaryMap: IntArray,
    originalStart: Int,
    originalEnd: Int,
    newStart: Int,
) {
  for (boundary in originalStart..originalEnd) {
    boundaryMap[boundary] = newStart + (boundary - originalStart)
  }
}

private fun AnnotatedString.Builder.addMappedStyle(
    style: SpanStyle,
    originalStart: Int,
    originalEnd: Int,
    boundaryMap: IntArray,
) {
  val mappedStart = boundaryMap[originalStart]
  val mappedEnd = boundaryMap[originalEnd]
  if (mappedEnd > mappedStart) {
    addStyle(style = style, start = mappedStart, end = mappedEnd)
  }
}

private fun AnnotatedString.Builder.addMappedParagraphStyle(
    style: ParagraphStyle,
    originalStart: Int,
    originalEnd: Int,
    boundaryMap: IntArray,
) {
  val mappedStart = boundaryMap[originalStart]
  val mappedEnd = boundaryMap[originalEnd]
  if (mappedEnd > mappedStart) {
    addStyle(style = style, start = mappedStart, end = mappedEnd)
  }
}

private fun AnnotatedString.Builder.addMappedStringAnnotation(
    tag: String,
    value: String,
    originalStart: Int,
    originalEnd: Int,
    boundaryMap: IntArray,
) {
  if (tag == composeInlineContentTag) return
  val mappedStart = boundaryMap[originalStart]
  val mappedEnd = boundaryMap[originalEnd]
  if (mappedEnd > mappedStart) {
    addStringAnnotation(tag = tag, annotation = value, start = mappedStart, end = mappedEnd)
  }
}

private fun AnnotatedString.Builder.addMappedLink(
    link: LinkAnnotation,
    originalStart: Int,
    originalEnd: Int,
    boundaryMap: IntArray,
) {
  val mappedStart = boundaryMap[originalStart]
  val mappedEnd = boundaryMap[originalEnd]
  if (mappedEnd > mappedStart) {
    when (link) {
      is LinkAnnotation.Url -> addLink(url = link, start = mappedStart, end = mappedEnd)
      is LinkAnnotation.Clickable -> addLink(clickable = link, start = mappedStart, end = mappedEnd)
    }
  }
}

private fun extractInlineUsername(href: String?, imageTitle: String, imageAlt: String): String {
  val fromHref =
      href
          ?.substringAfter("/user/", missingDelimiterValue = "")
          ?.substringBefore('/')
          ?.substringBefore('?')
          ?.substringBefore('#')
          ?.normalizeHtmlInlineText()
          .orEmpty()
  if (fromHref.isNotBlank()) return fromHref

  val fromTitle = imageTitle.normalizeHtmlInlineText()
  if (fromTitle.isNotBlank()) return fromTitle

  return imageAlt.normalizeHtmlInlineText()
}

private fun String.normalizeHtmlInlineText(): String {
  return replace('\u00A0', ' ').trim()
}

private fun AnnotatedString.trimTrailingWhitespace(): AnnotatedString {
  val trimmedLength = text.trimEnd().length
  return if (trimmedLength == text.length) this else subSequence(0, trimmedLength)
}

@Immutable
internal data class HtmlInlinePreprocessResult(
    val html: String,
    val placeholders: List<HtmlInlinePlaceholder>,
)

@Immutable
internal data class HtmlInlineAnnotatedStringResult(
    val annotated: AnnotatedString,
    val placeholders: List<HtmlInlinePlaceholder>,
)

@Immutable
internal data class HtmlInlinePlaceholder(
    val token: String,
    val inlineId: String,
    val kind: HtmlInlinePlaceholderKind,
    val username: String,
    val displayText: String,
    val imageUrl: String,
    val linkUrl: String?,
) {
  val alternateText: String
    get() =
        when (kind) {
          HtmlInlinePlaceholderKind.User ->
              displayText.ifBlank { username }.ifBlank { fallbackAvatarAltText }
          HtmlInlinePlaceholderKind.Avatar -> username.ifBlank { fallbackAvatarAltText }
        }
}

internal enum class HtmlInlinePlaceholderKind {
  User,
  Avatar,
}

private data class HtmlInlineOccurrence(
    val placeholder: HtmlInlinePlaceholder,
    val start: Int,
    val end: Int,
)
