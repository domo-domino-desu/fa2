package me.domino.fa2.ui.pages.search.component

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.domino.fa2.data.search.SearchUiLabelsRepository
import me.domino.fa2.data.taxonomy.FaTaxonomyRepository
import me.domino.fa2.ui.components.FilterDialogTriggerField
import me.domino.fa2.ui.components.FilterDropdownField
import me.domino.fa2.ui.components.FilterOption
import me.domino.fa2.ui.components.FilterOptionGroup
import me.domino.fa2.ui.components.GroupedTextPickerDialog
import me.domino.fa2.ui.layouts.SearchOverlayTopBar
import me.domino.fa2.ui.pages.search.SearchFormState
import me.domino.fa2.ui.pages.search.SearchScreenActions
import me.domino.fa2.ui.pages.search.orderByOptions
import me.domino.fa2.ui.pages.search.orderDirectionOptions
import me.domino.fa2.ui.pages.search.rangeOptions
import org.koin.compose.koinInject

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SearchOverlayContent(
    form: SearchFormState,
    actions: SearchScreenActions,
    canSearch: Boolean,
    modifier: Modifier = Modifier,
) {
  val taxonomyRepository = koinInject<FaTaxonomyRepository>()
  val searchUiLabelsRepository = koinInject<SearchUiLabelsRepository>()
  val taxonomyCatalog by taxonomyRepository.catalog.collectAsState()
  val searchUiLabelsCatalog by searchUiLabelsRepository.catalog.collectAsState()
  val searchCategoryOptions = remember(taxonomyCatalog) { taxonomyRepository.categoryOptions() }
  val searchTypeOptions = remember(taxonomyCatalog) { taxonomyRepository.typeOptions() }
  val searchSpeciesOptions = remember(taxonomyCatalog) { taxonomyRepository.speciesOptions() }
  val searchTypeOptionGroups = remember(taxonomyCatalog) { taxonomyRepository.typeOptionGroups() }
  val searchSpeciesOptionGroups =
      remember(taxonomyCatalog) { taxonomyRepository.speciesOptionGroups() }
  val searchOrderByOptions =
      remember(searchUiLabelsCatalog) { orderByOptions(searchUiLabelsRepository) }
  val searchOrderDirectionOptions =
      remember(searchUiLabelsCatalog) { orderDirectionOptions(searchUiLabelsRepository) }
  val searchRangeOptions =
      remember(searchUiLabelsCatalog) { rangeOptions(searchUiLabelsRepository) }

  Surface(
      modifier = modifier.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f)),
      color = MaterialTheme.colorScheme.surface,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      SearchOverlayTopBar(
          onClose = actions.onCloseOverlay,
          onApplySearch = actions.onApplySearch,
          canSearch = canSearch,
      )

      Column(
          modifier = Modifier.fillMaxSize().padding(12.dp),
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
            categoryOptions = searchCategoryOptions,
            typeOptions = searchTypeOptions,
            speciesOptions = searchSpeciesOptions,
            typeOptionGroups = searchTypeOptionGroups,
            speciesOptionGroups = searchSpeciesOptionGroups,
            orderByOptions = searchOrderByOptions,
            orderDirectionOptions = searchOrderDirectionOptions,
            rangeOptions = searchRangeOptions,
            searchUiLabelsRepository = searchUiLabelsRepository,
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
            title = searchUiLabelsRepository.filterGenderKeywordsLabel(),
            selectedGenders = form.selectedGenders,
            labelForGender = { gender -> searchUiLabelsRepository.genderLabel(gender.token) },
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
            title = searchUiLabelsRepository.filterSubmissionTypesLabel(),
            artLabel = searchUiLabelsRepository.submissionTypeLabel("art"),
            musicLabel = searchUiLabelsRepository.submissionTypeLabel("music"),
            flashLabel = searchUiLabelsRepository.submissionTypeLabel("flash"),
            storyLabel = searchUiLabelsRepository.submissionTypeLabel("story"),
            photoLabel = searchUiLabelsRepository.submissionTypeLabel("photo"),
            poetryLabel = searchUiLabelsRepository.submissionTypeLabel("poetry"),
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
    categoryOptions: List<FilterOption<Int>>,
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
        FilterDropdownField(
            label = "类别",
            options = categoryOptions,
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
            label = searchUiLabelsRepository.filterSortCriteriaLabel(),
            options = orderByOptions,
            selected = form.orderBy,
            onSelected = onUpdateOrderBy,
            modifier = Modifier.fillMaxWidth(),
        )
        FilterDropdownField(
            label = searchUiLabelsRepository.filterSortDirectionLabel(),
            options = orderDirectionOptions,
            selected = form.orderDirection,
            onSelected = onUpdateOrderDirection,
            modifier = Modifier.fillMaxWidth(),
        )
        FilterDropdownField(
            label = searchUiLabelsRepository.filterDateLabel(),
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
              label = "类别",
              options = categoryOptions,
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
              label = searchUiLabelsRepository.filterSortCriteriaLabel(),
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
              label = searchUiLabelsRepository.filterSortDirectionLabel(),
              options = orderDirectionOptions,
              selected = form.orderDirection,
              onSelected = onUpdateOrderDirection,
              modifier = Modifier.weight(1f),
          )
          FilterDropdownField(
              label = searchUiLabelsRepository.filterDateLabel(),
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
