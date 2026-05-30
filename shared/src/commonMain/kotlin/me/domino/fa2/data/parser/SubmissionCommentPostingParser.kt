package me.domino.fa2.data.parser

import com.fleeksoft.ksoup.nodes.Document

/** 解析投稿页面的评论发布状态。 */
internal class SubmissionCommentPostingParser {
  /** 从页面文档中解析评论是否可发布及提示信息。 */
  fun parse(document: Document): SubmissionCommentPostingState {
    val responseText = document.selectFirst("#responsebox")?.text()?.trim().orEmpty()
    val hasCommentForm = document.selectFirst("#add_comment_form") != null

    if (!hasCommentForm && responseText.isNotBlank()) {
      return SubmissionCommentPostingState(enabled = false, message = responseText)
    }
    return SubmissionCommentPostingState(enabled = hasCommentForm)
  }
}

/** 评论发布状态数据。 */
internal data class SubmissionCommentPostingState(
    /** 是否允许发布评论。 */
    val enabled: Boolean,
    /** 评论区的提示信息。 */
    val message: String? = null,
)
