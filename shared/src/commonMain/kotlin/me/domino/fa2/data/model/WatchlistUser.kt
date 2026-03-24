package me.domino.fa2.data.model

import kotlinx.serialization.Serializable

/** 关注列表用户项。 */
@Serializable
data class WatchlistUser(
  /** 用户名（登录名）。 */
  val username: String,
  /** 展示名。 */
  val displayName: String,
  /** 用户主页地址。 */
  val profileUrl: String,
)
