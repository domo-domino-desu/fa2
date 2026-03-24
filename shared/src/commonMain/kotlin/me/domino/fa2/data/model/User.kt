package me.domino.fa2.data.model

import kotlinx.serialization.Serializable

/**
 * User 页面头部信息。
 */
@Serializable
data class User(
    /** 用户名（登录名）。 */
    val username: String,
    /** 展示名。 */
    val displayName: String,
    /** 头像地址。 */
    val avatarUrl: String,
    /** 主页头图地址。 */
    val profileBannerUrl: String = "",
    /** 用户头衔。 */
    val userTitle: String,
    /** 注册时间（自然语言）。 */
    val registeredAt: String,
    /** 当前是否正在关注该用户。 */
    val isWatching: Boolean = false,
    /** 关注/取关动作 URL（可能为空）。 */
    val watchActionUrl: String = "",
    /** 关注者数量（Watched by）。 */
    val watchedByCount: Int? = null,
    /** 已关注数量（Watching）。 */
    val watchingCount: Int? = null,
    /** 关注者列表地址。 */
    val watchedByListUrl: String = "",
    /** 已关注列表地址。 */
    val watchingListUrl: String = "",
    /** 主页简介 HTML。 */
    val profileHtml: String = "",
)
