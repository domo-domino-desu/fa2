package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import me.domino.fa2.util.attachmenttext.deriveAttachmentFileName
import me.domino.fa2.util.toAbsoluteUrl

internal class SubmissionMetadataParser {
  fun parse(
      document: Document,
      sidebar: Element,
      content: Element,
      pageUrl: String,
  ): SubmissionParsedMetadata {
    val title =
        content
            .selectFirst("div.submission-id-container div.submission-title h2 p")
            ?.text()
            ?.trim()
            .orEmpty()
            .ifBlank { "Untitled" }

    val authorNode =
        content.selectFirst("div.submission-id-sub-container a[href*=\"/user/\"]")
            ?: throw IllegalStateException("Submission author link missing")
    val authorHref = authorNode.attr("href")
    val author =
        authorHref.substringAfter("/user/").substringBefore('/').trim().ifBlank {
          authorNode.text().trim().lowercase()
        }
    val authorDisplayName =
        content.selectFirst("span.c-usernameBlockSimple__displayName")?.text()?.trim().takeUnless {
          it.isNullOrBlank()
        } ?: authorNode.text().trim().ifBlank { author }
    val authorAvatarUrl =
        content
            .selectFirst("div.submission-id-avatar img")
            ?.attr("src")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> toAbsoluteUrl(pageUrl, raw) }
            .orEmpty()

    val timestampNode =
        content.selectFirst("div.submission-id-sub-container strong span.popup_date")
    val timestampRaw = timestampNode?.attr("title")?.trim()?.takeIf { it.isNotBlank() }
    val timestampNatural = timestampNode?.text()?.trim().orEmpty().ifBlank { "未知时间" }

    val statsNode =
        sidebar.selectFirst("section.stats-container")
            ?: throw IllegalStateException("Submission stats section missing")

    val infoRows = sidebar.select("section.info div")
    val (category, type) = parseCategoryAndType(infoRows)
    val species = parseInfoValue(infoRows, "Species")
    val size = parseInfoValue(infoRows, "Size")
    val fileSize = parseInfoValue(infoRows, "File Size")

    val keywords =
        sidebar.select("section.tags-row span.tags").mapNotNull { tag ->
          val structured =
              tag.selectFirst(".tag-block")?.attr("data-tag-name")?.trim()?.takeIf {
                it.isNotBlank()
              }
          structured ?: tag.selectFirst("a")?.text()?.trim()?.takeIf { it.isNotBlank() }
        }
    val blockedTagNamesFromBody =
        document
            .selectFirst("body")
            ?.attr("data-tag-blocklist")
            .orEmpty()
            .split(Regex("[,\\s]+"))
            .map { token -> token.trim() }
            .filter { token -> token.isNotBlank() }
    val blockedTagNamesFromChipState =
        sidebar.select("section.tags-row span.tags .tag-block.remove-tag").mapNotNull { node ->
          node.attr("data-tag-name").trim().takeIf { value -> value.isNotBlank() }
        }
    val blockedTagNames = (blockedTagNamesFromBody + blockedTagNamesFromChipState).distinct()
    val tagBlockNonce =
        document.selectFirst("body")?.attr("data-tag-blocklist-nonce")?.trim().orEmpty()

    val downloadUrl =
        document
            .selectFirst("div.download a")
            ?.attr("href")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { href -> toAbsoluteUrl(pageUrl, href) }
    val downloadFileName =
        deriveAttachmentFileName(
            downloadUrl = downloadUrl,
            linkText = document.selectFirst("div.download a")?.text(),
        )

    val favoriteAction =
        parseFavoriteActionUrl(
            document = document,
            sidebar = sidebar,
            content = content,
            pageUrl = pageUrl,
        )

    val submissionUrl =
        if (pageUrl.contains("/view/")) {
          pageUrl
        } else {
          content.selectFirst("a[href*=\"/view/\"]")?.attr("href")?.trim()?.let { href ->
            toAbsoluteUrl(pageUrl, href)
          } ?: pageUrl
        }

    return SubmissionParsedMetadata(
        submissionUrl = submissionUrl,
        title = title,
        author = author,
        authorDisplayName = authorDisplayName,
        authorAvatarUrl = authorAvatarUrl,
        timestampRaw = timestampRaw,
        timestampNatural = timestampNatural,
        viewCount = parseCount(statsNode, "div.views span.font-large"),
        commentCount = parseCount(statsNode, "div.comments span.font-large"),
        favoriteCount = parseCount(statsNode, "div.favorites span.font-large"),
        isFavorited = favoriteAction.isFavorited,
        favoriteActionUrl = favoriteAction.actionUrl,
        rating =
            statsNode.selectFirst("div.rating span.font-large")?.text()?.trim().orEmpty().ifBlank {
              "Unknown"
            },
        category = category,
        type = type,
        species = species,
        size = size,
        fileSize = fileSize,
        keywords = keywords,
        blockedTagNames = blockedTagNames,
        tagBlockNonce = tagBlockNonce,
        downloadUrl = downloadUrl,
        downloadFileName = downloadFileName,
    )
  }

  private fun parseFavoriteActionUrl(
      document: Document,
      sidebar: Element,
      content: Element,
      pageUrl: String,
  ): SubmissionFavoriteAction {
    val actionNode =
        sidebar.selectFirst("section.buttons div.fav a[href]")
            ?: content.selectFirst(
                "div.favorite-nav a[href*='/fav/'], div.favorite-nav a[href*='/unfav/']"
            )
            ?: document.selectFirst("a[href*='/fav/'], a[href*='/unfav/']")
            ?: return SubmissionFavoriteAction(actionUrl = "", isFavorited = false)

    val href = actionNode.attr("href").trim()
    if (href.isBlank()) {
      return SubmissionFavoriteAction(actionUrl = "", isFavorited = false)
    }
    val absoluteUrl = toAbsoluteUrl(pageUrl, href)
    val label = actionNode.text().trim().replace(" ", "").lowercase()

    val isFavorited =
        absoluteUrl.contains("/unfav/") || label.startsWith("-fav") || label.contains("unfav")

    return SubmissionFavoriteAction(actionUrl = absoluteUrl, isFavorited = isFavorited)
  }

  private fun parseInfoValue(infoRows: List<Element>, label: String): String {
    val row =
        infoRows.firstOrNull { element ->
          element.selectFirst("strong")?.text()?.trim()?.removeSuffix(":") == label
        } ?: throw IllegalStateException("Submission info row missing: $label")

    val text =
        row.select("span")
            .map { element -> element.text().trim() }
            .filter { value -> value.isNotBlank() }
            .joinToString(" / ")
            .ifBlank { row.ownText().trim() }
            .ifBlank { row.text().replace(label, "").replace(":", "").trim() }

    if (text.isBlank()) {
      throw IllegalStateException("Submission info row value missing: $label")
    }
    return text.removeSuffix("px")
  }

  private fun parseCategoryAndType(infoRows: List<Element>): Pair<String, String> {
    val row =
        infoRows.firstOrNull { element ->
          element.selectFirst("strong")?.text()?.trim()?.removeSuffix(":") == "Category"
        } ?: throw IllegalStateException("Submission info row missing: Category")

    val categoryFromSpan = row.selectFirst(".category-name")?.text()?.trim().orEmpty()
    val typeFromSpan = row.selectFirst(".type-name")?.text()?.trim().orEmpty()
    if (categoryFromSpan.isNotBlank() || typeFromSpan.isNotBlank()) {
      return categoryFromSpan to typeFromSpan
    }

    val fallback = parseInfoValue(infoRows = infoRows, label = "Category")
    val splitIndex = fallback.indexOf('/')
    if (splitIndex < 0) {
      return fallback.trim() to ""
    }
    val category = fallback.substring(0, splitIndex).trim()
    val type = fallback.substring(splitIndex + 1).trim()
    return category to type
  }

  private fun parseCount(node: Element, selector: String): Int =
      node.selectFirst(selector)?.text()?.trim()?.filter { ch -> ch.isDigit() }?.toIntOrNull() ?: 0
}

internal data class SubmissionParsedMetadata(
    val submissionUrl: String,
    val title: String,
    val author: String,
    val authorDisplayName: String,
    val authorAvatarUrl: String,
    val timestampRaw: String?,
    val timestampNatural: String,
    val viewCount: Int,
    val commentCount: Int,
    val favoriteCount: Int,
    val isFavorited: Boolean,
    val favoriteActionUrl: String,
    val rating: String,
    val category: String,
    val type: String,
    val species: String,
    val size: String,
    val fileSize: String,
    val keywords: List<String>,
    val blockedTagNames: List<String>,
    val tagBlockNonce: String,
    val downloadUrl: String?,
    val downloadFileName: String?,
)

internal data class SubmissionFavoriteAction(
    val actionUrl: String,
    val isFavorited: Boolean,
)
