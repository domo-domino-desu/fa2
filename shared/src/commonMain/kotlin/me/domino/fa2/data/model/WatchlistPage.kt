package me.domino.fa2.data.model

import kotlinx.serialization.Serializable

/** 关注列表分页。 */
@Serializable
data class WatchlistPage(
    /** 当前页用户。 */
    val users: List<WatchlistUser>,
    /** 下一页地址。 */
    val nextPageUrl: String?,
)
