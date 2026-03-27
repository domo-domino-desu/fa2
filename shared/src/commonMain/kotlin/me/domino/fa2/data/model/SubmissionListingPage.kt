package me.domino.fa2.data.model

import kotlinx.serialization.Serializable

/** 通用投稿列表分页（Browse / Search）。 */
@Serializable
data class SubmissionListingPage(
    /** 当前页投稿。 */
    val submissions: List<SubmissionThumbnail>,
    /** 下一页地址。 */
    val nextPageUrl: String?,
    /** 当前页码（尽最大努力解析）。 */
    val currentPageNumber: Int? = null,
    /** 结果总数（仅部分页面可用）。 */
    val totalCount: Int? = null,
)
