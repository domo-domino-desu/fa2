package me.domino.fa2.data.model

import kotlinx.serialization.Serializable

/**
 * Feed 页解析结果。
 *
 * @property submissions 当前页投稿列表。
 * @property nextPageUrl 下一页地址，不存在则为 null。
 */
@Serializable
data class FeedPage(
    /** 当前页投稿卡片集合。 */
    val submissions: List<SubmissionThumbnail>,
    /** 下一页地址；为空表示已经是末页。 */
    val nextPageUrl: String?,
    /** 上一页地址。 */
    val previousPageUrl: String? = null,
    /** 最前页地址。 */
    val firstPageUrl: String? = null,
    /** 最末页地址。 */
    val lastPageUrl: String? = null,
    /** 当前页地址。 */
    val currentPageUrl: String? = null,
)
