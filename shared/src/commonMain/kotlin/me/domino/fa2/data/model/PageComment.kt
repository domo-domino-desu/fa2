package me.domino.fa2.data.model

import kotlinx.serialization.Serializable

/** 页面评论（Submission / Journal 共用）。 */
@Serializable
data class PageComment(
    /** 评论 ID。 */
    val id: Long,
    /** 作者用户名。 */
    val author: String,
    /** 作者展示名。 */
    val authorDisplayName: String,
    /** 作者头像。 */
    val authorAvatarUrl: String = "",
    /** 相对时间。 */
    val timestampNatural: String,
    /** 原始时间。 */
    val timestampRaw: String?,
    /** 评论正文 HTML。 */
    val bodyHtml: String,
    /** 嵌套层级（0 为顶层）。 */
    val depth: Int = 0,
)
