package me.domino.fa2.data.model

import kotlinx.serialization.Serializable

/**
 * Feed 中单个投稿卡片的最小信息。
 *
 * @property id 投稿 ID。
 * @property title 标题。
 * @property author 作者名。
 * @property authorAvatarUrl 作者头像地址。
 * @property thumbnailUrl 缩略图地址。
 * @property thumbnailAspectRatio 缩略图宽高比。
 * @property isBlockedByTag 是否命中站内 tag 屏蔽。
 */
@Serializable
data class SubmissionThumbnail(
  /** 投稿唯一 ID。 */
  val id: Int,
  /** 投稿详情页 URL。 */
  val submissionUrl: String = "",
  /** 投稿标题。 */
  val title: String,
  /** 投稿作者名。 */
  val author: String,
  /** 作者头像地址。 */
  val authorAvatarUrl: String = "",
  /** 缩略图绝对地址。 */
  val thumbnailUrl: String,
  /** 缩略图宽高比（宽 / 高）。 */
  val thumbnailAspectRatio: Float,
  /** 是否命中 tag 屏蔽。 */
  val isBlockedByTag: Boolean = false,
)
