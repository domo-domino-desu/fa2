package me.domino.fa2.data.model

import kotlinx.serialization.Serializable

/** 日志分页。 */
@Serializable
data class JournalPage(
  /** 当前页日志。 */
  val journals: List<JournalSummary>,
  /** 下一页地址。 */
  val nextPageUrl: String?,
)
