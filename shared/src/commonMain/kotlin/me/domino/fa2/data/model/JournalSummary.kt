package me.domino.fa2.data.model

import kotlinx.serialization.Serializable

/** 日志列表项。 */
@Serializable
data class JournalSummary(
    /** Journal ID。 */
    val id: Int,
    /** 标题。 */
    val title: String,
    /** 日志地址。 */
    val journalUrl: String,
    /** 时间（自然语言）。 */
    val timestampNatural: String,
    /** 时间（原始 title）。 */
    val timestampRaw: String?,
    /** 评论数。 */
    val commentCount: Int,
    /** 内容摘要。 */
    val excerpt: String,
)
