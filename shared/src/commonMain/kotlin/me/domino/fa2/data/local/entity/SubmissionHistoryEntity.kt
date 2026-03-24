package me.domino.fa2.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 投稿浏览历史表实体（按 sid 去重，仅保留最新一条）。 */
@Entity(tableName = "fa_submission_history")
data class SubmissionHistoryEntity(
  /** 投稿 sid。 */
  @PrimaryKey val sid: Int,
  /** 最近访问时间戳（毫秒）。 */
  val visitedAtMs: Long,
  /** 投稿链接。 */
  val submissionUrl: String,
  /** 标题。 */
  val title: String,
  /** 作者用户名。 */
  val author: String,
  /** 缩略图链接。 */
  val thumbnailUrl: String,
  /** 缩略图宽高比。 */
  val thumbnailAspectRatio: Float,
  /** 作者头像链接。 */
  val authorAvatarUrl: String,
  /** 是否命中屏蔽标签。 */
  val isBlockedByTag: Boolean,
)
