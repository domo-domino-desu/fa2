package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import me.domino.fa2.data.model.FeedPage
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.TagBlockSettings
import me.domino.fa2.util.isBlockedByTagSettings
import me.domino.fa2.util.parseImageTags
import me.domino.fa2.util.parsePositiveFloat
import me.domino.fa2.util.parseSubmissionAvatarUrls
import me.domino.fa2.util.parseSubmissionSid
import me.domino.fa2.util.parseTagBlockSettings
import me.domino.fa2.util.toAbsoluteUrl

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
    val tagBlockSettings = parseTagBlockSettings(document)
    val figureNodes =
        submissionSelectors
            .asSequence()
            .map { selector -> document.select(selector) }
            .firstOrNull { nodes -> nodes.isNotEmpty() }
            .orEmpty()

    val avatarUrls = parseSubmissionAvatarUrls(html)
    val submissions =
        figureNodes.mapNotNull { node ->
          parseSubmission(
              node = node,
              avatarUrls = avatarUrls,
              tagBlockSettings = tagBlockSettings,
          )
        }
    val currentPageUrl = normalizeCurrentPageUrl(baseUrl)
    val nextPageUrl =
        resolveNavigationTarget(document = document, baseUrl = baseUrl, type = NavType.NEXT)
    val previousPageUrl =
        resolveNavigationTarget(document = document, baseUrl = baseUrl, type = NavType.PREVIOUS)
    val firstPageUrl =
        resolveNavigationTarget(document = document, baseUrl = baseUrl, type = NavType.FIRST)
    val lastPageUrl =
        resolveNavigationTarget(document = document, baseUrl = baseUrl, type = NavType.LAST)

    return FeedPage(
        submissions = submissions,
        nextPageUrl = nextPageUrl,
        previousPageUrl = previousPageUrl,
        firstPageUrl = firstPageUrl,
        lastPageUrl = lastPageUrl,
        currentPageUrl = currentPageUrl,
    )
  }

  /**
   * 解析单个投稿卡片。
   *
   * @param node figure 节点。
   */
  private fun parseSubmission(
      node: Element,
      avatarUrls: Map<Int, String>,
      tagBlockSettings: TagBlockSettings,
  ): SubmissionThumbnail? {
    val rawSubmissionUrl = node.selectFirst("a[href*=\"/view/\"]")?.attr("href").orEmpty()
    val submissionUrl =
        toAbsoluteUrl(
            baseUrl = "https://www.furaffinity.net/",
            maybeRelativeUrl = rawSubmissionUrl,
        )

    val id =
        node.attr("id").removePrefix("sid-").toIntOrNull()
            ?: parseSubmissionSid(submissionUrl)
            ?: return null

    val image =
        node.selectFirst("a[href*='/view/'] > img")
            ?: node.selectFirst("a[href*='/view/'] img")
            ?: return null
    val captionLinks = node.select("figcaption p a")

    val title =
        captionLinks.getOrNull(0)?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: image.attr("alt").trim().ifBlank { "Untitled #$id" }

    val author = captionLinks.getOrNull(1)?.text()?.trim()?.takeIf { it.isNotBlank() } ?: "unknown"

    val width =
        parsePositiveFloat(image.attr("data-width"))
            ?: parsePositiveFloat(image.attr("width"))
            ?: 1f

    val height =
        parsePositiveFloat(image.attr("data-height"))
            ?: parsePositiveFloat(image.attr("height"))
            ?: 1f

    val thumbnailRaw = resolveThumbnailRawUrl(image)
    val thumbnailUrl =
        toAbsoluteUrl(
            baseUrl = "https://www.furaffinity.net/",
            maybeRelativeUrl = thumbnailRaw,
        )
    val resolvedSubmissionUrl = submissionUrl.ifBlank { FaUrls.submission(id) }
    val imageTags = parseImageTags(image)
    val categoryTag = imageTags.sorted().firstOrNull { tag -> tag.startsWith("c_") }.orEmpty()
    val isBlockedByTag =
        isBlockedByTagSettings(imageTags = imageTags, tagBlockSettings = tagBlockSettings)

    return SubmissionThumbnail(
        id = id,
        submissionUrl = resolvedSubmissionUrl,
        title = title,
        author = author,
        authorAvatarUrl = avatarUrls[id].orEmpty(),
        thumbnailUrl = thumbnailUrl,
        thumbnailAspectRatio = width / height,
        categoryTag = categoryTag,
        isBlockedByTag = isBlockedByTag,
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

  private fun resolveNavigationTarget(
      document: com.fleeksoft.ksoup.nodes.Document,
      baseUrl: String,
      type: NavType,
  ): String? =
      document
          .select("a, form")
          .firstOrNull { node -> matchesNavigationType(node = node, type = type) }
          ?.let { node ->
            val rawTarget = node.attr("href").ifBlank { node.attr("action") }.trim()
            rawTarget.takeIf { it.isNotBlank() }?.let { target -> toAbsoluteUrl(baseUrl, target) }
          }

  private fun matchesNavigationType(node: Element, type: NavType): Boolean {
    val label = node.text().trim().lowercase()
    val rel = node.attr("rel").trim().lowercase()
    val aria = node.attr("aria-label").trim().lowercase()
    return when (type) {
      NavType.PREVIOUS -> rel == "prev" || aria.startsWith("prev") || label.startsWith("prev")
      NavType.NEXT -> rel == "next" || aria.startsWith("next") || label.startsWith("next")
      NavType.FIRST -> aria.startsWith("newest") || label.startsWith("newest")
      NavType.LAST -> aria.startsWith("oldest") || label.startsWith("oldest")
    }
  }

  private fun normalizeCurrentPageUrl(baseUrl: String): String = toAbsoluteUrl(baseUrl, baseUrl)

  private enum class NavType {
    PREVIOUS,
    NEXT,
    FIRST,
    LAST,
  }
}
