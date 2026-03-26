package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import me.domino.fa2.util.toAbsoluteUrl

internal class SubmissionMediaParser {
  fun parse(document: Document, content: Element, pageUrl: String): SubmissionParsedMedia {
    val imageNode =
        content.selectFirst("div.submission-area img#submissionImg")
            ?: content.selectFirst(
                "div.submission-area img[data-fullview-src], div.submission-area img[data-preview-src], div.submission-area img[src]"
            )

    val imageSrcRaw = imageNode?.attr("src")?.trim().orEmpty()
    val previewRaw = imageNode?.attr("data-preview-src")?.trim().orEmpty().ifBlank { imageSrcRaw }
    val fullRaw =
        imageNode?.attr("data-fullview-src")?.trim().orEmpty().ifBlank {
          document.selectFirst("meta[property='og:image']")?.attr("content")?.trim().orEmpty()
        }

    return SubmissionParsedMedia(
        previewImageUrl = toAbsoluteUrl(pageUrl, previewRaw),
        fullImageUrl = toAbsoluteUrl(pageUrl, fullRaw),
    )
  }
}

internal data class SubmissionParsedMedia(
    val previewImageUrl: String,
    val fullImageUrl: String,
)
