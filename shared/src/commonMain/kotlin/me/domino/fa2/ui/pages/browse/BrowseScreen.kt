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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.search.SearchUiLabelsRepository
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.taxonomy.FaTaxonomyRepository
import me.domino.fa2.ui.components.FilterDialogTriggerField
import me.domino.fa2.ui.components.FilterDropdownField
import me.domino.fa2.ui.components.FilterOption
import me.domino.fa2.ui.components.FilterOptionGroup
import me.domino.fa2.ui.components.GroupedTextPickerDialog
import me.domino.fa2.ui.components.platform.PlatformBackHandler
import me.domino.fa2.ui.components.submission.SubmissionWaterfall
import me.domino.fa2.ui.components.submission.WaterfallLoadingSkeleton
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.ui.layouts.BrowseFilterOverlayTopBar
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
  val searchUiLabelsRepository = koinInject<SearchUiLabelsRepository>()
  val taxonomyRepository = koinInject<FaTaxonomyRepository>()
  val settings by settingsService.settings.collectAsState()
  val searchUiLabelsCatalog by searchUiLabelsRepository.catalog.collectAsState()
  val taxonomyCatalog by taxonomyRepository.catalog.collectAsState()
  var filterPageVisible by remember { mutableStateOf(false) }
  val browseCategoryOptions = remember(taxonomyCatalog) { taxonomyRepository.categoryOptions() }
  val browseTypeOptions = remember(taxonomyCatalog) { taxonomyRepository.typeOptions() }
  val browseSpeciesOptions = remember(taxonomyCatalog) { taxonomyRepository.speciesOptions() }
  val browseTypeOptionGroups = remember(taxonomyCatalog) { taxonomyRepository.typeOptionGroups() }
  val browseSpeciesOptionGroups =
      remember(taxonomyCatalog) { taxonomyRepository.speciesOptionGroups() }
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
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp),
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
      Icon(imageVector = FaMaterialSymbols.Outlined.FilterAlt, contentDescription = "打开筛选页面")
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
          FilterDropdownField(
              label = "类别",
              options = categoryOptions,
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
              label = searchUiLabelsRepository.summaryGendersLabel(),
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
        FilterOption("", searchUiLabelsRepository.anyLabel()),
        FilterOption("male", searchUiLabelsRepository.genderLabel("male")),
        FilterOption("female", searchUiLabelsRepository.genderLabel("female")),
        FilterOption("trans_male", searchUiLabelsRepository.genderLabel("trans_male")),
        FilterOption("trans_female", searchUiLabelsRepository.genderLabel("trans_female")),
        FilterOption("intersex", searchUiLabelsRepository.genderLabel("intersex")),
        FilterOption("non_binary", searchUiLabelsRepository.genderLabel("non_binary")),
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
          ?: searchUiLabelsRepository.anyLabel()
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
      "${searchUiLabelsRepository.summaryGendersLabel()}：$genderLabel",
      "分级：$ratingLabel",
  )
}
