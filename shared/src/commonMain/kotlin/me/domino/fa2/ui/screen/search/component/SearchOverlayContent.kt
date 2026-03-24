package me.domino.fa2.ui.screen.search.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.domino.fa2.ui.component.FilterDialogTriggerField
import me.domino.fa2.ui.component.FilterDropdownField
import me.domino.fa2.ui.component.GroupedTextPickerDialog
import me.domino.fa2.ui.component.topbar.SearchOverlayTopBar
import me.domino.fa2.ui.screen.search.SearchFormState
import me.domino.fa2.ui.screen.search.SearchScreenActions
import me.domino.fa2.ui.screen.search.orderByOptions
import me.domino.fa2.ui.screen.search.orderDirectionOptions
import me.domino.fa2.ui.screen.search.rangeOptions
import me.domino.fa2.ui.screen.search.searchCategoryOptions
import me.domino.fa2.ui.screen.search.searchSpeciesOptionGroups
import me.domino.fa2.ui.screen.search.searchSpeciesOptions
import me.domino.fa2.ui.screen.search.searchTypeOptionGroups
import me.domino.fa2.ui.screen.search.searchTypeOptions

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SearchOverlayContent(
    form: SearchFormState,
    actions: SearchScreenActions,
    canSearch: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f)),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            SearchOverlayTopBar(
                onClose = actions.onCloseOverlay,
                onApplySearch = actions.onApplySearch,
                canSearch = canSearch,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = form.query,
                    onValueChange = actions.onUpdateQuery,
                    label = { Text("Query") },
                    placeholder = { Text("wolf @keywords female trans_female") },
                    modifier = Modifier.fillMaxWidth(),
                )

                SearchTopFilterGrid(
                    form = form,
                    onUpdateCategory = actions.onUpdateCategory,
                    onUpdateType = actions.onUpdateType,
                    onUpdateSpecies = actions.onUpdateSpecies,
                    onUpdateOrderBy = actions.onUpdateOrderBy,
                    onUpdateOrderDirection = actions.onUpdateOrderDirection,
                    onUpdateRange = actions.onUpdateRange,
                )

                if (form.range == "manual") {
                    ManualDateRangeSection(
                        rangeFrom = form.rangeFrom,
                        rangeTo = form.rangeTo,
                        onUpdateRangeFrom = actions.onUpdateRangeFrom,
                        onUpdateRangeTo = actions.onUpdateRangeTo,
                    )
                }

                GenderKeywordsSection(
                    selectedGenders = form.selectedGenders,
                    onToggleGender = actions.onToggleGender,
                )

                RatingsSection(
                    general = form.ratingGeneral,
                    mature = form.ratingMature,
                    adult = form.ratingAdult,
                    onSetGeneral = actions.onSetRatingGeneral,
                    onSetMature = actions.onSetRatingMature,
                    onSetAdult = actions.onSetRatingAdult,
                )

                SubmissionTypeSection(
                    typeArt = form.typeArt,
                    typeMusic = form.typeMusic,
                    typeFlash = form.typeFlash,
                    typeStory = form.typeStory,
                    typePhoto = form.typePhoto,
                    typePoetry = form.typePoetry,
                    onSetTypeArt = actions.onSetTypeArt,
                    onSetTypeMusic = actions.onSetTypeMusic,
                    onSetTypeFlash = actions.onSetTypeFlash,
                    onSetTypeStory = actions.onSetTypeStory,
                    onSetTypePhoto = actions.onSetTypePhoto,
                    onSetTypePoetry = actions.onSetTypePoetry,
                )
            }
        }
    }
}

@Composable
private fun SearchTopFilterGrid(
    form: SearchFormState,
    onUpdateCategory: (Int) -> Unit,
    onUpdateType: (Int) -> Unit,
    onUpdateSpecies: (Int) -> Unit,
    onUpdateOrderBy: (String) -> Unit,
    onUpdateOrderDirection: (String) -> Unit,
    onUpdateRange: (String) -> Unit,
) {
    var typePickerVisible by remember { mutableStateOf(false) }
    var speciesPickerVisible by remember { mutableStateOf(false) }
    val selectedTypeLabel = searchTypeOptions.firstOrNull { option ->
        option.value == form.type
    }?.label ?: form.type.toString()
    val selectedSpeciesLabel = searchSpeciesOptions.firstOrNull { option ->
        option.value == form.species
    }?.label ?: form.species.toString()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val singleColumn = maxWidth < 560.dp
        if (singleColumn) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterDropdownField(
                    label = "Category",
                    options = searchCategoryOptions,
                    selected = form.category,
                    onSelected = onUpdateCategory,
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterDialogTriggerField(
                    label = "Type",
                    valueLabel = selectedTypeLabel,
                    onOpenPicker = { typePickerVisible = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterDialogTriggerField(
                    label = "Species",
                    valueLabel = selectedSpeciesLabel,
                    onOpenPicker = { speciesPickerVisible = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterDropdownField(
                    label = "Sort Criteria",
                    options = orderByOptions,
                    selected = form.orderBy,
                    onSelected = onUpdateOrderBy,
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterDropdownField(
                    label = "Sort Direction",
                    options = orderDirectionOptions,
                    selected = form.orderDirection,
                    onSelected = onUpdateOrderDirection,
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterDropdownField(
                    label = "Date Filter",
                    options = rangeOptions,
                    selected = form.range,
                    onSelected = onUpdateRange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilterDropdownField(
                        label = "Category",
                        options = searchCategoryOptions,
                        selected = form.category,
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
                        label = "Sort Criteria",
                        options = orderByOptions,
                        selected = form.orderBy,
                        onSelected = onUpdateOrderBy,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilterDropdownField(
                        label = "Sort Direction",
                        options = orderDirectionOptions,
                        selected = form.orderDirection,
                        onSelected = onUpdateOrderDirection,
                        modifier = Modifier.weight(1f),
                    )
                    FilterDropdownField(
                        label = "Date Filter",
                        options = rangeOptions,
                        selected = form.range,
                        onSelected = onUpdateRange,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (typePickerVisible) {
        GroupedTextPickerDialog(
            title = "Type",
            groups = searchTypeOptionGroups,
            selected = form.type,
            onSelected = onUpdateType,
            onDismissRequest = { typePickerVisible = false },
        )
    }

    if (speciesPickerVisible) {
        GroupedTextPickerDialog(
            title = "Species",
            groups = searchSpeciesOptionGroups,
            selected = form.species,
            onSelected = onUpdateSpecies,
            onDismissRequest = { speciesPickerVisible = false },
        )
    }
}
