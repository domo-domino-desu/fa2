package me.domino.fa2.ui.pages.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.ui.components.FilterDialogTriggerField
import me.domino.fa2.ui.components.FilterDropdownField
import me.domino.fa2.ui.components.FilterOption
import me.domino.fa2.ui.components.FilterOptionGroup
import me.domino.fa2.ui.components.GroupedFilterDropdownField
import me.domino.fa2.ui.components.GroupedTextPickerDialog
import me.domino.fa2.ui.components.platform.PlatformBackHandler
import me.domino.fa2.ui.components.submission.SubmissionWaterfall
import me.domino.fa2.ui.components.submission.WaterfallLoadingSkeleton
import me.domino.fa2.ui.components.toFilterOption
import me.domino.fa2.ui.components.toFilterOptionGroup
import me.domino.fa2.ui.host.LocalAppSettings
import me.domino.fa2.ui.host.LocalSearchUiLabelsCatalog
import me.domino.fa2.ui.host.LocalSearchUiLabelsRepository
import me.domino.fa2.ui.host.LocalTaxonomyCatalog
import me.domino.fa2.ui.host.LocalTaxonomyRepository
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.ui.layouts.BrowseFilterOverlayTopBar
import me.domino.fa2.ui.search.SearchUiLabelsRepository
import me.domino.fa2.ui.search.SearchUiOptionKey
import me.domino.fa2.ui.search.SearchUiTextKey

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
  val settings = LocalAppSettings.current
  val searchUiLabelsRepository = LocalSearchUiLabelsRepository.current
  val taxonomyRepository = LocalTaxonomyRepository.current
  val searchUiLabelsCatalog = LocalSearchUiLabelsCatalog.current
  val taxonomyCatalog = LocalTaxonomyCatalog.current
  var filterPageVisible by remember { mutableStateOf(false) }
  val browseCategoryOptions =
      remember(taxonomyCatalog) { taxonomyRepository.categoryOptions().map { it.toFilterOption() } }
  val browseCategoryOptionGroups =
      remember(taxonomyCatalog) {
        taxonomyRepository.categoryOptionGroups().map { it.toFilterOptionGroup() }
      }
  val browseTypeOptions =
      remember(taxonomyCatalog) { taxonomyRepository.typeOptions().map { it.toFilterOption() } }
  val browseSpeciesOptions =
      remember(taxonomyCatalog) { taxonomyRepository.speciesOptions().map { it.toFilterOption() } }
  val browseTypeOptionGroups =
      remember(taxonomyCatalog) {
        taxonomyRepository.typeOptionGroups().map { it.toFilterOptionGroup() }
      }
  val browseSpeciesOptionGroups =
      remember(taxonomyCatalog) {
        taxonomyRepository.speciesOptionGroups().map { it.toFilterOptionGroup() }
      }
  val browseGenderOptions =
      remember(searchUiLabelsCatalog) { buildBrowseGenderOptions(searchUiLabelsRepository) }

  val filterBar: @Composable () -> Unit = {
    BrowseFilterSummaryBar(
        chips =
            buildBrowseFilterChips(
                filter = state.appliedFilter,
                categoryOptions = browseCategoryOptions,
                typeOptions = browseTypeOptions,
                speciesOptions = browseSpeciesOptions,
                genderOptions = browseGenderOptions,
                searchUiLabelsRepository = searchUiLabelsRepository,
            ),
        onOpenFilterPage = { filterPageVisible = true },
    )
  }

  Box(modifier = Modifier.fillMaxSize()) {
    if (state.loading && state.submissions.isEmpty()) {
      Column(
          modifier = Modifier.fillMaxSize(),
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
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        filterBar()
        BrowseStatusCard(title = "加载失败", body = state.errorMessage.orEmpty(), onRetry = onRetry)
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
          categoryOptions = browseCategoryOptions,
          categoryOptionGroups = browseCategoryOptionGroups,
          typeOptions = browseTypeOptions,
          speciesOptions = browseSpeciesOptions,
          typeOptionGroups = browseTypeOptionGroups,
          speciesOptionGroups = browseSpeciesOptionGroups,
          genderOptions = browseGenderOptions,
          searchUiLabelsRepository = searchUiLabelsRepository,
          modifier = Modifier.fillMaxSize(),
      )
    }
  }

  PlatformBackHandler(enabled = filterPageVisible, onBack = { filterPageVisible = false })
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BrowseFilterSummaryBar(chips: List<String>, onOpenFilterPage: () -> Unit) {
  Surface(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp),
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(14.dp),
      onClick = onOpenFilterPage,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
      Icon(
          imageVector = FaMaterialSymbols.Outlined.FilterAlt,
          contentDescription = "打开筛选页面",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    categoryOptions: List<FilterOption<Int>>,
    categoryOptionGroups: List<FilterOptionGroup<Int>>,
    typeOptions: List<FilterOption<Int>>,
    speciesOptions: List<FilterOption<Int>>,
    typeOptionGroups: List<FilterOptionGroup<Int>>,
    speciesOptionGroups: List<FilterOptionGroup<Int>>,
    genderOptions: List<FilterOption<String>>,
    searchUiLabelsRepository: SearchUiLabelsRepository,
    modifier: Modifier = Modifier,
) {
  var typePickerVisible by remember { mutableStateOf(false) }
  var speciesPickerVisible by remember { mutableStateOf(false) }
  val selectedTypeLabel =
      typeOptions.firstOrNull { option -> option.value == filter.type }?.label
          ?: filter.type.toString()
  val selectedSpeciesLabel =
      speciesOptions.firstOrNull { option -> option.value == filter.species }?.label
          ?: filter.species.toString()

  Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
    val bodyScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize()) {
      BrowseFilterOverlayTopBar(
          onClose = onClose,
          onApply = onApply,
          onTitleClick = { coroutineScope.launch { bodyScrollState.animateScrollTo(0) } },
      )

      Column(
          modifier =
              Modifier.fillMaxSize()
                  .verticalScroll(bodyScrollState)
                  .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
          GroupedFilterDropdownField(
              label = "类别",
              groups = categoryOptionGroups,
              selected = filter.category,
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
              label = searchUiLabelsRepository.text(SearchUiTextKey.SUMMARY_GENDERS),
              options = genderOptions,
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
        title = "分类",
        groups = typeOptionGroups,
        selected = filter.type,
        onSelected = onUpdateType,
        onDismissRequest = { typePickerVisible = false },
    )
  }

  if (speciesPickerVisible) {
    GroupedTextPickerDialog(
        title = "物种",
        groups = speciesOptionGroups,
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
private fun RatingCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    Text(text = label, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun BrowseStatusCard(title: String, body: String, onRetry: () -> Unit) {
  Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
      border =
          androidx.compose.foundation.BorderStroke(
              width = 1.dp,
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
          ),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
  ) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(text = title, style = MaterialTheme.typography.titleMedium)
      Text(
          text = body,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(onClick = onRetry) { Text("重试") }
    }
  }
}

private fun buildBrowseGenderOptions(
    searchUiLabelsRepository: SearchUiLabelsRepository
): List<FilterOption<String>> =
    listOf(
        FilterOption("", searchUiLabelsRepository.text(SearchUiTextKey.PHRASE_ANY)),
        FilterOption(
            "male",
            searchUiLabelsRepository.optionLabel(SearchUiOptionKey.GENDER, "male"),
        ),
        FilterOption(
            "female",
            searchUiLabelsRepository.optionLabel(SearchUiOptionKey.GENDER, "female"),
        ),
        FilterOption(
            "trans_male",
            searchUiLabelsRepository.optionLabel(SearchUiOptionKey.GENDER, "trans_male"),
        ),
        FilterOption(
            "trans_female",
            searchUiLabelsRepository.optionLabel(SearchUiOptionKey.GENDER, "trans_female"),
        ),
        FilterOption(
            "intersex",
            searchUiLabelsRepository.optionLabel(SearchUiOptionKey.GENDER, "intersex"),
        ),
        FilterOption(
            "non_binary",
            searchUiLabelsRepository.optionLabel(SearchUiOptionKey.GENDER, "non_binary"),
        ),
    )

private fun buildBrowseFilterChips(
    filter: BrowseFilterState,
    categoryOptions: List<FilterOption<Int>>,
    typeOptions: List<FilterOption<Int>>,
    speciesOptions: List<FilterOption<Int>>,
    genderOptions: List<FilterOption<String>>,
    searchUiLabelsRepository: SearchUiLabelsRepository,
): List<String> {
  val categoryLabel =
      categoryOptions.firstOrNull { it.value == filter.category }?.label
          ?: filter.category.toString()
  val typeLabel =
      typeOptions.firstOrNull { it.value == filter.type }?.label ?: filter.type.toString()
  val speciesLabel =
      speciesOptions.firstOrNull { it.value == filter.species }?.label ?: filter.species.toString()
  val genderLabel =
      genderOptions.firstOrNull { it.value == filter.gender }?.label
          ?: searchUiLabelsRepository.text(SearchUiTextKey.PHRASE_ANY)
  val ratingLabel =
      buildList {
            if (filter.ratingGeneral) add("General")
            if (filter.ratingMature) add("Mature")
            if (filter.ratingAdult) add("Adult")
          }
          .joinToString(" + ")
          .ifBlank { "None" }
  return listOf(
      "类别：$categoryLabel",
      "分类：$typeLabel",
      "物种：$speciesLabel",
      "${searchUiLabelsRepository.text(SearchUiTextKey.SUMMARY_GENDERS)}：$genderLabel",
      "分级：$ratingLabel",
  )
}
