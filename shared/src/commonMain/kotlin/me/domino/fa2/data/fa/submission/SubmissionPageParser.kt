package me.domino.fa2.data.fa.submission

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import me.domino.fa2.data.fa.media.deriveAttachmentFileName
import me.domino.fa2.data.model.Submission
import me.domino.fa2.utils.parsePositiveFloat
import me.domino.fa2.utils.parseSubmissionSid
import me.domino.fa2.utils.toAbsoluteUrl

/** 解析投稿详情页面，提取完整投稿信息。 */
internal class SubmissionPageParser {
  /** 匹配分辨率字符串的正则。 */
  private val sizeRegex = Regex("""(\d+)\s*x\s*(\d+)""")
  /** 媒体信息解析器。 */
  private val mediaParser = SubmissionMediaParser()
  /** 评论列表解析器。 */
  private val commentsParser = SubmissionCommentsParser()
  /** 评论发布状态解析器。 */
  private val commentPostingParser = SubmissionCommentPostingParser()

  /** 从页面文档中解析并返回完整的投稿数据对象。 */
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

  /** 解析投稿元数据（标题、作者、时间、统计数据等）。 */
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
        size = parseOptionalInfoValue(contentStats, "Resolution"),
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

  /** 从内容统计区域解析指定标签对应的值（值缺失时抛出异常）。 */
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

  /** 从内容统计区域解析指定标签对应的可选值（不存在时返回 null）。 */
  private fun parseOptionalInfoValue(contentStats: Element, vararg targetLabels: String): String? {
    val columns = contentStats.children()
    val labelNodes = columns.getOrNull(0)?.children().orEmpty()
    val values = columns.getOrNull(1)?.children().orEmpty()
    val index =
        labelNodes.indexOfFirst { node ->
          targetLabels.any { label -> node.text().trim().equals(label, ignoreCase = true) }
        }
    if (index < 0) return null
    return values.getOrNull(index)?.text()?.trim()?.removeSuffix("px")?.takeIf { it.isNotBlank() }
  }

  /** 解析投稿标签关键字列表。 */
  private fun parseKeywords(root: Element): List<String> =
      root.select("div.submission-tags span.tags").mapNotNull { tag ->
        val structured =
            tag.selectFirst(".tag-block")?.attr("data-tag-name")?.trim()?.takeIf { it.isNotBlank() }
        structured ?: tag.selectFirst("a")?.text()?.trim()?.takeIf { it.isNotBlank() }
      }

  /** 解析被屏蔽的标签名列表。 */
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

  /** 解析收藏操作链接及当前收藏状态。 */
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

  /** 查找下载链接节点。 */
  private fun findDownloadNode(root: Element): Element? =
      root.select("a[href]").firstOrNull { node ->
        node.text().trim().equals("Download", ignoreCase = true)
      }

  /** 解析统计项的数量（去除非数字字符后转换为整数）。 */
  private fun parseStatCount(statsNode: Element, label: String): Int =
      parseStatText(statsNode, label).filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0

  /** 解析统计项的文本值。 */
  private fun parseStatText(statsNode: Element, label: String): String {
    val stat =
        statsNode.children().firstOrNull { item ->
          item.selectFirst(".highlight")?.text()?.trim()?.equals(label, ignoreCase = true) == true
        } ?: return ""
    return stat.children().firstOrNull()?.text()?.trim().orEmpty()
  }

  /** 根据分辨率字符串计算宽高比。 */
  private fun parseAspectRatio(size: String?): Float {
    val match = size?.let(sizeRegex::find) ?: return 1f
    val width = parsePositiveFloat(match.groupValues[1]) ?: return 1f
    val height = parsePositiveFloat(match.groupValues[2]) ?: return 1f
    if (height <= 0f) return 1f
    return width / height
  }
}

/** 投稿元数据中间解析结果。 */
private data class SubmissionParsedMetadata(
    /** 投稿页面规范地址。 */
    val submissionUrl: String,
    /** 投稿标题。 */
    val title: String,
    /** 作者用户名。 */
    val author: String,
    /** 作者显示名称。 */
    val authorDisplayName: String,
    /** 作者头像地址。 */
    val authorAvatarUrl: String,
    /** 原始时间戳字符串。 */
    val timestampRaw: String?,
    /** 自然语言时间描述。 */
    val timestampNatural: String,
    /** 浏览数。 */
    val viewCount: Int,
    /** 评论数。 */
    val commentCount: Int,
    /** 收藏数。 */
    val favoriteCount: Int,
    /** 当前用户是否已收藏。 */
    val isFavorited: Boolean,
    /** 收藏/取消收藏的操作地址。 */
    val favoriteActionUrl: String,
    /** 评级。 */
    val rating: String,
    /** 分类。 */
    val category: String,
    /** 子分类/主题。 */
    val type: String,
    /** 物种。 */
    val species: String,
    /** 分辨率。 */
    val size: String?,
    /** 文件大小。 */
    val fileSize: String,
    /** 关键字列表。 */
    val keywords: List<String>,
    /** 被屏蔽的标签名列表。 */
    val blockedTagNames: List<String>,
    /** 标签屏蔽 nonce。 */
    val tagBlockNonce: String,
    /** 下载链接。 */
    val downloadUrl: String?,
    /** 下载文件名。 */
    val downloadFileName: String?,
)

/** 收藏操作数据。 */
private data class SubmissionFavoriteAction(
    /** 操作地址。 */
    val actionUrl: String,
    /** 是否已收藏。 */
    val isFavorited: Boolean,
)
