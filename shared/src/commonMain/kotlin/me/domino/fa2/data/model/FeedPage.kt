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
)
