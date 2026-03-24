package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import me.domino.fa2.data.model.JournalDetail
import me.domino.fa2.data.model.PageComment
import me.domino.fa2.util.ParserUtils

/**
 * Journal 详情解析器。
 */
class JournalParser {
    private val commentWidthRegex = Regex("""width\s*:\s*([0-9.]+)%""", RegexOption.IGNORE_CASE)

    /**
     * 解析单篇日志。
     */
    fun parse(
        html: String,
        url: String,
    ): JournalDetail {
        val document = Ksoup.parse(html, url)
        ParserUtils.ensureUserPageAccessible(document)

        val bodyNode = document.selectFirst("div.journal-content")
            ?: throw IllegalStateException("Journal content missing")

        val title = document.selectFirst("#c-journalTitleTop__subject h3")
            ?.text()
            ?.trim()
            .orEmpty()
            .ifBlank { "Untitled Journal" }

        val timestampNode = document.selectFirst("#c-journalTitleTop__date .popup_date")
        val timestampRaw = timestampNode?.attr("title")?.trim()?.takeIf { it.isNotBlank() }
        val timestampNatural = timestampNode?.text()?.trim().orEmpty().ifBlank { "未知时间" }

        val rating = document.selectFirst("#c-journalTitleTop__contentRating")
            ?.text()
            ?.trim()
            .orEmpty()
            .ifBlank { "Unknown" }

        val commentCount = document.selectFirst("div.content > section div.section-footer span.font-large")
            ?.text()
            ?.filter { ch -> ch.isDigit() }
            ?.toIntOrNull()
            ?: 0
        val comments = parseComments(
            document = document,
            pageUrl = url,
        )
        val commentPosting = parseCommentPostingState(document)

        val journalUrl = document.selectFirst("meta[property='og:url']")
            ?.attr("content")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: url

        val journalId = ParserUtils.parseJournalId(journalUrl)
            ?: ParserUtils.parseJournalId(url)
            ?: throw IllegalStateException("Cannot parse journal id from url: $url")

        return JournalDetail(
            id = journalId,
            title = title,
            journalUrl = ParserUtils.toAbsoluteUrl(url, journalUrl),
            timestampNatural = timestampNatural,
            timestampRaw = timestampRaw,
            rating = rating,
            bodyHtml = bodyNode.html(),
            commentCount = commentCount,
            comments = comments,
            commentPostingEnabled = commentPosting.enabled,
            commentPostingMessage = commentPosting.message,
        )
    }

    private fun parseComments(
        document: Document,
        pageUrl: String,
    ): List<PageComment> =
        document.select("#comments-journal div.comment_container").mapNotNull { node ->
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

    private data class CommentPostingState(
        val enabled: Boolean,
        val message: String? = null,
    )
}
