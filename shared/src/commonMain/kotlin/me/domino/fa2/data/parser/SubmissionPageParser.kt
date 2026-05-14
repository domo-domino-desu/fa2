package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import me.domino.fa2.data.model.Submission
import me.domino.fa2.domain.attachmenttext.deriveAttachmentFileName
import me.domino.fa2.util.parsePositiveFloat
import me.domino.fa2.util.parseSubmissionSid
import me.domino.fa2.util.toAbsoluteUrl

internal class SubmissionPageParser {
  private val sizeRegex = Regex("""(\d+)\s*x\s*(\d+)""")
  private val mediaParser = SubmissionMediaParser()
  private val commentsParser = SubmissionCommentsParser()
  private val commentPostingParser = SubmissionCommentPostingParser()

  fun parse(document: Document, pageUrl: String): Submission {
    val root =
        document.selectFirst("#submission_page")
            ?: throw IllegalStateException("Submission page root missing")
    val content =
        root.selectFirst("div.submission-content")
            ?: throw IllegalStateException("Submission content missing")
    val descriptionNode =
        root.selectFirst("section.submission-description div.submission-description-text")
            ?: throw IllegalStateException("Submission description missing")

    val media = mediaParser.parse(document = document, content = content, pageUrl = pageUrl)
    val metadata = parseMetadata(document = document, root = root, pageUrl = pageUrl)
    val comments = commentsParser.parse(document = document, pageUrl = pageUrl)
    val commentPosting = commentPostingParser.parse(document)
    val sid =
        parseSubmissionSid(pageUrl)
            ?: parseSubmissionSid(metadata.submissionUrl)
            ?: throw IllegalStateException("Cannot parse submission sid from URL: $pageUrl")

    return Submission(
        id = sid,
        submissionUrl = metadata.submissionUrl,
        title = metadata.title,
        author = metadata.author,
        authorDisplayName = metadata.authorDisplayName,
        authorAvatarUrl = metadata.authorAvatarUrl,
        timestampRaw = metadata.timestampRaw,
        timestampNatural = metadata.timestampNatural,
        viewCount = metadata.viewCount,
        commentCount = metadata.commentCount,
        comments = comments,
        commentPostingEnabled = commentPosting.enabled,
        commentPostingMessage = commentPosting.message,
        favoriteCount = metadata.favoriteCount,
        isFavorited = metadata.isFavorited,
        favoriteActionUrl = metadata.favoriteActionUrl,
        rating = metadata.rating,
        category = metadata.category,
        type = metadata.type,
        species = metadata.species,
        size = metadata.size,
        fileSize = metadata.fileSize,
        keywords = metadata.keywords,
        blockedTagNames = metadata.blockedTagNames,
        tagBlockNonce = metadata.tagBlockNonce,
        previewImageUrl = media.previewImageUrl,
        fullImageUrl = media.fullImageUrl,
        downloadUrl = metadata.downloadUrl,
        downloadFileName = metadata.downloadFileName,
        aspectRatio = parseAspectRatio(metadata.size),
        descriptionHtml = descriptionNode.html(),
    )
  }

  private fun parseMetadata(
      document: Document,
      root: Element,
      pageUrl: String,
  ): SubmissionParsedMetadata {
    val description = root.selectFirst("section.submission-description")
    val title =
        description
            ?.selectFirst("div.submission-title h2")
            ?.text()
            ?.trim()
            .orEmpty()
            .ifBlank {
              document.selectFirst("meta[property='og:title']")?.attr("content")?.trim().orEmpty()
            }
            .ifBlank { "Untitled" }

    val authorNode =
        description?.selectFirst("a[href*=\"/user/\"]")
            ?: throw IllegalStateException("Submission author link missing")
    val authorHref = authorNode.attr("href")
    val author =
        authorHref.substringAfter("/user/").substringBefore('/').trim().ifBlank {
          authorNode.text().trim().lowercase()
        }
    val authorDisplayName =
        description
            .selectFirst("span.c-usernameBlockSimple__displayName")
            ?.text()
            ?.trim()
            .takeUnless { it.isNullOrBlank() } ?: authorNode.text().trim().ifBlank { author }
    val authorAvatarUrl =
        description
            .selectFirst("img.submission-user-icon")
            ?.attr("src")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> toAbsoluteUrl(pageUrl, raw) }
            .orEmpty()

    val timestampNode = description.selectFirst("span.popup_date")
    val timestampRaw = timestampNode?.attr("title")?.trim()?.takeIf { it.isNotBlank() }
    val timestampNatural = timestampNode?.text()?.trim().orEmpty().ifBlank { "未知时间" }

    val statsNode =
        root.selectFirst("div.submission-page-stats")
            ?: throw IllegalStateException("Submission stats missing")
    val contentStats =
        root.selectFirst("div.submission-content-stats")
            ?: throw IllegalStateException("Submission content stats missing")
    val downloadNode = findDownloadNode(root)
    val downloadUrl =
        downloadNode
            ?.attr("href")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { href -> toAbsoluteUrl(pageUrl, href) }
    val favoriteAction = parseFavoriteAction(root = root, pageUrl = pageUrl)

    return SubmissionParsedMetadata(
        submissionUrl =
            document
                .selectFirst("meta[property='og:url']")
                ?.attr("content")
                ?.trim()
                .orEmpty()
                .ifBlank { pageUrl },
        title = title,
        author = author,
        authorDisplayName = authorDisplayName,
        authorAvatarUrl = authorAvatarUrl,
        timestampRaw = timestampRaw,
        timestampNatural = timestampNatural,
        viewCount = parseStatCount(statsNode, "Views"),
        commentCount = parseStatCount(statsNode, "Comments"),
        favoriteCount = parseStatCount(statsNode, "Favorites"),
        isFavorited = favoriteAction.isFavorited,
        favoriteActionUrl = favoriteAction.actionUrl,
        rating = parseStatText(statsNode, "Rating").ifBlank { "Unknown" },
        category = parseInfoValue(contentStats, "Category"),
        type = parseInfoValue(contentStats, "Sub-Category", "Theme"),
        species = parseInfoValue(contentStats, "Species"),
        size = parseInfoValue(contentStats, "Resolution"),
        fileSize = parseInfoValue(contentStats, "File Size"),
        keywords = parseKeywords(root),
        blockedTagNames = parseBlockedTagNames(document, root),
        tagBlockNonce =
            document.selectFirst("body")?.attr("data-tag-blocklist-nonce")?.trim().orEmpty(),
        downloadUrl = downloadUrl,
        downloadFileName =
            deriveAttachmentFileName(downloadUrl = downloadUrl, linkText = downloadNode?.text()),
    )
  }

  private fun parseInfoValue(contentStats: Element, vararg targetLabels: String): String {
    val columns = contentStats.children()
    val labelNodes = columns.getOrNull(0)?.children().orEmpty()
    val values = columns.getOrNull(1)?.children().orEmpty()
    val index =
        labelNodes.indexOfFirst { node ->
          targetLabels.any { label -> node.text().trim().equals(label, ignoreCase = true) }
        }
    val value = values.getOrNull(index)?.text()?.trim().orEmpty()
    if (value.isBlank()) {
      throw IllegalStateException(
          "Submission info value missing: ${targetLabels.joinToString("/")}"
      )
    }
    return value.removeSuffix("px")
  }

  private fun parseKeywords(root: Element): List<String> =
      root.select("div.submission-tags span.tags").mapNotNull { tag ->
        val structured =
            tag.selectFirst(".tag-block")?.attr("data-tag-name")?.trim()?.takeIf { it.isNotBlank() }
        structured ?: tag.selectFirst("a")?.text()?.trim()?.takeIf { it.isNotBlank() }
      }

  private fun parseBlockedTagNames(document: Document, root: Element): List<String> {
    val blockedTagNamesFromBody =
        document
            .selectFirst("body")
            ?.attr("data-tag-blocklist")
            .orEmpty()
            .split(Regex("[,\\s]+"))
            .map { token -> token.trim() }
            .filter { token -> token.isNotBlank() }
    val blockedTagNamesFromChipState =
        root.select("div.submission-tags span.tags .tag-block.remove-tag").mapNotNull { node ->
          node.attr("data-tag-name").trim().takeIf { value -> value.isNotBlank() }
        }
    return (blockedTagNamesFromBody + blockedTagNamesFromChipState).distinct()
  }

  private fun parseFavoriteAction(root: Element, pageUrl: String): SubmissionFavoriteAction {
    val actionNode =
        root.selectFirst("a[href*='/fav/'], a[href*='/unfav/']")
            ?: return SubmissionFavoriteAction(actionUrl = "", isFavorited = false)
    val href = actionNode.attr("href").trim()
    if (href.isBlank()) return SubmissionFavoriteAction(actionUrl = "", isFavorited = false)

    val absoluteUrl = toAbsoluteUrl(pageUrl, href)
    val label = actionNode.text().trim().replace(" ", "").lowercase()
    return SubmissionFavoriteAction(
        actionUrl = absoluteUrl,
        isFavorited =
            absoluteUrl.contains("/unfav/") || label.startsWith("-fav") || label.contains("unfav"),
    )
  }

  private fun findDownloadNode(root: Element): Element? =
      root.select("a[href]").firstOrNull { node ->
        node.text().trim().equals("Download", ignoreCase = true)
      }

  private fun parseStatCount(statsNode: Element, label: String): Int =
      parseStatText(statsNode, label).filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0

  private fun parseStatText(statsNode: Element, label: String): String {
    val stat =
        statsNode.children().firstOrNull { item ->
          item.selectFirst(".highlight")?.text()?.trim()?.equals(label, ignoreCase = true) == true
        } ?: return ""
    return stat.children().firstOrNull()?.text()?.trim().orEmpty()
  }

  private fun parseAspectRatio(size: String): Float {
    val match = sizeRegex.find(size) ?: return 1f
    val width = parsePositiveFloat(match.groupValues[1]) ?: return 1f
    val height = parsePositiveFloat(match.groupValues[2]) ?: return 1f
    if (height <= 0f) return 1f
    return width / height
  }
}

private data class SubmissionParsedMetadata(
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

private data class SubmissionFavoriteAction(
    val actionUrl: String,
    val isFavorited: Boolean,
)
