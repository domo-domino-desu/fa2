package me.domino.fa2.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 搜索历史表实体（按规范化查询去重，仅保留最新一条）。 */
@Entity(tableName = "fa_search_history")
data class SearchHistoryEntity(
  /** 规范化查询键（trim + lowercase）。 */
  @PrimaryKey val queryKey: String,
  /** 原始展示查询词。 */
  val query: String,
  /** 最近访问时间戳（毫秒）。 */
  val visitedAtMs: Long,
)
