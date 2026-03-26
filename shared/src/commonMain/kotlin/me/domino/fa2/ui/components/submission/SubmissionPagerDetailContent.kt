package me.domino.fa2.ui.components.submission

import androidx.compose.runtime.Composable
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.settings.BlockedSubmissionPagerMode
import me.domino.fa2.ui.pages.submission.SubmissionDetailUiState

/** 单条投稿详情内容。 */
@Composable
internal fun SubmissionDetailContent(
    /** 投稿缩略数据。 */
    item: SubmissionThumbnail,
    /** 投稿详情状态。 */
    detailState: SubmissionDetailUiState?,
    /** 重试详情回调。 */
    onRetry: () -> Unit,
    /** 打开作者回调。 */
    onOpenAuthor: (String) -> Unit,
    /** 使用关键词跳转搜索。 */
    onSearchKeyword: (String) -> Unit,
    /** 关键词长按回调。 */
    onKeywordLongPress: (String) -> Unit,
    /** 跳转 Browse 筛选回调。 */
    onOpenBrowseFilter: (category: Int, type: Int, species: Int) -> Unit,
    /** 复制投稿链接。 */
    onCopySubmissionUrl: (String) -> Unit,
    /** 打开图片放大回调。 */
    onOpenImageZoom: (String) -> Unit,
    /** 当前投稿是否命中屏蔽标签。 */
    isBlockedByTag: Boolean,
    /** 左右滑中的被屏蔽投稿策略。 */
    blockedSubmissionMode: BlockedSubmissionPagerMode,
    /** 当前投稿是否已在详情页解锁显示。 */
    isBlockedMediaRevealed: Boolean,
    /** 解锁当前投稿媒体显示。 */
    onRevealBlockedMedia: () -> Unit,
    /** 加载附件文本。 */
    onLoadAttachmentText: () -> Unit,
    /** 触发描述翻译。 */
    onTranslateDescription: () -> Unit,
    /** 切换描述重新换行。 */
    onWrapDescriptionText: () -> Unit,
    /** 触发附件翻译。 */
    onTranslateAttachment: () -> Unit,
    /** 切换附件重新换行。 */
    onWrapAttachmentText: () -> Unit,
    /** 请求 pager 容器重新获取焦点。 */
    requestPagerFocus: () -> Unit,
) {
  when (val state = detailState) {
    null,
    SubmissionDetailUiState.Loading ->
        SubmissionDetailLoadingContent(
            item = item,
            isBlockedByTag = isBlockedByTag,
            blockedSubmissionMode = blockedSubmissionMode,
            isBlockedMediaRevealed = isBlockedMediaRevealed,
        )

    is SubmissionDetailUiState.Error -> SubmissionDetailErrorContent(state.message, onRetry)
    is SubmissionDetailUiState.Success -> {
      SubmissionDetailSuccessContent(
          item = item,
          detail = state.detail,
          blockedKeywords = state.blockedKeywords,
          favoriteErrorMessage = state.favoriteErrorMessage,
          onOpenAuthor = onOpenAuthor,
          onSearchKeyword = onSearchKeyword,
          onKeywordLongPress = onKeywordLongPress,
          onOpenBrowseFilter = onOpenBrowseFilter,
          onCopySubmissionUrl = onCopySubmissionUrl,
          onOpenImageZoom = onOpenImageZoom,
          isBlockedByTag = isBlockedByTag,
          blockedSubmissionMode = blockedSubmissionMode,
          isBlockedMediaRevealed = isBlockedMediaRevealed,
          onRevealBlockedMedia = onRevealBlockedMedia,
          descriptionTranslationState = state.descriptionTranslationState,
          onTranslateDescription = onTranslateDescription,
          onWrapDescriptionText = onWrapDescriptionText,
          attachmentTextState = state.attachmentTextState,
          attachmentTranslationState = state.attachmentTranslationState,
          onLoadAttachmentText = onLoadAttachmentText,
          onTranslateAttachment = onTranslateAttachment,
          onWrapAttachmentText = onWrapAttachmentText,
          requestPagerFocus = requestPagerFocus,
      )
    }
  }
}
