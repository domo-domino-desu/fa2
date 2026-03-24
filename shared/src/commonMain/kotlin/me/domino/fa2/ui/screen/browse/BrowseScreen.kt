package me.domino.fa2.ui.screen.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.ui.component.FaBrowseTaxonomyOptions
import me.domino.fa2.ui.component.FilterDialogTriggerField
import me.domino.fa2.ui.component.FilterDropdownField
import me.domino.fa2.ui.component.FilterOption
import me.domino.fa2.ui.component.GroupedTextPickerDialog
import me.domino.fa2.ui.component.platform.PlatformBackHandler
import me.domino.fa2.ui.component.SubmissionWaterfall
import me.domino.fa2.ui.component.WaterfallLoadingSkeleton
import me.domino.fa2.ui.component.topbar.BrowseFilterOverlayTopBar
import org.koin.compose.koinInject

@Composable
fun BrowseScreen(
    state: BrowseUiState,
    onUpdateCategory: (Int) -> Unit,
    onUpdateType: (Int) -> Unit,
    onUpdateSpecies: (Int) -> Unit,
    onUpdateGender: (String) -> Unit,
    onSetRatingGeneral: (Boolean) -> Unit,
    onSetRatingMature: (Boolean) -> Unit,
    onSetRatingAdult: (Boolean) -> Unit,
    onApplyFilter: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenSubmission: (SubmissionThumbnail) -> Unit,
    onLastVisibleIndexChanged: (Int) -> Unit,
    onRetryLoadMore: () -> Unit,
    waterfallState: LazyStaggeredGridState,
) {
    val settingsService = koinInject<AppSettingsService>()
    val settings by settingsService.settings.collectAsState()
    var filterPageVisible by remember { mutableStateOf(false) }

    val filterBar: @Composable () -> Unit = {
        BrowseFilterSummaryBar(
            chips = buildBrowseFilterChips(state.appliedFilter),
            onOpenFilterPage = { filterPageVisible = true },
        )
    }

    if (state.loading && state.submissions.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            filterBar()
            WaterfallLoadingSkeleton(
                minCardWidthDp = settings.waterfallMinCardWidthDp,
                state = waterfallState,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else if (!state.errorMessage.isNullOrBlank() && state.submissions.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            filterBar()
            BrowseStatusCard(
                title = "加载失败",
                body = state.errorMessage.orEmpty(),
                onRetry = onRetry,
            )
        }
    } else {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            SubmissionWaterfall(
                items = state.submissions,
                onItemClick = onOpenSubmission,
                onLastVisibleIndexChanged = onLastVisibleIndexChanged,
                canLoadMore = state.hasMore,
                loadingMore = state.isLoadingMore,
                appendErrorMessage = state.appendErrorMessage,
                onRetryLoadMore = onRetryLoadMore,
                state = waterfallState,
                minCardWidthDp = settings.waterfallMinCardWidthDp,
                headerContent = filterBar,
                blockedSubmissionMode = settings.blockedSubmissionWaterfallMode,
            )
        }
    }

    if (filterPageVisible) {
        BrowseFilterPage(
            filter = state.draftFilter,
            onClose = { filterPageVisible = false },
            onApply = {
                onApplyFilter()
                filterPageVisible = false
            },
            onUpdateCategory = onUpdateCategory,
            onUpdateType = onUpdateType,
            onUpdateSpecies = onUpdateSpecies,
            onUpdateGender = onUpdateGender,
            onSetRatingGeneral = onSetRatingGeneral,
            onSetRatingMature = onSetRatingMature,
            onSetRatingAdult = onSetRatingAdult,
            modifier = Modifier.fillMaxSize(),
        )
    }

    PlatformBackHandler(
        enabled = filterPageVisible,
        onBack = { filterPageVisible = false },
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BrowseFilterSummaryBar(
    chips: List<String>,
    onOpenFilterPage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chips.forEach { chip ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
                    shape = CircleShape,
                ) {
                    Text(
                        text = chip,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
        }
        IconButton(onClick = onOpenFilterPage) {
            Icon(
                imageVector = Icons.Outlined.FilterAlt,
                contentDescription = "打开筛选页面",
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BrowseFilterPage(
    filter: BrowseFilterState,
    onClose: () -> Unit,
    onApply: () -> Unit,
    onUpdateCategory: (Int) -> Unit,
    onUpdateType: (Int) -> Unit,
    onUpdateSpecies: (Int) -> Unit,
    onUpdateGender: (String) -> Unit,
    onSetRatingGeneral: (Boolean) -> Unit,
    onSetRatingMature: (Boolean) -> Unit,
    onSetRatingAdult: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var typePickerVisible by remember { mutableStateOf(false) }
    var speciesPickerVisible by remember { mutableStateOf(false) }
    val selectedTypeLabel = browseTypeOptions.firstOrNull { option ->
        option.value == filter.type
    }?.label ?: filter.type.toString()
    val selectedSpeciesLabel = browseSpeciesOptions.firstOrNull { option ->
        option.value == filter.species
    }?.label ?: filter.species.toString()

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BrowseFilterOverlayTopBar(
                onClose = onClose,
                onApply = onApply,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilterDropdownField(
                        label = "Category",
                        options = browseCategoryOptions,
                        selected = filter.category,
                        onSelected = onUpdateCategory,
                        modifier = Modifier.weight(1f),
                    )
                    FilterDialogTriggerField(
                        label = "Type",
                        valueLabel = selectedTypeLabel,
                        onOpenPicker = { typePickerVisible = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilterDialogTriggerField(
                        label = "Species",
                        valueLabel = selectedSpeciesLabel,
                        onOpenPicker = { speciesPickerVisible = true },
                        modifier = Modifier.weight(1f),
                    )
                    FilterDropdownField(
                        label = "Gender",
                        options = browseGenderOptions,
                        selected = filter.gender,
                        onSelected = onUpdateGender,
                        modifier = Modifier.weight(1f),
                    )
                }
                RatingRow(
                    general = filter.ratingGeneral,
                    mature = filter.ratingMature,
                    adult = filter.ratingAdult,
                    onSetGeneral = onSetRatingGeneral,
                    onSetMature = onSetRatingMature,
                    onSetAdult = onSetRatingAdult,
                )
            }
        }
    }

    if (typePickerVisible) {
        GroupedTextPickerDialog(
            title = "Type",
            groups = browseTypeOptionGroups,
            selected = filter.type,
            onSelected = onUpdateType,
            onDismissRequest = { typePickerVisible = false },
        )
    }

    if (speciesPickerVisible) {
        GroupedTextPickerDialog(
            title = "Species",
            groups = browseSpeciesOptionGroups,
            selected = filter.species,
            onSelected = onUpdateSpecies,
            onDismissRequest = { speciesPickerVisible = false },
        )
    }
}

@Composable
private fun RatingRow(
    general: Boolean,
    mature: Boolean,
    adult: Boolean,
    onSetGeneral: (Boolean) -> Unit,
    onSetMature: (Boolean) -> Unit,
    onSetAdult: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RatingCheckbox("General", general, onSetGeneral)
        RatingCheckbox("Mature", mature, onSetMature)
        RatingCheckbox("Adult", adult, onSetAdult)
    }
}

@Composable
private fun RatingCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun BrowseStatusCard(
    title: String,
    body: String,
    onRetry: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

private val browseCategoryOptions = FaBrowseTaxonomyOptions.categoryOptions

private val browseTypeOptions = FaBrowseTaxonomyOptions.typeOptions

private val browseSpeciesOptions = FaBrowseTaxonomyOptions.speciesOptions

private val browseTypeOptionGroups = FaBrowseTaxonomyOptions.typeOptionGroups

private val browseSpeciesOptionGroups = FaBrowseTaxonomyOptions.speciesOptionGroups

private val browseGenderOptions = listOf(
    FilterOption("", "Any"),
    FilterOption("male", "Male"),
    FilterOption("female", "Female"),
    FilterOption("trans_male", "Trans (Male)"),
    FilterOption("trans_female", "Trans (Female)"),
    FilterOption("intersex", "Intersex"),
    FilterOption("non_binary", "Non-Binary"),
)

private fun buildBrowseFilterChips(filter: BrowseFilterState): List<String> {
    val categoryLabel = browseCategoryOptions.firstOrNull { it.value == filter.category }?.label
        ?: filter.category.toString()
    val typeLabel = browseTypeOptions.firstOrNull { it.value == filter.type }?.label
        ?: filter.type.toString()
    val speciesLabel = browseSpeciesOptions.firstOrNull { it.value == filter.species }?.label
        ?: filter.species.toString()
    val genderLabel = browseGenderOptions.firstOrNull { it.value == filter.gender }?.label
        ?: "Any"
    val ratingLabel = buildList {
        if (filter.ratingGeneral) add("General")
        if (filter.ratingMature) add("Mature")
        if (filter.ratingAdult) add("Adult")
    }.joinToString(" + ")
        .ifBlank { "None" }
    return listOf(
        "Category: $categoryLabel",
        "Type: $typeLabel",
        "Species: $speciesLabel",
        "Gender: $genderLabel",
        "Rating: $ratingLabel",
    )
}
