package me.domino.fa2.data.model

/** 搜索历史记录。 */
data class SearchHistoryRecord(
    /** 搜索关键词。 */
    val query: String,
    /** 非默认筛选条件摘要（为空表示默认条件）。 */
    val filtersSummary: String = "",
    /** 首页面搜索 URL（用于恢复完整筛选条件）。 */
    val searchUrl: String? = null,
)
