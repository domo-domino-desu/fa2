package me.domino.fa2.data.model

import kotlinx.serialization.Serializable

/** 单篇日志详情。 */
@Serializable
data class JournalDetail(
  /** Journal ID。 */
  val id: Int,
  /** 标题。 */
  val title: String,
  /** 地址。 */
  val journalUrl: String,
  /** 时间（自然语言）。 */
  val timestampNatural: String,
  /** 时间（原始 title）。 */
  val timestampRaw: String?,
  /** 内容评级。 */
  val rating: String,
  /** 正文 HTML。 */
  val bodyHtml: String,
  /** 评论数。 */
  val commentCount: Int,
  /** 评论列表。 */
  val comments: List<PageComment> = emptyList(),
  /** 是否允许发送评论。 */
  val commentPostingEnabled: Boolean = true,
  /** 评论发送状态提示（如禁言提示）。 */
  val commentPostingMessage: String? = null,
)
