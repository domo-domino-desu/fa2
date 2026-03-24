package me.domino.fa2.data.model

import kotlinx.serialization.Serializable

/**
 * Submission 详情页数据。
 */
@Serializable
data class Submission(
    /** 投稿 ID。 */
    val id: Int,
    /** 投稿详情页 URL。 */
    val submissionUrl: String,
    /** 标题。 */
    val title: String,
    /** 作者用户名。 */
    val author: String,
    /** 作者展示名。 */
    val authorDisplayName: String,
    /** 作者头像地址。 */
    val authorAvatarUrl: String = "",
    /** 时间原文（可为空）。 */
    val timestampRaw: String?,
    /** 时间自然语言（如 3 years ago）。 */
    val timestampNatural: String,
    /** 浏览量。 */
    val viewCount: Int,
    /** 评论数。 */
    val commentCount: Int,
    /** 评论列表。 */
    val comments: List<PageComment> = emptyList(),
    /** 是否允许发送评论。 */
    val commentPostingEnabled: Boolean = true,
    /** 评论发送状态提示（如禁言提示）。 */
    val commentPostingMessage: String? = null,
    /** 收藏数。 */
    val favoriteCount: Int,
    /** 当前是否已收藏。 */
    val isFavorited: Boolean = false,
    /** 收藏动作 URL（可能为空）。 */
    val favoriteActionUrl: String = "",
    /** 分级。 */
    val rating: String,
    /** 分类。 */
    val category: String,
    /** 类型。 */
    val type: String = "",
    /** 物种。 */
    val species: String,
    /** 尺寸文本。 */
    val size: String,
    /** 文件大小文本。 */
    val fileSize: String,
    /** 关键词。 */
    val keywords: List<String>,
    /** 当前用户已屏蔽标签（页面 body 的 tag blocklist）。 */
    val blockedTagNames: List<String> = emptyList(),
    /** Tag 屏蔽 nonce（用于 /route/tag_blocking）。 */
    val tagBlockNonce: String = "",
    /** 预览图 URL。 */
    val previewImageUrl: String,
    /** 原图 URL。 */
    val fullImageUrl: String,
    /** 下载 URL（可为空）。 */
    val downloadUrl: String?,
    /** 媒体宽高比。 */
    val aspectRatio: Float,
    /** 描述 HTML。 */
    val descriptionHtml: String,
)
