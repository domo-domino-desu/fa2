package me.domino.fa2.ui.pages.submission

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import me.domino.fa2.application.submissionseries.SubmissionSeriesResolvedSeries
import me.domino.fa2.data.settings.BlockedSubmissionPagerMode
import me.domino.fa2.domain.ocr.NormalizedImagePoint
import me.domino.fa2.ui.components.PageStateWrapper
import me.domino.fa2.ui.components.platform.PlatformDownloadRequest
import me.domino.fa2.ui.components.submission.SubmissionPager
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.ui.layouts.SubmissionRouteTopBar
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SubmissionRouteChrome(
    state: SubmissionPagerUiState,
    pageState: me.domino.fa2.data.model.PageState<SubmissionPagerUiState>,
    topBarActions: SubmissionTopBarActions,
    zoomOverlayVisible: Boolean,
    blockedSubmissionMode: BlockedSubmissionPagerMode,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onDownload: (PlatformDownloadRequest) -> Unit,
    onRequestScrollToTop: () -> Unit,
    onRetryPage: () -> Unit,
    onRetryCurrentDetail: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onOpenAuthor: (String) -> Unit,
    onSearchKeyword: (String) -> Unit,
    onKeywordLongPress: (String) -> Unit,
    onOpenBrowseFilter: (Int, Int, Int) -> Unit,
    onCopySubmissionUrl: (String) -> Unit,
    onLoadAttachmentText: () -> Unit,
    onTranslateDescription: () -> Unit,
    onWrapDescriptionText: () -> Unit,
    onTranslateAttachment: () -> Unit,
    onWrapAttachmentText: () -> Unit,
    onOpenSubmissionSeries: (SubmissionSeriesResolvedSeries) -> Unit,
    scrollOffsetOfSid: (Int) -> Int,
    requestPagerFocus: () -> Unit,
    onOpenImageZoom: (String) -> Unit,
    onDismissImageZoom: () -> Unit,
    onToggleImageOcr: () -> Unit,
    onTranslateImageOcr: () -> Unit,
    imageOcrTranslationEnabled: Boolean,
    onOpenImageOcrDialog: (String) -> Unit,
    onDismissImageOcrDialog: () -> Unit,
    onUpdateImageOcrDialogDraft: (String) -> Unit,
    onRefreshImageOcrDialogTranslation: () -> Unit,
    onMergeImageOcrBlocks: (String, List<NormalizedImagePoint>) -> Unit,
    onPageScrollOffsetChanged: (Int, Int) -> Unit,
    onToggleFavorite: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize().testTag("submission-route")) {
    if (!zoomOverlayVisible) {
      SubmissionRouteTopBar(
          onBack = onBack,
          onGoHome = onGoHome,
          shareUrl = topBarActions.shareUrl,
          downloadUrl = topBarActions.downloadRequest?.downloadUrl,
          onDownload = { topBarActions.downloadRequest?.let { request -> onDownload(request) } },
          onTitleClick = onRequestScrollToTop,
      )
    }
    PageStateWrapper(
        state = pageState,
        hasContent =
            when (state) {
              SubmissionPagerUiState.Empty -> false
              is SubmissionPagerUiState.Data -> state.submissions.isNotEmpty()
            },
        onRetry = onRetryPage,
    ) {
      when (val snapshot = state) {
        SubmissionPagerUiState.Empty -> {
          Text(
              text = stringResource(Res.string.no_browsable_content),
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
              style = MaterialTheme.typography.bodyMedium,
          )
        }

        is SubmissionPagerUiState.Data -> {
          SubmissionImagePrefetchEffect(snapshot = snapshot)
          Box(modifier = Modifier.fillMaxSize()) {
            SubmissionPager(
                submissions = snapshot.submissions,
                detailBySid = snapshot.detailBySid,
                initialIndex = snapshot.currentIndex,
                onRetryCurrentDetail = onRetryCurrentDetail,
                onPageChanged = onPageChanged,
                onOpenAuthor = onOpenAuthor,
                onSearchKeyword = onSearchKeyword,
                onKeywordLongPress = onKeywordLongPress,
                onOpenBrowseFilter = onOpenBrowseFilter,
                onCopySubmissionUrl = onCopySubmissionUrl,
                onLoadAttachmentText = onLoadAttachmentText,
                onTranslateDescription = onTranslateDescription,
                onWrapDescriptionText = onWrapDescriptionText,
                onTranslateAttachment = onTranslateAttachment,
                onWrapAttachmentText = onWrapAttachmentText,
                onOpenSubmissionSeries = onOpenSubmissionSeries,
                scrollOffsetOfSid = scrollOffsetOfSid,
                requestPagerFocus = requestPagerFocus,
                activeZoomOverlayImageUrl = snapshot.zoomOverlayImageUrl,
                zoomImageOcrState = snapshot.zoomImageOcrState,
                onOpenImageZoom = onOpenImageZoom,
                onDismissImageZoom = onDismissImageZoom,
                onToggleImageOcr = onToggleImageOcr,
                onTranslateImageOcr = onTranslateImageOcr,
                imageOcrTranslationEnabled = imageOcrTranslationEnabled,
                onOpenImageOcrDialog = onOpenImageOcrDialog,
                onDismissImageOcrDialog = onDismissImageOcrDialog,
                onUpdateImageOcrDialogDraft = onUpdateImageOcrDialogDraft,
                onRefreshImageOcrDialogTranslation = onRefreshImageOcrDialogTranslation,
                onMergeImageOcrBlocks = onMergeImageOcrBlocks,
                onPageScrollOffsetChanged = onPageScrollOffsetChanged,
                scrollToTopVersionBySid = snapshot.scrollToTopVersionBySid,
                blockedSubmissionMode = blockedSubmissionMode,
            )

            val currentItem = snapshot.submissions.getOrNull(snapshot.currentIndex)
            val detailState =
                currentItem?.let { item ->
                  snapshot.detailBySid[item.id] as? SubmissionDetailUiState.Success
                }
            if (
                !zoomOverlayVisible &&
                    detailState != null &&
                    detailState.detail.favoriteActionUrl.isNotBlank()
            ) {
              MediumFloatingActionButton(
                  onClick = {
                    onToggleFavorite()
                    requestPagerFocus()
                  },
                  modifier =
                      Modifier.align(Alignment.BottomEnd).padding(20.dp).focusProperties {
                        canFocus = false
                      },
                  containerColor =
                      if (detailState.detail.isFavorited) {
                        MaterialTheme.colorScheme.primaryContainer
                      } else {
                        MaterialTheme.colorScheme.surface
                      },
              ) {
                if (detailState.favoriteUpdating) {
                  LoadingIndicator(
                      modifier = Modifier.size(22.dp),
                      color = MaterialTheme.colorScheme.primary,
                  )
                } else {
                  Icon(
                      imageVector =
                          if (detailState.detail.isFavorited) {
                            FaMaterialSymbols.Filled.Favorite
                          } else {
                            FaMaterialSymbols.Filled.FavoriteBorder
                          },
                      contentDescription =
                          if (detailState.detail.isFavorited) {
                            stringResource(Res.string.unfavorite)
                          } else {
                            stringResource(Res.string.favorite)
                          },
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
