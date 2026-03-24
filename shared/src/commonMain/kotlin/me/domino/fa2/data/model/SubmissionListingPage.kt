package me.domino.fa2.data.model

import kotlinx.serialization.Serializable

/** 通用投稿列表分页（Browse / Search）。 */
@Serializable
data class SubmissionListingPage(
    /** 当前页投稿。 */
    val submissions: List<SubmissionThumbnail>,
    /** 下一页地址。 */
    val nextPageUrl: String?,
)
