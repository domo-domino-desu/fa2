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
import fa2.shared.generated.resources.*
import kotlinx.coroutines.launch
import me.domino.fa2.ui.components.FilterDialogTriggerField
import me.domino.fa2.ui.components.FilterDropdownField
import me.domino.fa2.ui.components.FilterOption
import me.domino.fa2.ui.components.FilterOptionGroup
import me.domino.fa2.ui.components.GroupedFilterDropdownField
import me.domino.fa2.ui.components.GroupedTextPickerDialog
import me.domino.fa2.ui.components.toFilterOption
import me.domino.fa2.ui.components.toFilterOptionGroup
import me.domino.fa2.ui.host.LocalAppI18n
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
import me.domino.fa2.ui.search.SearchUiMetadataKey
import me.domino.fa2.ui.search.SearchUiOptionKey
import me.domino.fa2.ui.search.SearchUiTextKey
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SearchOverlayContent(
    form: SearchFormState,
    actions: SearchScreenActions,
    canSearch: Boolean,
    uiData: SearchOverlayUiData,
    modifier: Modifier = Modifier,
) {
  val appI18n = LocalAppI18n.current
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
            label = { Text(stringResource(Res.string.query)) },
            placeholder = { Text(stringResource(Res.string.search_query_example)) },
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
            title =
                uiData.searchUiLabelsRepository.text(
                    SearchUiTextKey.FILTER_GENDER_KEYWORDS,
                    appI18n.uiLanguage,
                ),
            selectedGenders = form.selectedGenders,
            labelForGender = { gender ->
              uiData.searchUiLabelsRepository.metadataOptionLabel(
                  SearchUiOptionKey.GENDER,
                  gender.token,
                  appI18n.metadata,
              )
            },
            onToggleGender = actions.onToggleGender,
        )

        RatingsSection(
            title =
                uiData.searchUiLabelsRepository.metadataLabel(
                    SearchUiMetadataKey.RATING,
                    appI18n.metadata,
                ),
            generalLabel =
                uiData.searchUiLabelsRepository.metadataOptionLabel(
                    SearchUiOptionKey.RATING,
                    "general",
                    appI18n.metadata,
                ),
            matureLabel =
                uiData.searchUiLabelsRepository.metadataOptionLabel(
                    SearchUiOptionKey.RATING,
                    "mature",
                    appI18n.metadata,
                ),
            adultLabel =
                uiData.searchUiLabelsRepository.metadataOptionLabel(
                    SearchUiOptionKey.RATING,
                    "adult",
                    appI18n.metadata,
                ),
            general = form.ratingGeneral,
            mature = form.ratingMature,
            adult = form.ratingAdult,
            onSetGeneral = actions.onSetRatingGeneral,
            onSetMature = actions.onSetRatingMature,
            onSetAdult = actions.onSetRatingAdult,
        )

        SubmissionTypeSection(
            title =
                uiData.searchUiLabelsRepository.text(
                    SearchUiTextKey.FILTER_SUBMISSION_TYPES,
                    appI18n.uiLanguage,
                ),
            artLabel =
                uiData.searchUiLabelsRepository.optionLabel(
                    SearchUiOptionKey.SUBMISSION_TYPE,
                    "art",
                    appI18n.uiLanguage,
                ),
            musicLabel =
                uiData.searchUiLabelsRepository.optionLabel(
                    SearchUiOptionKey.SUBMISSION_TYPE,
                    "music",
                    appI18n.uiLanguage,
                ),
            flashLabel =
                uiData.searchUiLabelsRepository.optionLabel(
                    SearchUiOptionKey.SUBMISSION_TYPE,
                    "flash",
                    appI18n.uiLanguage,
                ),
            storyLabel =
                uiData.searchUiLabelsRepository.optionLabel(
                    SearchUiOptionKey.SUBMISSION_TYPE,
                    "story",
                    appI18n.uiLanguage,
                ),
            photoLabel =
                uiData.searchUiLabelsRepository.optionLabel(
                    SearchUiOptionKey.SUBMISSION_TYPE,
                    "photo",
                    appI18n.uiLanguage,
                ),
            poetryLabel =
                uiData.searchUiLabelsRepository.optionLabel(
                    SearchUiOptionKey.SUBMISSION_TYPE,
                    "poetry",
                    appI18n.uiLanguage,
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
  val appI18n = LocalAppI18n.current
  val taxonomyRepository = LocalTaxonomyRepository.current
  val searchUiLabelsRepository = LocalSearchUiLabelsRepository.current
  val taxonomyCatalog = LocalTaxonomyCatalog.current
  val searchUiLabelsCatalog = LocalSearchUiLabelsCatalog.current

  return remember(
      taxonomyCatalog,
      searchUiLabelsCatalog,
      taxonomyRepository,
      searchUiLabelsRepository,
      appI18n,
  ) {
    SearchOverlayUiData(
        categoryOptions =
            taxonomyRepository.categoryOptions(appI18n.metadata).map { it.toFilterOption() },
        categoryOptionGroups =
            taxonomyRepository.categoryOptionGroups(appI18n.metadata).map {
              it.toFilterOptionGroup()
            },
        typeOptions = taxonomyRepository.typeOptions(appI18n.metadata).map { it.toFilterOption() },
        speciesOptions =
            taxonomyRepository.speciesOptions(appI18n.metadata).map { it.toFilterOption() },
        typeOptionGroups =
            taxonomyRepository.typeOptionGroups(appI18n.metadata).map { it.toFilterOptionGroup() },
        speciesOptionGroups =
            taxonomyRepository.speciesOptionGroups(appI18n.metadata).map {
              it.toFilterOptionGroup()
            },
        orderByOptions = orderByOptions(searchUiLabelsRepository, appI18n.uiLanguage),
        orderDirectionOptions = orderDirectionOptions(searchUiLabelsRepository, appI18n.uiLanguage),
        rangeOptions = rangeOptions(searchUiLabelsRepository, appI18n.uiLanguage),
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
  val appI18n = LocalAppI18n.current
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
            label =
                searchUiLabelsRepository.metadataLabel(
                    SearchUiMetadataKey.CATEGORY,
                    appI18n.metadata,
                ),
            groups = categoryOptionGroups,
            selected = form.category,
            onSelected = onUpdateCategory,
            modifier = Modifier.fillMaxWidth(),
        )
        FilterDialogTriggerField(
            label =
                searchUiLabelsRepository.metadataLabel(SearchUiMetadataKey.TYPE, appI18n.metadata),
            valueLabel = selectedTypeLabel,
            onOpenPicker = { typePickerVisible = true },
            modifier = Modifier.fillMaxWidth(),
        )
        FilterDialogTriggerField(
            label =
                searchUiLabelsRepository.metadataLabel(
                    SearchUiMetadataKey.SPECIES,
                    appI18n.metadata,
                ),
            valueLabel = selectedSpeciesLabel,
            onOpenPicker = { speciesPickerVisible = true },
            modifier = Modifier.fillMaxWidth(),
        )
        FilterDropdownField(
            label =
                searchUiLabelsRepository.text(
                    SearchUiTextKey.FILTER_SORT_CRITERIA,
                    appI18n.uiLanguage,
                ),
            options = orderByOptions,
            selected = form.orderBy,
            onSelected = onUpdateOrderBy,
            modifier = Modifier.fillMaxWidth(),
        )
        FilterDropdownField(
            label =
                searchUiLabelsRepository.text(
                    SearchUiTextKey.FILTER_SORT_DIRECTION,
                    appI18n.uiLanguage,
                ),
            options = orderDirectionOptions,
            selected = form.orderDirection,
            onSelected = onUpdateOrderDirection,
            modifier = Modifier.fillMaxWidth(),
        )
        FilterDropdownField(
            label =
                searchUiLabelsRepository.text(
                    SearchUiTextKey.FILTER_DATE,
                    appI18n.uiLanguage,
                ),
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
              label =
                  searchUiLabelsRepository.metadataLabel(
                      SearchUiMetadataKey.CATEGORY,
                      appI18n.metadata,
                  ),
              groups = categoryOptionGroups,
              selected = form.category,
              onSelected = onUpdateCategory,
              modifier = Modifier.weight(1f),
          )
          FilterDialogTriggerField(
              label =
                  searchUiLabelsRepository.metadataLabel(
                      SearchUiMetadataKey.TYPE,
                      appI18n.metadata,
                  ),
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
              label =
                  searchUiLabelsRepository.metadataLabel(
                      SearchUiMetadataKey.SPECIES,
                      appI18n.metadata,
                  ),
              valueLabel = selectedSpeciesLabel,
              onOpenPicker = { speciesPickerVisible = true },
              modifier = Modifier.weight(1f),
          )
          FilterDropdownField(
              label =
                  searchUiLabelsRepository.text(
                      SearchUiTextKey.FILTER_SORT_CRITERIA,
                      appI18n.uiLanguage,
                  ),
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
              label =
                  searchUiLabelsRepository.text(
                      SearchUiTextKey.FILTER_SORT_DIRECTION,
                      appI18n.uiLanguage,
                  ),
              options = orderDirectionOptions,
              selected = form.orderDirection,
              onSelected = onUpdateOrderDirection,
              modifier = Modifier.weight(1f),
          )
          FilterDropdownField(
              label =
                  searchUiLabelsRepository.text(
                      SearchUiTextKey.FILTER_DATE,
                      appI18n.uiLanguage,
                  ),
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
        title = searchUiLabelsRepository.metadataLabel(SearchUiMetadataKey.TYPE, appI18n.metadata),
        groups = typeOptionGroups,
        selected = form.type,
        onSelected = onUpdateType,
        onDismissRequest = { typePickerVisible = false },
    )
  }

  if (speciesPickerVisible) {
    GroupedTextPickerDialog(
        title =
            searchUiLabelsRepository.metadataLabel(
                SearchUiMetadataKey.SPECIES,
                appI18n.metadata,
            ),
        groups = speciesOptionGroups,
        selected = form.species,
        onSelected = onUpdateSpecies,
        onDismissRequest = { speciesPickerVisible = false },
    )
  }
}
