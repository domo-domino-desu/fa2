package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.nodes.Document
import me.domino.fa2.data.model.Submission
import me.domino.fa2.util.parsePositiveFloat
import me.domino.fa2.util.parseSubmissionSid

internal class LegacySubmissionParser {
  private val sizeRegex = Regex("""(\d+)\s*x\s*(\d+)""")
  private val mediaParser = SubmissionMediaParser()
  private val metadataParser = SubmissionMetadataParser()
  private val commentsParser = SubmissionCommentsParser()
  private val commentPostingParser = SubmissionCommentPostingParser()

  fun parse(document: Document, pageUrl: String): Submission {
    val root =
        document.selectFirst("#columnpage")
            ?: throw IllegalStateException("Legacy submission page column container missing")
    val sidebar =
        root.selectFirst("div.submission-sidebar")
            ?: throw IllegalStateException("Legacy submission sidebar missing")
    val content =
        root.selectFirst("div.submission-content")
            ?: throw IllegalStateException("Legacy submission content missing")

    val media = mediaParser.parse(document = document, content = content, pageUrl = pageUrl)
    val metadata =
        metadataParser.parse(
            document = document,
            sidebar = sidebar,
            content = content,
            pageUrl = pageUrl,
        )
    val comments = commentsParser.parse(document = document, pageUrl = pageUrl)
    val commentPosting = commentPostingParser.parse(document)
    val descriptionNode =
        content.selectFirst("section div.section-body div.submission-description")
            ?: throw IllegalStateException("Legacy submission description missing")

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

  private fun parseAspectRatio(size: String): Float {
    val match = sizeRegex.find(size) ?: return 1f
    val width = parsePositiveFloat(match.groupValues[1]) ?: return 1f
    val height = parsePositiveFloat(match.groupValues[2]) ?: return 1f
    if (height <= 0f) return 1f
    return width / height
  }
}
