package me.domino.fa2.ui.pages.search

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import fa2.shared.generated.resources.*
import io.ktor.http.decodeURLQueryComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.repository.ActivityHistoryRepository
import me.domino.fa2.data.repository.SearchRepository
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.taxonomy.FaTaxonomyRepository
import me.domino.fa2.i18n.AppI18nSnapshot
import me.domino.fa2.i18n.SystemLanguageProvider
import me.domino.fa2.i18n.appString
import me.domino.fa2.ui.components.FilterOption
import me.domino.fa2.ui.navigation.SubmissionListHolder
import me.domino.fa2.ui.search.SearchUiLabelsRepository
import me.domino.fa2.ui.search.SearchUiMetadataKey
import me.domino.fa2.ui.search.SearchUiOptionKey
import me.domino.fa2.ui.search.SearchUiTextKey
import me.domino.fa2.ui.state.PaginationSnapshot
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.logging.FaLog

internal const val searchAutoLoadThreshold = 10

enum class SearchGender(val token: String) {
  Male("male"),
  Female("female"),
  TransMale("trans_male"),
  TransFemale("trans_female"),
  Intersex("intersex"),
  NonBinary("non_binary"),
}

data class SearchFormState(
    val query: String = "",
    val category: Int = 1,
    val type: Int = 1,
    val species: Int = 1,
    val orderBy: String = "relevancy",
    val orderDirection: String = "desc",
    val range: String = "all",
    val rangeFrom: String = "",
    val rangeTo: String = "",
    val ratingGeneral: Boolean = true,
    val ratingMature: Boolean = true,
    val ratingAdult: Boolean = true,
    val typeArt: Boolean = true,
    val typeMusic: Boolean = true,
    val typeFlash: Boolean = true,
    val typeStory: Boolean = true,
    val typePhoto: Boolean = true,
    val typePoetry: Boolean = true,
    val selectedGenders: Set<SearchGender> = emptySet(),
)

data class SearchUiState(
    val overlayVisible: Boolean = false,
    val draft: SearchFormState = SearchFormState(),
    val applied: SearchFormState? = null,
    val hasSearched: Boolean = false,
    val submissions: List<SubmissionThumbnail> = emptyList(),
    val nextPageUrl: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val appendErrorMessage: String? = null,
) {
  val hasMore: Boolean
    get() = !nextPageUrl.isNullOrBlank()
}

/** Search 页面状态模型。 */
class SearchScreenModel(
    private val repository: SearchRepository,
    private val submissionListHolder: SubmissionListHolder,
    private val historyRepository: ActivityHistoryRepository,
    private val settingsService: AppSettingsService,
    private val systemLanguageProvider: SystemLanguageProvider,
    private val taxonomyRepository: FaTaxonomyRepository,
    private val searchUiLabelsRepository: SearchUiLabelsRepository,
) : StateScreenModel<SearchUiState>(SearchUiState()) {
  private val log = FaLog.withTag("SearchScreenModel")
  private val mutablePageState =
      MutableStateFlow<PageState<SearchUiState>>(PageState.Success(state.value))
  val pageState: StateFlow<PageState<SearchUiState>> = mutablePageState.asStateFlow()
  private val workflow =
      SearchScreenWorkflow(
          repository = repository,
          submissionListHolder = submissionListHolder,
          historyRepository = historyRepository,
          settingsService = settingsService,
          systemLanguageProvider = systemLanguageProvider,
          taxonomyRepository = taxonomyRepository,
          searchUiLabelsRepository = searchUiLabelsRepository,
          screenModelScope = screenModelScope,
          log = log,
          stateProvider = { state.value },
          stateSink = { mutableState.value = it },
          pageStateSink = { mutablePageState.value = it },
      )

  fun openOverlay() {
    workflow.openOverlay()
  }

  fun closeOverlay() {
    workflow.closeOverlay()
  }

  fun updateQuery(query: String) {
    workflow.updateQuery(query)
  }

  fun toggleGender(gender: SearchGender, checked: Boolean) {
    workflow.toggleGender(gender, checked)
  }

  fun updateCategory(category: Int) {
    workflow.updateCategory(category)
  }

  fun updateType(type: Int) {
    workflow.updateType(type)
  }

  fun updateSpecies(species: Int) {
    workflow.updateSpecies(species)
  }

  fun updateOrderBy(orderBy: String) {
    workflow.updateOrderBy(orderBy)
  }

  fun updateOrderDirection(orderDirection: String) {
    workflow.updateOrderDirection(orderDirection)
  }

  fun updateRange(range: String) {
    workflow.updateRange(range)
  }

  fun updateRangeFrom(value: String) {
    workflow.updateRangeFrom(value)
  }

  fun updateRangeTo(value: String) {
    workflow.updateRangeTo(value)
  }

  fun setRatingGeneral(enabled: Boolean) {
    workflow.setRatingGeneral(enabled)
  }

  fun setRatingMature(enabled: Boolean) {
    workflow.setRatingMature(enabled)
  }

  fun setRatingAdult(enabled: Boolean) {
    workflow.setRatingAdult(enabled)
  }

  fun setTypeArt(enabled: Boolean) {
    workflow.setTypeArt(enabled)
  }

  fun setTypeMusic(enabled: Boolean) {
    workflow.setTypeMusic(enabled)
  }

  fun setTypeFlash(enabled: Boolean) {
    workflow.setTypeFlash(enabled)
  }

  fun setTypeStory(enabled: Boolean) {
    workflow.setTypeStory(enabled)
  }

  fun setTypePhoto(enabled: Boolean) {
    workflow.setTypePhoto(enabled)
  }

  fun setTypePoetry(enabled: Boolean) {
    workflow.setTypePoetry(enabled)
  }

  fun applySearch() {
    workflow.applySearch()
  }

  fun applySearchFromUrl(url: String, fallbackQuery: String = "") {
    workflow.applySearchFromUrl(url = url, fallbackQuery = fallbackQuery)
  }

  fun refresh() {
    workflow.refresh()
  }

  fun onLastVisibleIndexChanged(lastVisibleIndex: Int) {
    workflow.onLastVisibleIndexChanged(lastVisibleIndex)
  }

  fun retryLoadMore() {
    workflow.retryLoadMore()
  }

  fun setCurrentSubmission(sid: Int) {
    workflow.setCurrentSubmission(sid)
  }
}

internal fun buildSearchUrl(form: SearchFormState, page: Int): String =
    FaUrls.search(
        FaUrls.SearchParams(
            q = form.query,
            page = page,
            category = form.category,
            arttype = form.type,
            species = form.species,
            orderBy = form.orderBy,
            orderDirection = form.orderDirection,
            range = form.range,
            rangeFrom = form.rangeFrom,
            rangeTo = form.rangeTo,
            ratingGeneral = form.ratingGeneral,
            ratingMature = form.ratingMature,
            ratingAdult = form.ratingAdult,
            typeArt = form.typeArt,
            typeMusic = form.typeMusic,
            typeFlash = form.typeFlash,
            typeStory = form.typeStory,
            typePhoto = form.typePhoto,
            typePoetry = form.typePoetry,
        )
    )

internal fun SearchUiState.toPaginationSnapshot(): PaginationSnapshot<SubmissionThumbnail> =
    PaginationSnapshot(
        items = submissions,
        nextPageUrl = nextPageUrl,
        loading = loading,
        refreshing = refreshing,
        isLoadingMore = isLoadingMore,
        errorMessage = errorMessage,
        appendErrorMessage = appendErrorMessage,
    )

internal fun SearchUiState.applyPagination(
    snapshot: PaginationSnapshot<SubmissionThumbnail>
): SearchUiState =
    copy(
        submissions = snapshot.items,
        nextPageUrl = snapshot.nextPageUrl,
        loading = snapshot.loading,
        refreshing = snapshot.refreshing,
        isLoadingMore = snapshot.isLoadingMore,
        errorMessage = snapshot.errorMessage,
        appendErrorMessage = snapshot.appendErrorMessage,
    )

internal fun parseGendersFromQuery(query: String): Set<SearchGender> {
  val tokens = query.split(Regex("\\s+")).filter { token -> token.isNotBlank() }
  val keywordsIndex = tokens.indexOfFirst { token -> token.equals("@keywords", ignoreCase = true) }
  if (keywordsIndex < 0) return emptySet()
  val keywordTokens = tokens.drop(keywordsIndex + 1)
  return keywordTokens.mapNotNullTo(linkedSetOf()) { token ->
    SearchGender.entries.firstOrNull { gender ->
      gender.token.equals(token.trim().lowercase(), ignoreCase = true)
    }
  }
}

internal fun rewriteQueryWithGenders(query: String, genders: Set<SearchGender>): String {
  val tokens = query.split(Regex("\\s+")).filter { token -> token.isNotBlank() }.toMutableList()
  val keywordsIndex = tokens.indexOfFirst { token -> token.equals("@keywords", ignoreCase = true) }
  val selectedGenderTokens =
      SearchGender.entries.filter { gender -> gender in genders }.map { gender -> gender.token }

  if (keywordsIndex < 0) {
    if (selectedGenderTokens.isEmpty()) {
      return tokens.joinToString(" ").trim()
    }
    val base = tokens.joinToString(" ").trim()
    return if (base.isBlank()) {
      "@keywords ${selectedGenderTokens.joinToString(" ")}"
    } else {
      "$base @keywords ${selectedGenderTokens.joinToString(" ")}"
    }
  }

  val beforeKeywords = tokens.take(keywordsIndex)
  val afterKeywords = tokens.drop(keywordsIndex + 1)
  val nonGenderKeywords =
      afterKeywords.filterNot { token ->
        SearchGender.entries.any { gender ->
          gender.token.equals(token.trim().lowercase(), ignoreCase = true)
        }
      }

  if (selectedGenderTokens.isEmpty() && nonGenderKeywords.isEmpty()) {
    return beforeKeywords.joinToString(" ").trim()
  }

  val rebuilt = buildList {
    addAll(beforeKeywords)
    add("@keywords")
    addAll(nonGenderKeywords)
    addAll(selectedGenderTokens)
  }
  return rebuilt.joinToString(" ").trim()
}

internal fun buildSearchFiltersSummary(
    form: SearchFormState,
    appI18n: AppI18nSnapshot,
    taxonomyRepository: FaTaxonomyRepository,
    searchUiLabelsRepository: SearchUiLabelsRepository,
): String {
  val defaults = SearchFormState()
  val summary = mutableListOf<String>()
  val orderByOptions = orderByOptions(searchUiLabelsRepository, appI18n.uiLanguage)
  val orderDirectionOptions = orderDirectionOptions(searchUiLabelsRepository, appI18n.uiLanguage)
  val rangeOptions = rangeOptions(searchUiLabelsRepository, appI18n.uiLanguage)

  if (form.category != defaults.category) {
    val label =
        taxonomyRepository.categoryDisplayNameById(form.category, appI18n.metadata)
            ?: form.category.toString()
    summary +=
        searchUiLabelsRepository.formatLabelValue(
            searchUiLabelsRepository.metadataLabel(SearchUiMetadataKey.CATEGORY, appI18n.metadata),
            label,
            appI18n.uiLanguage,
        )
  }
  if (form.type != defaults.type) {
    val label =
        taxonomyRepository.typeDisplayNameById(form.type, appI18n.metadata) ?: form.type.toString()
    summary +=
        searchUiLabelsRepository.formatLabelValue(
            searchUiLabelsRepository.metadataLabel(SearchUiMetadataKey.TYPE, appI18n.metadata),
            label,
            appI18n.uiLanguage,
        )
  }
  if (form.species != defaults.species) {
    val label =
        taxonomyRepository.speciesDisplayNameById(form.species, appI18n.metadata)
            ?: form.species.toString()
    summary +=
        searchUiLabelsRepository.formatLabelValue(
            searchUiLabelsRepository.metadataLabel(SearchUiMetadataKey.SPECIES, appI18n.metadata),
            label,
            appI18n.uiLanguage,
        )
  }
  if (form.orderBy != defaults.orderBy) {
    summary +=
        searchUiLabelsRepository.formatLabelValue(
            searchUiLabelsRepository.text(SearchUiTextKey.SUMMARY_SORT, appI18n.uiLanguage),
            orderByOptions.labelOf(form.orderBy),
            appI18n.uiLanguage,
        )
  }
  if (form.orderDirection != defaults.orderDirection) {
    summary +=
        searchUiLabelsRepository.formatLabelValue(
            searchUiLabelsRepository.text(SearchUiTextKey.SUMMARY_DIRECTION, appI18n.uiLanguage),
            orderDirectionOptions.labelOf(form.orderDirection),
            appI18n.uiLanguage,
        )
  }
  if (form.range != defaults.range) {
    val label = rangeOptions.labelOf(form.range)
    if (form.range == "manual") {
      val from = form.rangeFrom.trim()
      val to = form.rangeTo.trim()
      val detail =
          when {
            from.isNotBlank() && to.isNotBlank() ->
                "${searchUiLabelsRepository.text(SearchUiTextKey.PHRASE_FROM, appI18n.uiLanguage)} $from " +
                    "${searchUiLabelsRepository.text(SearchUiTextKey.PHRASE_TO, appI18n.uiLanguage)} $to"
            from.isNotBlank() ->
                "${searchUiLabelsRepository.text(SearchUiTextKey.PHRASE_FROM, appI18n.uiLanguage)} $from"
            to.isNotBlank() ->
                "${searchUiLabelsRepository.text(SearchUiTextKey.PHRASE_TO, appI18n.uiLanguage)} $to"
            else -> ""
          }
      summary +=
          if (detail.isBlank()) {
            searchUiLabelsRepository.formatLabelValue(
                searchUiLabelsRepository.text(SearchUiTextKey.SUMMARY_DATE, appI18n.uiLanguage),
                label,
                appI18n.uiLanguage,
            )
          } else {
            searchUiLabelsRepository.formatLabelValue(
                searchUiLabelsRepository.text(SearchUiTextKey.SUMMARY_DATE, appI18n.uiLanguage),
                "$label${appString(Res.string.parenthetical_format, detail)}",
                appI18n.uiLanguage,
            )
          }
    } else {
      summary +=
          searchUiLabelsRepository.formatLabelValue(
              searchUiLabelsRepository.text(SearchUiTextKey.SUMMARY_DATE, appI18n.uiLanguage),
              label,
              appI18n.uiLanguage,
          )
    }
  }

  if (
      form.ratingGeneral != defaults.ratingGeneral ||
          form.ratingMature != defaults.ratingMature ||
          form.ratingAdult != defaults.ratingAdult
  ) {
    val ratings = buildList {
      if (form.ratingGeneral)
          add(
              searchUiLabelsRepository.metadataOptionLabel(
                  SearchUiOptionKey.RATING,
                  "general",
                  appI18n.metadata,
              )
          )
      if (form.ratingMature)
          add(
              searchUiLabelsRepository.metadataOptionLabel(
                  SearchUiOptionKey.RATING,
                  "mature",
                  appI18n.metadata,
              )
          )
      if (form.ratingAdult)
          add(
              searchUiLabelsRepository.metadataOptionLabel(
                  SearchUiOptionKey.RATING,
                  "adult",
                  appI18n.metadata,
              )
          )
    }
    summary +=
        searchUiLabelsRepository.formatLabelValue(
            searchUiLabelsRepository.metadataLabel(SearchUiMetadataKey.RATING, appI18n.metadata),
            ratings
                .ifEmpty {
                  listOf(
                      searchUiLabelsRepository.metadataOptionLabel(
                          SearchUiOptionKey.RATING,
                          "none",
                          appI18n.metadata,
                      )
                  )
                }
                .joinToString(", "),
            appI18n.uiLanguage,
        )
  }

  if (
      form.typeArt != defaults.typeArt ||
          form.typeMusic != defaults.typeMusic ||
          form.typeFlash != defaults.typeFlash ||
          form.typeStory != defaults.typeStory ||
          form.typePhoto != defaults.typePhoto ||
          form.typePoetry != defaults.typePoetry
  ) {
    val types = buildList {
      if (form.typeArt)
          add(
              searchUiLabelsRepository.optionLabel(
                  SearchUiOptionKey.SUBMISSION_TYPE,
                  "art",
                  appI18n.uiLanguage,
              )
          )
      if (form.typeMusic)
          add(
              searchUiLabelsRepository.optionLabel(
                  SearchUiOptionKey.SUBMISSION_TYPE,
                  "music",
                  appI18n.uiLanguage,
              )
          )
      if (form.typeFlash)
          add(
              searchUiLabelsRepository.optionLabel(
                  SearchUiOptionKey.SUBMISSION_TYPE,
                  "flash",
                  appI18n.uiLanguage,
              )
          )
      if (form.typeStory)
          add(
              searchUiLabelsRepository.optionLabel(
                  SearchUiOptionKey.SUBMISSION_TYPE,
                  "story",
                  appI18n.uiLanguage,
              )
          )
      if (form.typePhoto)
          add(
              searchUiLabelsRepository.optionLabel(
                  SearchUiOptionKey.SUBMISSION_TYPE,
                  "photo",
                  appI18n.uiLanguage,
              )
          )
      if (form.typePoetry)
          add(
              searchUiLabelsRepository.optionLabel(
                  SearchUiOptionKey.SUBMISSION_TYPE,
                  "poetry",
                  appI18n.uiLanguage,
              )
          )
    }
    summary +=
        searchUiLabelsRepository.formatLabelValue(
            searchUiLabelsRepository.text(
                SearchUiTextKey.SUMMARY_SUBMISSION_TYPES,
                appI18n.uiLanguage,
            ),
            types
                .ifEmpty {
                  listOf(
                      searchUiLabelsRepository.text(
                          SearchUiTextKey.PHRASE_NONE,
                          appI18n.uiLanguage,
                      )
                  )
                }
                .joinToString(appString(Res.string.list_delimiter)),
            appI18n.uiLanguage,
        )
  }

  if (form.selectedGenders.isNotEmpty()) {
    val genders =
        SearchGender.entries
            .filter { gender -> gender in form.selectedGenders }
            .joinToString(appString(Res.string.list_delimiter)) { gender ->
              searchUiLabelsRepository.metadataOptionLabel(
                  SearchUiOptionKey.GENDER,
                  gender.token,
                  appI18n.metadata,
              )
            }
    if (genders.isNotBlank()) {
      summary +=
          searchUiLabelsRepository.formatLabelValue(
              searchUiLabelsRepository.metadataLabel(SearchUiMetadataKey.GENDER, appI18n.metadata),
              genders,
              appI18n.uiLanguage,
          )
    }
  }

  return summary.joinToString(" · ")
}

private fun <T> List<FilterOption<T>>.labelOf(value: T): String =
    firstOrNull { option -> option.value == value }?.label ?: value.toString()

internal fun parseSearchFormFromUrl(url: String, fallbackQuery: String): SearchFormState {
  val rawQuery = url.substringAfter('?', "").substringBefore('#')
  val params: Map<String, List<String>> =
      rawQuery
          .split('&')
          .asSequence()
          .map { token -> token.trim() }
          .filter { token -> token.isNotBlank() }
          .map { token ->
            val keyRaw = token.substringBefore('=')
            val valueRaw = token.substringAfter('=', "")
            keyRaw.decodeURLQueryComponent() to valueRaw.decodeURLQueryComponent()
          }
          .groupBy(keySelector = { pair -> pair.first }, valueTransform = { pair -> pair.second })

  fun first(name: String): String = params[name]?.firstOrNull().orEmpty()
  fun firstInt(name: String, default: Int): Int = first(name).toIntOrNull() ?: default
  fun has(name: String): Boolean = params.containsKey(name)
  fun boolFlag(name: String): Boolean = first(name) == "1"

  val defaults = SearchFormState()
  val query = first("q").ifBlank { fallbackQuery.trim() }
  val selectedGenders = parseGendersFromQuery(query)

  val ratingKeys = listOf("rating-general", "rating-mature", "rating-adult")
  val hasAnyRatingFlag = ratingKeys.any(::has)
  val typeKeys =
      listOf(
          "type-art",
          "type-music",
          "type-flash",
          "type-story",
          "type-photo",
          "type-poetry",
      )
  val hasAnyTypeFlag = typeKeys.any(::has)

  return SearchFormState(
      query = query.trim(),
      category = firstInt("category", defaults.category),
      type = firstInt("arttype", defaults.type),
      species = firstInt("species", defaults.species),
      orderBy = first("order-by").ifBlank { defaults.orderBy },
      orderDirection = first("order-direction").ifBlank { defaults.orderDirection },
      range = first("range").ifBlank { defaults.range },
      rangeFrom = first("range_from"),
      rangeTo = first("range_to"),
      ratingGeneral = if (hasAnyRatingFlag) boolFlag("rating-general") else defaults.ratingGeneral,
      ratingMature = if (hasAnyRatingFlag) boolFlag("rating-mature") else defaults.ratingMature,
      ratingAdult = if (hasAnyRatingFlag) boolFlag("rating-adult") else defaults.ratingAdult,
      typeArt = if (hasAnyTypeFlag) boolFlag("type-art") else defaults.typeArt,
      typeMusic = if (hasAnyTypeFlag) boolFlag("type-music") else defaults.typeMusic,
      typeFlash = if (hasAnyTypeFlag) boolFlag("type-flash") else defaults.typeFlash,
      typeStory = if (hasAnyTypeFlag) boolFlag("type-story") else defaults.typeStory,
      typePhoto = if (hasAnyTypeFlag) boolFlag("type-photo") else defaults.typePhoto,
      typePoetry = if (hasAnyTypeFlag) boolFlag("type-poetry") else defaults.typePoetry,
      selectedGenders = selectedGenders,
  )
}
