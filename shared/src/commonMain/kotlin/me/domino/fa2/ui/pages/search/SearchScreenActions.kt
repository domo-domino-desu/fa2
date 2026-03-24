package me.domino.fa2.ui.pages.search

import androidx.compose.runtime.Stable
import me.domino.fa2.data.model.SubmissionThumbnail

@Stable
data class SearchScreenActions(
    val onOpenOverlay: () -> Unit,
    val onCloseOverlay: () -> Unit,
    val onUpdateQuery: (String) -> Unit,
    val onToggleGender: (SearchGender, Boolean) -> Unit,
    val onUpdateCategory: (Int) -> Unit,
    val onUpdateType: (Int) -> Unit,
    val onUpdateSpecies: (Int) -> Unit,
    val onUpdateOrderBy: (String) -> Unit,
    val onUpdateOrderDirection: (String) -> Unit,
    val onUpdateRange: (String) -> Unit,
    val onUpdateRangeFrom: (String) -> Unit,
    val onUpdateRangeTo: (String) -> Unit,
    val onSetRatingGeneral: (Boolean) -> Unit,
    val onSetRatingMature: (Boolean) -> Unit,
    val onSetRatingAdult: (Boolean) -> Unit,
    val onSetTypeArt: (Boolean) -> Unit,
    val onSetTypeMusic: (Boolean) -> Unit,
    val onSetTypeFlash: (Boolean) -> Unit,
    val onSetTypeStory: (Boolean) -> Unit,
    val onSetTypePhoto: (Boolean) -> Unit,
    val onSetTypePoetry: (Boolean) -> Unit,
    val onApplySearch: () -> Unit,
    val onRefresh: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenSubmission: (SubmissionThumbnail) -> Unit,
    val onLastVisibleIndexChanged: (Int) -> Unit,
    val onRetryLoadMore: () -> Unit,
)
