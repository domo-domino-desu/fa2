package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.Ksoup
import me.domino.fa2.data.model.Submission
import me.domino.fa2.util.parsePositiveFloat
import me.domino.fa2.util.parseSubmissionSid

/** Submission 详情页解析器。 */
class SubmissionParser {
  private val sizeRegex = Regex("""(\d+)\s*x\s*(\d+)""")
  private val mediaParser = SubmissionMediaParser()
  private val metadataParser = SubmissionMetadataParser()
  private val commentsParser = SubmissionCommentsParser()
  private val commentPostingParser = SubmissionCommentPostingParser()

  /**
   * 解析 submission 详情页。
   *
   * @param html 页面 HTML。
   * @param url 页面 URL。
   */
  fun parse(html: String, url: String): Submission {
    val document = Ksoup.parse(html, url)
    val root =
        document.selectFirst("#columnpage")
            ?: throw IllegalStateException("Submission page column container missing")
    val sidebar =
        root.selectFirst("div.submission-sidebar")
            ?: throw IllegalStateException("Submission sidebar missing")
    val content =
        root.selectFirst("div.submission-content")
            ?: throw IllegalStateException("Submission content missing")

    val media = mediaParser.parse(document = document, content = content, pageUrl = url)
    val metadata =
        metadataParser.parse(
            document = document,
            sidebar = sidebar,
            content = content,
            pageUrl = url,
        )
    val comments = commentsParser.parse(document = document, pageUrl = url)
    val commentPosting = commentPostingParser.parse(document)
    val descriptionNode =
        content.selectFirst("section div.section-body div.submission-description")
            ?: throw IllegalStateException("Submission description missing")

    val sid =
        parseSubmissionSid(url)
            ?: parseSubmissionSid(metadata.submissionUrl)
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
        downloadFileName = metadata.downloadFileName,
        aspectRatio = parseAspectRatio(metadata.size),
        descriptionHtml = descriptionHtml,
    )
  }

  private fun parseAspectRatio(size: String): Float {
    val match = sizeRegex.find(size) ?: return 1f
    val width = parsePositiveFloat(match.groupValues[1]) ?: return 1f
    val height = parsePositiveFloat(match.groupValues[2]) ?: return 1f
    if (height <= 0f) return 1f
    return width / height
  }
}
