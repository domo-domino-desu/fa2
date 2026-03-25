package me.domino.fa2.ui.components.submission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.settings.BlockedSubmissionPagerMode
import me.domino.fa2.data.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.ui.components.platform.PlatformBackHandler
import me.domino.fa2.ui.components.platform.PlatformVerticalScrollbar
import me.domino.fa2.ui.pages.submission.SubmissionDetailUiState

/** 投稿详情左右滑浏览容器。 */
@Composable
fun SubmissionPager(
    /** 当前可浏览投稿列表。 */
    submissions: List<SubmissionThumbnail>,
    /** 各投稿详情状态。 */
    detailBySid: Map<Int, SubmissionDetailUiState>,
    /** 初始页索引。 */
    initialIndex: Int,
    /** 重试当前页详情回调。 */
    onRetryCurrentDetail: () -> Unit,
    /** 分页滚动位置变更回调。 */
    onPageChanged: (Int) -> Unit,
    /** 打开作者主页回调。 */
    onOpenAuthor: (String) -> Unit,
    /** 使用关键词跳转搜索。 */
    onSearchKeyword: (String) -> Unit,
    /** 关键词长按回调。 */
    onKeywordLongPress: (String) -> Unit,
    /** 跳转 Browse 筛选回调。 */
    onOpenBrowseFilter: (category: Int, type: Int, species: Int) -> Unit,
    /** 复制投稿链接。 */
    onCopySubmissionUrl: (String) -> Unit,
    /** 描述翻译服务。 */
    descriptionTranslationService: SubmissionDescriptionTranslationService,
    /** 请求 pager 容器重新获取焦点。 */
    requestPagerFocus: () -> Unit,
    /** 原图缩放遮罩显隐回调。 */
    onZoomOverlayVisibilityChanged: (Boolean) -> Unit,
    /** 当前页回顶触发信号。 */
    scrollCurrentPageToTopSignal: Int,
    /** 左右滑中的被屏蔽投稿策略。 */
    blockedSubmissionMode: BlockedSubmissionPagerMode,
) {
  if (submissions.isEmpty()) {
    Text(text = "当前没有可浏览的投稿。", modifier = Modifier.padding(16.dp))
    return
  }

  val safeInitialIndex = initialIndex.coerceIn(0, submissions.lastIndex)
  val pagerState =
      rememberPagerState(initialPage = safeInitialIndex, pageCount = { submissions.size })

  LaunchedEffect(safeInitialIndex, submissions.size) {
    if (
        pagerState.currentPage != safeInitialIndex &&
            safeInitialIndex in 0 until pagerState.pageCount
    ) {
      pagerState.scrollToPage(safeInitialIndex)
    }
  }

  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.currentPage }
        .distinctUntilChanged()
        .collect { page -> onPageChanged(page) }
  }
  var zoomOverlayImageUrl by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(zoomOverlayImageUrl) {
    onZoomOverlayVisibilityChanged(!zoomOverlayImageUrl.isNullOrBlank())
  }
  PlatformBackHandler(enabled = !zoomOverlayImageUrl.isNullOrBlank()) { zoomOverlayImageUrl = null }
  val blockedMediaRevealState = remember { mutableStateMapOf<Int, Boolean>() }
  val currentPageScrollStates = remember {
    mutableStateMapOf<Int, androidx.compose.foundation.ScrollState>()
  }

  LaunchedEffect(scrollCurrentPageToTopSignal, pagerState.currentPage) {
    val scrollState = currentPageScrollStates[pagerState.currentPage] ?: return@LaunchedEffect
    scrollState.animateScrollTo(0)
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val item = submissions[page]
        val scrollState = rememberScrollState()
        DisposableEffect(page, scrollState) {
          currentPageScrollStates[page] = scrollState
          onDispose {
            if (currentPageScrollStates[page] === scrollState) {
              currentPageScrollStates.remove(page)
            }
          }
        }
        Box(modifier = Modifier.fillMaxSize()) {
          Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            SubmissionDetailContent(
                item = item,
                detailState = detailBySid[item.id],
                onRetry = onRetryCurrentDetail,
                onOpenAuthor = onOpenAuthor,
                onSearchKeyword = onSearchKeyword,
                onKeywordLongPress = onKeywordLongPress,
                onOpenBrowseFilter = onOpenBrowseFilter,
                onCopySubmissionUrl = onCopySubmissionUrl,
                onOpenImageZoom = { imageUrl -> zoomOverlayImageUrl = imageUrl },
                isBlockedByTag = item.isBlockedByTag,
                blockedSubmissionMode = blockedSubmissionMode,
                isBlockedMediaRevealed = blockedMediaRevealState[item.id] == true,
                onRevealBlockedMedia = { blockedMediaRevealState[item.id] = true },
                descriptionTranslationService = descriptionTranslationService,
                requestPagerFocus = requestPagerFocus,
            )
          }
          PlatformVerticalScrollbar(
              scrollState = scrollState,
              modifier =
                  Modifier.align(Alignment.CenterEnd)
                      .fillMaxHeight()
                      .padding(vertical = 8.dp, horizontal = 2.dp),
          )
        }
      }
    }

    val activeImageUrl = zoomOverlayImageUrl
    if (!activeImageUrl.isNullOrBlank()) {
      SubmissionZoomImageOverlay(
          imageUrl = activeImageUrl,
          onDismiss = { zoomOverlayImageUrl = null },
      )
    }
  }
}
