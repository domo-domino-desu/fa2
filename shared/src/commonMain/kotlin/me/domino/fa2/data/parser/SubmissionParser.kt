package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import me.domino.fa2.data.model.PageComment
import me.domino.fa2.data.model.Submission
import me.domino.fa2.util.ParserUtils

/**
 * Submission 详情页解析器。
 */
class SubmissionParser {
    private val sizeRegex = Regex("""(\d+)\s*x\s*(\d+)""")
    private val commentWidthRegex = Regex("""width\s*:\s*([0-9.]+)%""", RegexOption.IGNORE_CASE)

    /**
     * 解析 submission 详情页。
     * @param html 页面 HTML。
     * @param url 页面 URL。
     */
    fun parse(
        html: String,
        url: String,
    ): Submission {
        val document = Ksoup.parse(html, url)
        val root = document.selectFirst("#columnpage")
            ?: throw IllegalStateException("Submission page column container missing")
        val sidebar = root.selectFirst("div.submission-sidebar")
            ?: throw IllegalStateException("Submission sidebar missing")
        val content = root.selectFirst("div.submission-content")
            ?: throw IllegalStateException("Submission content missing")

        val media = parseMedia(content = content, pageUrl = url)
        val metadata = parseMetadata(
            document = document,
            sidebar = sidebar,
            content = content,
            pageUrl = url,
        )
        val comments = parseComments(document = document, pageUrl = url)
        val commentPosting = parseCommentPostingState(document)
        val descriptionNode = content.selectFirst("section div.section-body div.submission-description")
            ?: throw IllegalStateException("Submission description missing")

        val sid = ParserUtils.parseSubmissionSid(url)
            ?: ParserUtils.parseSubmissionSid(metadata.submissionUrl)
            ?: throw IllegalStateException("Cannot parse submission sid from URL: $url")
        val descriptionHtml = descriptionNode.html()

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
            aspectRatio = parseAspectRatio(metadata.size),
            descriptionHtml = descriptionHtml,
        )
    }

    private fun parseComments(
        document: Document,
        pageUrl: String,
    ): List<PageComment> =
        document.select("#comments-submission div.comment_container").mapNotNull { node ->
            parseCommentNode(
                commentNode = node,
                pageUrl = pageUrl,
            )
        }

    private fun parseCommentNode(
        commentNode: Element,
        pageUrl: String,
    ): PageComment? {
        val commentId = commentNode.selectFirst("a.comment_anchor")
            ?.id()
            ?.substringAfter("cid:")
            ?.trim()
            ?.toLongOrNull()
            ?: return null

        val profileLink = commentNode.selectFirst("comment-username a[href*='/user/']")
        val authorFromHref = profileLink?.attr("href")
            ?.substringAfter("/user/")
            ?.substringBefore('/')
            ?.trim()
            .orEmpty()
        val authorFromLabel = commentNode.selectFirst(".c-usernameBlock__userName")
            ?.text()
            ?.trim()
            ?.replace("~", "")
            ?.replace("@", "")
            .orEmpty()
        val author = authorFromHref.ifBlank { authorFromLabel }.ifBlank { "unknown" }

        val displayName = commentNode.selectFirst(".c-usernameBlock__displayName")
            ?.text()
            ?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?: author

        val avatarUrl = commentNode.selectFirst("img.comment_useravatar")
            ?.attr("src")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> ParserUtils.toAbsoluteUrl(pageUrl, raw) }
            .orEmpty()

        val timestampNode = commentNode.selectFirst("comment-date .popup_date")
        val timestampRaw = timestampNode?.attr("title")?.trim()?.takeIf { it.isNotBlank() }
        val timestampNatural = timestampNode?.text()?.trim().orEmpty().ifBlank { "未知时间" }

        val bodyHtml = commentNode.selectFirst("comment-user-text .user-submitted-links")
            ?.html()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: commentNode.selectFirst("comment-user-text")
                ?.html()
                ?.trim()
                .orEmpty()

        val widthPercent = commentWidthRegex.find(commentNode.attr("style"))
            ?.groupValues
            ?.getOrNull(1)
            ?.toFloatOrNull()
        val depth = if (widthPercent == null) 0 else ((100f - widthPercent) / 3f).toInt().coerceAtLeast(0)

        return PageComment(
            id = commentId,
            author = author,
            authorDisplayName = displayName,
            authorAvatarUrl = avatarUrl,
            timestampNatural = timestampNatural,
            timestampRaw = timestampRaw,
            bodyHtml = bodyHtml,
            depth = depth,
        )
    }

    private fun parseCommentPostingState(document: Document): CommentPostingState {
        val responseText = document.selectFirst("#responsebox")
            ?.text()
            ?.trim()
            .orEmpty()
        val hasCommentForm = document.selectFirst("#add_comment_form") != null

        if (!hasCommentForm && responseText.isNotBlank()) {
            return CommentPostingState(
                enabled = false,
                message = responseText,
            )
        }
        return CommentPostingState(enabled = hasCommentForm)
    }

    private fun parseMedia(
        content: Element,
        pageUrl: String,
    ): ParsedMedia {
        val imageNode = content.selectFirst("div.submission-area img#submissionImg")
            ?: throw IllegalStateException("Submission media image missing")
        val previewRaw = imageNode.attr("data-preview-src")
            .ifBlank { imageNode.attr("src") }
            .trim()
        val fullRaw = imageNode.attr("data-fullview-src")
            .ifBlank { imageNode.attr("src") }
            .trim()
        if (previewRaw.isBlank() || fullRaw.isBlank()) {
            throw IllegalStateException("Submission media URLs missing")
        }
        return ParsedMedia(
            previewImageUrl = ParserUtils.toAbsoluteUrl(pageUrl, previewRaw),
            fullImageUrl = ParserUtils.toAbsoluteUrl(pageUrl, fullRaw),
        )
    }

    private fun parseMetadata(
        document: Document,
        sidebar: Element,
        content: Element,
        pageUrl: String,
    ): ParsedMetadata {
        val title = content.selectFirst("div.submission-id-container div.submission-title h2 p")
            ?.text()
            ?.trim()
            .orEmpty()
            .ifBlank { "Untitled" }

        val authorNode = content.selectFirst("div.submission-id-sub-container a[href*=\"/user/\"]")
            ?: throw IllegalStateException("Submission author link missing")
        val authorHref = authorNode.attr("href")
        val author = authorHref.substringAfter("/user/").substringBefore('/').trim()
            .ifBlank { authorNode.text().trim().lowercase() }
        val authorDisplayName = content.selectFirst("span.c-usernameBlockSimple__displayName")
            ?.text()
            ?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?: authorNode.text().trim().ifBlank { author }
        val authorAvatarUrl = content.selectFirst("div.submission-id-avatar img")
            ?.attr("src")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> ParserUtils.toAbsoluteUrl(pageUrl, raw) }
            .orEmpty()

        val timestampNode = content.selectFirst("div.submission-id-sub-container strong span.popup_date")
        val timestampRaw = timestampNode?.attr("title")?.trim()?.takeIf { it.isNotBlank() }
        val timestampNatural = timestampNode?.text()?.trim().orEmpty().ifBlank { "未知时间" }

        val statsNode = sidebar.selectFirst("section.stats-container")
            ?: throw IllegalStateException("Submission stats section missing")

        val infoRows = sidebar.select("section.info div")
        val (category, type) = parseCategoryAndType(infoRows)
        val species = parseInfoValue(infoRows, "Species")
        val size = parseInfoValue(infoRows, "Size")
        val fileSize = parseInfoValue(infoRows, "File Size")

        val keywords = sidebar.select("section.tags-row span.tags").mapNotNull { tag ->
            val structured = tag.selectFirst(".tag-block")
                ?.attr("data-tag-name")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            structured ?: tag.selectFirst("a")?.text()?.trim()?.takeIf { it.isNotBlank() }
        }
        val blockedTagNamesFromBody = document.selectFirst("body")
            ?.attr("data-tag-blocklist")
            .orEmpty()
            .split(Regex("[,\\s]+"))
            .map { token -> token.trim() }
            .filter { token -> token.isNotBlank() }
        val blockedTagNamesFromChipState = sidebar.select("section.tags-row span.tags .tag-block.remove-tag")
            .mapNotNull { node ->
                node.attr("data-tag-name")
                    .trim()
                    .takeIf { value -> value.isNotBlank() }
            }
        val blockedTagNames = (blockedTagNamesFromBody + blockedTagNamesFromChipState)
            .distinct()
        val tagBlockNonce = document.selectFirst("body")
            ?.attr("data-tag-blocklist-nonce")
            ?.trim()
            .orEmpty()

        val downloadUrl = document.selectFirst("div.download a")
            ?.attr("href")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { href -> ParserUtils.toAbsoluteUrl(pageUrl, href) }

        val favoriteAction = parseFavoriteActionUrl(
            document = document,
            sidebar = sidebar,
            content = content,
            pageUrl = pageUrl,
        )

        val submissionUrl = if (pageUrl.contains("/view/")) {
            pageUrl
        } else {
            content.selectFirst("a[href*=\"/view/\"]")
                ?.attr("href")
                ?.trim()
                ?.let { href -> ParserUtils.toAbsoluteUrl(pageUrl, href) }
                ?: pageUrl
        }

        return ParsedMetadata(
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
            rating = statsNode.selectFirst("div.rating span.font-large")
                ?.text()
                ?.trim()
                .orEmpty()
                .ifBlank { "Unknown" },
            category = category,
            type = type,
            species = species,
            size = size,
            fileSize = fileSize,
            keywords = keywords,
            blockedTagNames = blockedTagNames,
            tagBlockNonce = tagBlockNonce,
            downloadUrl = downloadUrl,
        )
    }

    private fun parseFavoriteActionUrl(
        document: Document,
        sidebar: Element,
        content: Element,
        pageUrl: String,
    ): FavoriteAction {
        val actionNode = sidebar.selectFirst("section.buttons div.fav a[href]")
            ?: content.selectFirst("div.favorite-nav a[href*='/fav/'], div.favorite-nav a[href*='/unfav/']")
            ?: document.selectFirst("a[href*='/fav/'], a[href*='/unfav/']")
            ?: return FavoriteAction(
                actionUrl = "",
                isFavorited = false,
            )

        val href = actionNode.attr("href").trim()
        if (href.isBlank()) {
            return FavoriteAction(
                actionUrl = "",
                isFavorited = false,
            )
        }
        val absoluteUrl = ParserUtils.toAbsoluteUrl(pageUrl, href)
        val label = actionNode.text()
            .trim()
            .replace(" ", "")
            .lowercase()

        val isFavorited = absoluteUrl.contains("/unfav/") ||
                label.startsWith("-fav") ||
                label.contains("unfav")

        return FavoriteAction(
            actionUrl = absoluteUrl,
            isFavorited = isFavorited,
        )
    }

    private fun parseInfoValue(
        infoRows: List<Element>,
        label: String,
    ): String {
        val row = infoRows.firstOrNull { element ->
            element.selectFirst("strong")?.text()?.trim()?.removeSuffix(":") == label
        } ?: throw IllegalStateException("Submission info row missing: $label")

        val text = row.select("span")
            .map { element -> element.text().trim() }
            .filter { value -> value.isNotBlank() }
            .joinToString(" / ")
            .ifBlank { row.ownText().trim() }
            .ifBlank {
                row.text()
                    .replace(label, "")
                    .replace(":", "")
                    .trim()
            }

        if (text.isBlank()) {
            throw IllegalStateException("Submission info row value missing: $label")
        }
        return text.removeSuffix("px")
    }

    private fun parseCategoryAndType(infoRows: List<Element>): Pair<String, String> {
        val row = infoRows.firstOrNull { element ->
            element.selectFirst("strong")?.text()?.trim()?.removeSuffix(":") == "Category"
        } ?: throw IllegalStateException("Submission info row missing: Category")

        val categoryFromSpan = row.selectFirst(".category-name")
            ?.text()
            ?.trim()
            .orEmpty()
        val typeFromSpan = row.selectFirst(".type-name")
            ?.text()
            ?.trim()
            .orEmpty()
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

    private fun parseCount(
        node: Element,
        selector: String,
    ): Int =
        node.selectFirst(selector)
            ?.text()
            ?.trim()
            ?.filter { ch -> ch.isDigit() }
            ?.toIntOrNull()
            ?: 0

    private fun parseAspectRatio(size: String): Float {
        val match = sizeRegex.find(size) ?: return 1f
        val width = ParserUtils.parsePositiveFloat(match.groupValues[1]) ?: return 1f
        val height = ParserUtils.parsePositiveFloat(match.groupValues[2]) ?: return 1f
        if (height <= 0f) return 1f
        return width / height
    }

    private data class ParsedMedia(
        val previewImageUrl: String,
        val fullImageUrl: String,
    )

    private data class ParsedMetadata(
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
    )

    private data class FavoriteAction(
        val actionUrl: String,
        val isFavorited: Boolean,
    )

    private data class CommentPostingState(
        val enabled: Boolean,
        val message: String? = null,
    )
}
