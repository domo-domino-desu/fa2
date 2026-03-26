package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.nodes.Document

internal class SubmissionCommentPostingParser {
  fun parse(document: Document): SubmissionCommentPostingState {
    val responseText = document.selectFirst("#responsebox")?.text()?.trim().orEmpty()
    val hasCommentForm = document.selectFirst("#add_comment_form") != null

    if (!hasCommentForm && responseText.isNotBlank()) {
      return SubmissionCommentPostingState(enabled = false, message = responseText)
    }
    return SubmissionCommentPostingState(enabled = hasCommentForm)
  }
}

internal data class SubmissionCommentPostingState(
    val enabled: Boolean,
    val message: String? = null,
)
