package me.domino.fa2.data.model

import kotlinx.serialization.Serializable

/** User 投稿类分页数据（gallery/favorites/scraps）。 */
@Serializable
data class GalleryPage(
    /** 当前页投稿。 */
    val submissions: List<SubmissionThumbnail>,
    /** 下一页地址。 */
    val nextPageUrl: String?,
    /** 当前页码（尽最大努力解析）。 */
    val currentPageNumber: Int? = null,
    /** 文件夹分组。 */
    val folderGroups: List<GalleryFolderGroup> = emptyList(),
)
