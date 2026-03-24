package me.domino.fa2.data.model

import kotlinx.serialization.Serializable

/** User 投稿类分页数据（gallery/favorites/scraps）。 */
@Serializable
data class GalleryPage(
    /** 当前页投稿。 */
    val submissions: List<SubmissionThumbnail>,
    /** 下一页地址。 */
    val nextPageUrl: String?,
    /** 文件夹分组。 */
    val folderGroups: List<GalleryFolderGroup> = emptyList(),
)
