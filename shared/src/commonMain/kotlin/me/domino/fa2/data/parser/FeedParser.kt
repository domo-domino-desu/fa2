package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import me.domino.fa2.data.model.FeedPage
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.ParserUtils

/** Feed 页面解析器。 */
class FeedParser {
  /** submissions 卡片候选选择器。 */
  private val submissionSelectors =
      listOf("#messagecenter-submissions figure", "section.gallery-section figure")

  /**
   * 解析 HTML 为 FeedPage。
   *
   * @param html 页面 HTML。
   * @param baseUrl 基准 URL。
   */
  fun parse(html: String, baseUrl: String): FeedPage {
    val document = Ksoup.parse(html, baseUrl)
    val hasTagBlocklist =
        document.selectFirst("body")?.attr("data-tag-blocklist")?.trim()?.isNotBlank() == true
    val figureNodes =
        submissionSelectors
            .asSequence()
            .map { selector -> document.select(selector) }
            .firstOrNull { nodes -> nodes.isNotEmpty() }
            .orEmpty()

    val avatarUrls = ParserUtils.parseSubmissionAvatarUrls(html)
    val submissions =
        figureNodes.mapNotNull { node ->
          parseSubmission(
              node = node,
              avatarUrls = avatarUrls,
              hasTagBlocklist = hasTagBlocklist,
          )
        }
    val nextPageUrl =
        document
            .select("a, form")
            .firstOrNull(::isNextButton)
            ?.let { node ->
              val rawTarget = node.attr("href").ifBlank { node.attr("action") }
              rawTarget.takeIf { it.isNotBlank() }
            }
            ?.let { target -> ParserUtils.toAbsoluteUrl(baseUrl, target) }

    return FeedPage(submissions = submissions, nextPageUrl = nextPageUrl)
  }

  /**
   * 解析单个投稿卡片。
   *
   * @param node figure 节点。
   */
  private fun parseSubmission(
      node: Element,
      avatarUrls: Map<Int, String>,
      hasTagBlocklist: Boolean,
  ): SubmissionThumbnail? {
    val rawSubmissionUrl = node.selectFirst("a[href*=\"/view/\"]")?.attr("href").orEmpty()
    val submissionUrl =
        ParserUtils.toAbsoluteUrl(
            baseUrl = "https://www.furaffinity.net/",
            maybeRelativeUrl = rawSubmissionUrl,
        )

    val id =
        node.attr("id").removePrefix("sid-").toIntOrNull()
            ?: ParserUtils.parseSubmissionSid(submissionUrl)
            ?: return null

    val image = node.selectFirst("a[href*='/view/'] img") ?: node.selectFirst("img") ?: return null
    val captionLinks = node.select("figcaption p a")

    val title =
        captionLinks.getOrNull(0)?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: image.attr("alt").trim().ifBlank { "Untitled #$id" }

    val author = captionLinks.getOrNull(1)?.text()?.trim()?.takeIf { it.isNotBlank() } ?: "unknown"

    val width =
        ParserUtils.parsePositiveFloat(image.attr("data-width"))
            ?: ParserUtils.parsePositiveFloat(image.attr("width"))
            ?: 1f

    val height =
        ParserUtils.parsePositiveFloat(image.attr("data-height"))
            ?: ParserUtils.parsePositiveFloat(image.attr("height"))
            ?: 1f

    val thumbnailRaw = resolveThumbnailRawUrl(image)
    val thumbnailUrl =
        ParserUtils.toAbsoluteUrl(
            baseUrl = "https://www.furaffinity.net/",
            maybeRelativeUrl = thumbnailRaw,
        )

    return SubmissionThumbnail(
        id = id,
        submissionUrl = submissionUrl.ifBlank { FaUrls.submission(id) },
        title = title,
        author = author,
        authorAvatarUrl = avatarUrls[id].orEmpty(),
        thumbnailUrl = thumbnailUrl,
        thumbnailAspectRatio = width / height,
        isBlockedByTag = parseBlockedByTag(image = image, hasTagBlocklist = hasTagBlocklist),
    )
  }

  private fun resolveThumbnailRawUrl(image: Element): String {
    val direct =
        listOf(
                "src",
                "data-src",
                "data-preview-src",
                "data-fullview-src",
                "data-lazy-src",
                "data-original",
            )
            .asSequence()
            .map { attribute -> image.attr(attribute).trim() }
            .firstOrNull { value -> value.isNotBlank() }
    if (!direct.isNullOrBlank()) {
      return direct
    }

    return extractSrcsetFirstUrl(image.attr("srcset"))
  }

  private fun extractSrcsetFirstUrl(rawSrcset: String): String =
      rawSrcset.substringBefore(',').substringBefore(' ').trim()

  private fun parseBlockedByTag(image: Element, hasTagBlocklist: Boolean): Boolean {
    val hasReason = image.attr("data-reason").trim().isNotBlank()
    val title = image.attr("title").trim().lowercase()
    val hasBlockedTitle = title.contains("blocked tags")
    val blockedByClass = hasTagBlocklist && image.hasClass("blocked-content")
    return hasReason || hasBlockedTitle || blockedByClass
  }

  /**
   * 判断节点是否表示“下一页”按钮。
   *
   * @param node 链接或表单节点。
   */
  private fun isNextButton(node: Element): Boolean {
    val label = node.text().trim().lowercase()
    val rel = node.attr("rel").trim().lowercase()
    val aria = node.attr("aria-label").trim().lowercase()
    return rel == "next" || aria.startsWith("next") || label.startsWith("next")
  }
}
