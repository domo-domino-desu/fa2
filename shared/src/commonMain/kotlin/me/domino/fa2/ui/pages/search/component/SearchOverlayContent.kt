package me.domino.fa2.ui.pages.search.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.domino.fa2.ui.components.FilterDialogTriggerField
import me.domino.fa2.ui.components.FilterDropdownField
import me.domino.fa2.ui.components.FilterOption
import me.domino.fa2.ui.components.FilterOptionGroup
import me.domino.fa2.ui.components.GroupedFilterDropdownField
import me.domino.fa2.ui.components.GroupedTextPickerDialog
import me.domino.fa2.ui.components.toFilterOption
import me.domino.fa2.ui.components.toFilterOptionGroup
import me.domino.fa2.ui.host.LocalSearchUiLabelsCatalog
import me.domino.fa2.ui.host.LocalSearchUiLabelsRepository
import me.domino.fa2.ui.host.LocalTaxonomyCatalog
import me.domino.fa2.ui.host.LocalTaxonomyRepository
import me.domino.fa2.ui.layouts.SearchOverlayTopBar
import me.domino.fa2.ui.pages.search.SearchFormState
import me.domino.fa2.ui.pages.search.SearchScreenActions
import me.domino.fa2.ui.pages.search.orderByOptions
import me.domino.fa2.ui.pages.search.orderDirectionOptions
import me.domino.fa2.ui.pages.search.rangeOptions
import me.domino.fa2.ui.search.SearchUiLabelsRepository
import me.domino.fa2.ui.search.SearchUiOptionKey
import me.domino.fa2.ui.search.SearchUiTextKey

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SearchOverlayContent(
    form: SearchFormState,
    actions: SearchScreenActions,
    canSearch: Boolean,
    uiData: SearchOverlayUiData,
    modifier: Modifier = Modifier,
) {
  Surface(
      modifier = modifier.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f)),
      color = MaterialTheme.colorScheme.surface,
  ) {
    val bodyScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize()) {
      SearchOverlayTopBar(
          onClose = actions.onCloseOverlay,
          onApplySearch = actions.onApplySearch,
          canSearch = canSearch,
          onTitleClick = { coroutineScope.launch { bodyScrollState.animateScrollTo(0) } },
      )

      Column(
          modifier = Modifier.fillMaxSize().verticalScroll(bodyScrollState).padding(12.dp),
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
            categoryOptions = uiData.categoryOptions,
            categoryOptionGroups = uiData.categoryOptionGroups,
            typeOptions = uiData.typeOptions,
            speciesOptions = uiData.speciesOptions,
            typeOptionGroups = uiData.typeOptionGroups,
            speciesOptionGroups = uiData.speciesOptionGroups,
            orderByOptions = uiData.orderByOptions,
            orderDirectionOptions = uiData.orderDirectionOptions,
            rangeOptions = uiData.rangeOptions,
            searchUiLabelsRepository = uiData.searchUiLabelsRepository,
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
            title = uiData.searchUiLabelsRepository.text(SearchUiTextKey.FILTER_GENDER_KEYWORDS),
            selectedGenders = form.selectedGenders,
            labelForGender = { gender ->
              uiData.searchUiLabelsRepository.optionLabel(SearchUiOptionKey.GENDER, gender.token)
            },
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
            title = uiData.searchUiLabelsRepository.text(SearchUiTextKey.FILTER_SUBMISSION_TYPES),
            artLabel =
                uiData.searchUiLabelsRepository.optionLabel(
                    SearchUiOptionKey.SUBMISSION_TYPE,
                    "art",
                ),
            musicLabel =
                uiData.searchUiLabelsRepository.optionLabel(
                    SearchUiOptionKey.SUBMISSION_TYPE,
                    "music",
                ),
            flashLabel =
                uiData.searchUiLabelsRepository.optionLabel(
                    SearchUiOptionKey.SUBMISSION_TYPE,
                    "flash",
                ),
            storyLabel =
                uiData.searchUiLabelsRepository.optionLabel(
                    SearchUiOptionKey.SUBMISSION_TYPE,
                    "story",
                ),
            photoLabel =
                uiData.searchUiLabelsRepository.optionLabel(
                    SearchUiOptionKey.SUBMISSION_TYPE,
                    "photo",
                ),
            poetryLabel =
                uiData.searchUiLabelsRepository.optionLabel(
                    SearchUiOptionKey.SUBMISSION_TYPE,
                    "poetry",
                ),
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

internal data class SearchOverlayUiData(
    val categoryOptions: List<FilterOption<Int>>,
    val categoryOptionGroups: List<FilterOptionGroup<Int>>,
    val typeOptions: List<FilterOption<Int>>,
    val speciesOptions: List<FilterOption<Int>>,
    val typeOptionGroups: List<FilterOptionGroup<Int>>,
    val speciesOptionGroups: List<FilterOptionGroup<Int>>,
    val orderByOptions: List<FilterOption<String>>,
    val orderDirectionOptions: List<FilterOption<String>>,
    val rangeOptions: List<FilterOption<String>>,
    val searchUiLabelsRepository: SearchUiLabelsRepository,
)

@Composable
internal fun rememberSearchOverlayUiData(): SearchOverlayUiData {
  val taxonomyRepository = LocalTaxonomyRepository.current
  val searchUiLabelsRepository = LocalSearchUiLabelsRepository.current
  val taxonomyCatalog = LocalTaxonomyCatalog.current
  val searchUiLabelsCatalog = LocalSearchUiLabelsCatalog.current

  return remember(
      taxonomyCatalog,
      searchUiLabelsCatalog,
      taxonomyRepository,
      searchUiLabelsRepository,
  ) {
    SearchOverlayUiData(
        categoryOptions = taxonomyRepository.categoryOptions().map { it.toFilterOption() },
        categoryOptionGroups =
            taxonomyRepository.categoryOptionGroups().map { it.toFilterOptionGroup() },
        typeOptions = taxonomyRepository.typeOptions().map { it.toFilterOption() },
        speciesOptions = taxonomyRepository.speciesOptions().map { it.toFilterOption() },
        typeOptionGroups = taxonomyRepository.typeOptionGroups().map { it.toFilterOptionGroup() },
        speciesOptionGroups =
            taxonomyRepository.speciesOptionGroups().map { it.toFilterOptionGroup() },
        orderByOptions = orderByOptions(searchUiLabelsRepository),
        orderDirectionOptions = orderDirectionOptions(searchUiLabelsRepository),
        rangeOptions = rangeOptions(searchUiLabelsRepository),
        searchUiLabelsRepository = searchUiLabelsRepository,
    )
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
    categoryOptions: List<FilterOption<Int>>,
    categoryOptionGroups: List<FilterOptionGroup<Int>>,
    typeOptions: List<FilterOption<Int>>,
    speciesOptions: List<FilterOption<Int>>,
    orderByOptions: List<FilterOption<String>>,
    orderDirectionOptions: List<FilterOption<String>>,
    rangeOptions: List<FilterOption<String>>,
    typeOptionGroups: List<FilterOptionGroup<Int>>,
    speciesOptionGroups: List<FilterOptionGroup<Int>>,
    searchUiLabelsRepository: SearchUiLabelsRepository,
) {
  var typePickerVisible by remember { mutableStateOf(false) }
  var speciesPickerVisible by remember { mutableStateOf(false) }
  val selectedTypeLabel =
      typeOptions.firstOrNull { option -> option.value == form.type }?.label ?: form.type.toString()
  val selectedSpeciesLabel =
      speciesOptions.firstOrNull { option -> option.value == form.species }?.label
          ?: form.species.toString()

  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val singleColumn = maxWidth < 560.dp
    if (singleColumn) {
      Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        GroupedFilterDropdownField(
            label = "类别",
            groups = categoryOptionGroups,
            selected = form.category,
            onSelected = onUpdateCategory,
            modifier = Modifier.fillMaxWidth(),
        )
        FilterDialogTriggerField(
            label = "分类",
            valueLabel = selectedTypeLabel,
            onOpenPicker = { typePickerVisible = true },
            modifier = Modifier.fillMaxWidth(),
        )
        FilterDialogTriggerField(
            label = "物种",
            valueLabel = selectedSpeciesLabel,
            onOpenPicker = { speciesPickerVisible = true },
            modifier = Modifier.fillMaxWidth(),
        )
        FilterDropdownField(
            label = searchUiLabelsRepository.text(SearchUiTextKey.FILTER_SORT_CRITERIA),
            options = orderByOptions,
            selected = form.orderBy,
            onSelected = onUpdateOrderBy,
            modifier = Modifier.fillMaxWidth(),
        )
        FilterDropdownField(
            label = searchUiLabelsRepository.text(SearchUiTextKey.FILTER_SORT_DIRECTION),
            options = orderDirectionOptions,
            selected = form.orderDirection,
            onSelected = onUpdateOrderDirection,
            modifier = Modifier.fillMaxWidth(),
        )
        FilterDropdownField(
            label = searchUiLabelsRepository.text(SearchUiTextKey.FILTER_DATE),
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
          GroupedFilterDropdownField(
              label = "类别",
              groups = categoryOptionGroups,
              selected = form.category,
              onSelected = onUpdateCategory,
              modifier = Modifier.weight(1f),
          )
          FilterDialogTriggerField(
              label = "分类",
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
              label = "物种",
              valueLabel = selectedSpeciesLabel,
              onOpenPicker = { speciesPickerVisible = true },
              modifier = Modifier.weight(1f),
          )
          FilterDropdownField(
              label = searchUiLabelsRepository.text(SearchUiTextKey.FILTER_SORT_CRITERIA),
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
              label = searchUiLabelsRepository.text(SearchUiTextKey.FILTER_SORT_DIRECTION),
              options = orderDirectionOptions,
              selected = form.orderDirection,
              onSelected = onUpdateOrderDirection,
              modifier = Modifier.weight(1f),
          )
          FilterDropdownField(
              label = searchUiLabelsRepository.text(SearchUiTextKey.FILTER_DATE),
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
        title = "分类",
        groups = typeOptionGroups,
        selected = form.type,
        onSelected = onUpdateType,
        onDismissRequest = { typePickerVisible = false },
    )
  }

  if (speciesPickerVisible) {
    GroupedTextPickerDialog(
        title = "物种",
        groups = speciesOptionGroups,
        selected = form.species,
        onSelected = onUpdateSpecies,
        onDismissRequest = { speciesPickerVisible = false },
    )
  }
}
