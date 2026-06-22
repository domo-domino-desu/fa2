package me.domino.fa2.data.fa.submission

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import me.domino.fa2.utils.toAbsoluteUrl

/** 解析投稿页面的媒体信息。 */
internal class SubmissionMediaParser {
  /** 从页面文档中提取预览图和完整图片地址。 */
  fun parse(document: Document, content: Element, pageUrl: String): SubmissionParsedMedia {
    val imageNode =
        content.selectFirst("div.submission-area img#submissionImg")
            ?: content.selectFirst(
                "div.submission-area img[data-fullview-src], div.submission-area img[data-preview-src], div.submission-area img[src]"
            )

    val imageSrcRaw = imageNode?.attr("src")?.trim().orEmpty()
    val previewRaw = imageNode?.attr("data-preview-src")?.trim().orEmpty().ifBlank { imageSrcRaw }
    val fullRaw =
        imageNode
            ?.attr("data-fullview-src")
            ?.trim()
            .orEmpty()
            .ifBlank { imageSrcRaw }
            .ifBlank {
              document.selectFirst("meta[property='og:image']")?.attr("content")?.trim().orEmpty()
            }

    return SubmissionParsedMedia(
        previewImageUrl = toAbsoluteUrl(pageUrl, previewRaw),
        fullImageUrl = toAbsoluteUrl(pageUrl, fullRaw),
    )
  }
}

/** 解析后的媒体地址数据。 */
internal data class SubmissionParsedMedia(
    /** 预览图地址。 */
    val previewImageUrl: String,
    /** 完整图片地址。 */
    val fullImageUrl: String,
)
