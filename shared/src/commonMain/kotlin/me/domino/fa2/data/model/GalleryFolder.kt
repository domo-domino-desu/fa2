package me.domino.fa2.data.model

import kotlinx.serialization.Serializable

/**
 * 用户投稿分区中的单个文件夹入口。
 */
@Serializable
data class GalleryFolder(
    /** 文件夹标题。 */
    val title: String,
    /** 文件夹 URL。 */
    val url: String,
    /** 是否为当前激活文件夹。 */
    val isActive: Boolean,
)

/**
 * 用户投稿分区中的文件夹分组。
 */
@Serializable
data class GalleryFolderGroup(
    /** 分组标题（可能为空）。 */
    val title: String? = null,
    /** 分组中的文件夹列表。 */
    val folders: List<GalleryFolder> = emptyList(),
)
