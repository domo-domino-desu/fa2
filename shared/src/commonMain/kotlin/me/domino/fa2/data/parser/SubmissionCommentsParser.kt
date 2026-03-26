package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import me.domino.fa2.data.model.PageComment
import me.domino.fa2.util.toAbsoluteUrl

internal class SubmissionCommentsParser {
  private val commentWidthRegex = Regex("""width\s*:\s*([0-9.]+)%""", RegexOption.IGNORE_CASE)

  fun parse(document: Document, pageUrl: String): List<PageComment> =
      document.select("#comments-submission div.comment_container").mapNotNull { node ->
        parseCommentNode(commentNode = node, pageUrl = pageUrl)
      }

  private fun parseCommentNode(commentNode: Element, pageUrl: String): PageComment? {
    val commentId =
        commentNode
            .selectFirst("a.comment_anchor")
            ?.id()
            ?.substringAfter("cid:")
            ?.trim()
            ?.toLongOrNull() ?: return null

    val profileLink = commentNode.selectFirst("comment-username a[href*='/user/']")
    val authorFromHref =
        profileLink?.attr("href")?.substringAfter("/user/")?.substringBefore('/')?.trim().orEmpty()
    val authorFromLabel =
        commentNode
            .selectFirst(".c-usernameBlock__userName")
            ?.text()
            ?.trim()
            ?.replace("~", "")
            ?.replace("@", "")
            .orEmpty()
    val author = authorFromHref.ifBlank { authorFromLabel }.ifBlank { "unknown" }

    val displayName =
        commentNode.selectFirst(".c-usernameBlock__displayName")?.text()?.trim().takeUnless {
          it.isNullOrBlank()
        } ?: author

    val avatarUrl =
        commentNode
            .selectFirst("img.comment_useravatar")
            ?.attr("src")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> toAbsoluteUrl(pageUrl, raw) }
            .orEmpty()

    val timestampNode = commentNode.selectFirst("comment-date .popup_date")
    val timestampRaw = timestampNode?.attr("title")?.trim()?.takeIf { it.isNotBlank() }
    val timestampNatural = timestampNode?.text()?.trim().orEmpty().ifBlank { "未知时间" }

    val bodyHtml =
        commentNode.selectFirst("comment-user-text .user-submitted-links")?.html()?.trim()?.takeIf {
          it.isNotBlank()
        } ?: commentNode.selectFirst("comment-user-text")?.html()?.trim().orEmpty()

    val widthPercent =
        commentWidthRegex
            .find(commentNode.attr("style"))
            ?.groupValues
            ?.getOrNull(1)
            ?.toFloatOrNull()
    val depth =
        if (widthPercent == null) 0 else ((100f - widthPercent) / 3f).toInt().coerceAtLeast(0)

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
}
