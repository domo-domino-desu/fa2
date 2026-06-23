package me.domino.fa2.ui.i18n.search

import fa2.shared.generated.resources.Res
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.domino.fa2.data.i18n.AppLanguage
import me.domino.fa2.data.i18n.MetadataDisplayPreferences
import me.domino.fa2.data.i18n.defaultMetadataDisplayPreferences
import me.domino.fa2.data.i18n.localizedFor
import me.domino.fa2.data.i18n.localizedOrOriginal
import me.domino.fa2.utils.logging.FaLog

private const val searchUiLabelsResourcePath = "files/fa-search.json"

private val searchUiLabelsJson: Json = Json {
  ignoreUnknownKeys = true
  isLenient = true
  explicitNulls = false
}

@Serializable
data class SearchUiLabelsCatalog(
    val version: Int = 1,
    val summary: SearchSummaryLabels = SearchSummaryLabels(),
    val filters: SearchFilterLabels = SearchFilterLabels(),
    val metadata: SearchMetadataLabels = SearchMetadataLabels(),
    val options: SearchOptionLabels = SearchOptionLabels(),
    val phrases: SearchPhraseLabels = SearchPhraseLabels(),
    val punctuation: SearchPunctuationLabels = SearchPunctuationLabels(),
)

@Serializable
data class SearchSummaryLabels(
    val sort: Map<String, String> = emptyMap(),
    val direction: Map<String, String> = emptyMap(),
    val date: Map<String, String> = emptyMap(),
    val genders: Map<String, String> = emptyMap(),
    val submissionTypes: Map<String, String> = emptyMap(),
)

@Serializable
data class SearchFilterLabels(
    val sortCriteria: Map<String, String> = emptyMap(),
    val sortDirection: Map<String, String> = emptyMap(),
    val dateFilter: Map<String, String> = emptyMap(),
    val genderKeywords: Map<String, String> = emptyMap(),
    val submissionTypes: Map<String, String> = emptyMap(),
)

@Serializable
data class SearchMetadataLabels(
    val category: Map<String, String> = emptyMap(),
    val type: Map<String, String> = emptyMap(),
    val species: Map<String, String> = emptyMap(),
    val gender: Map<String, String> = emptyMap(),
    val rating: Map<String, String> = emptyMap(),
)

@Serializable
data class SearchOptionLabels(
    val orderBy: Map<String, Map<String, String>> = emptyMap(),
    val orderDirection: Map<String, Map<String, String>> = emptyMap(),
    val range: Map<String, Map<String, String>> = emptyMap(),
    val genders: Map<String, Map<String, String>> = emptyMap(),
    val ratings: Map<String, Map<String, String>> = emptyMap(),
    val submissionTypes: Map<String, Map<String, String>> = emptyMap(),
)

@Serializable
data class SearchPhraseLabels(
    val any: Map<String, String> = emptyMap(),
    val none: Map<String, String> = emptyMap(),
    val from: Map<String, String> = emptyMap(),
    val to: Map<String, String> = emptyMap(),
)

@Serializable
data class SearchPunctuationLabels(
    val labelValueSeparator: Map<String, String> = emptyMap(),
)

class SearchUiLabelsRepository {
  private val log = FaLog.withTag("SearchUiLabelsRepository")
  private val loadMutex = Mutex()
  private val mutableCatalog = MutableStateFlow<SearchUiLabelsCatalog?>(null)

  @Volatile private var preparedCatalog: SearchUiLabelsCatalog? = null

  val catalog: StateFlow<SearchUiLabelsCatalog?> = mutableCatalog.asStateFlow()

  suspend fun ensureLoaded() {
    if (preparedCatalog != null) return

    loadMutex.withLock {
      if (preparedCatalog != null) return

      runCatching {
            searchUiLabelsJson.decodeFromString<SearchUiLabelsCatalog>(
                Res.readBytes(searchUiLabelsResourcePath).decodeToString()
            )
          }
          .onSuccess { loaded ->
            preparedCatalog = loaded
            mutableCatalog.value = loaded
            log.i { "search-ui-labels -> 加载成功(version=${loaded.version})" }
          }
          .onFailure { error -> log.e(error) { "search-ui-labels -> 加载失败" } }
    }
  }

  fun text(key: SearchUiTextKey, language: AppLanguage = AppLanguage.ZH_HANS): String =
      textMap(key).localizedFor(language, fallback = textFallback(key))

  fun metadataLabel(
      key: SearchUiMetadataKey,
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences,
  ): String =
      metadata.localizedOrOriginal(
          localized = metadataMap(key),
          original = metadataMap(key).englishLabel(),
      )

  fun optionLabel(
      key: SearchUiOptionKey,
      value: String,
      language: AppLanguage = AppLanguage.ZH_HANS,
  ): String =
      optionMap(key)
          .labelOf(
              key = value,
              language = language,
              fallback = optionFallback(key = key, value = value, language = language),
          )

  fun metadataOptionLabel(
      key: SearchUiOptionKey,
      value: String,
      metadata: MetadataDisplayPreferences = defaultMetadataDisplayPreferences,
  ): String {
    val localized = optionMap(key)[value].orEmpty()
    val fallback = optionFallback(key = key, value = value, language = AppLanguage.EN)
    return metadata.localizedOrOriginal(
        localized,
        original = localized.englishLabel().ifBlank { fallback },
    )
  }

  fun formatLabelValue(
      label: String,
      value: String,
      language: AppLanguage = AppLanguage.ZH_HANS,
  ): String = "$label${text(SearchUiTextKey.LABEL_VALUE_SEPARATOR, language)}$value"

  private fun metadataMap(key: SearchUiMetadataKey): Map<String, String> =
      when (key) {
        SearchUiMetadataKey.CATEGORY -> preparedCatalog?.metadata?.category.orEmpty()
        SearchUiMetadataKey.TYPE -> preparedCatalog?.metadata?.type.orEmpty()
        SearchUiMetadataKey.SPECIES -> preparedCatalog?.metadata?.species.orEmpty()
        SearchUiMetadataKey.GENDER -> preparedCatalog?.metadata?.gender.orEmpty()
        SearchUiMetadataKey.RATING -> preparedCatalog?.metadata?.rating.orEmpty()
      }

  private fun textMap(key: SearchUiTextKey): Map<String, String> =
      when (key) {
        SearchUiTextKey.SUMMARY_SORT -> preparedCatalog?.summary?.sort.orEmpty()
        SearchUiTextKey.SUMMARY_DIRECTION -> preparedCatalog?.summary?.direction.orEmpty()
        SearchUiTextKey.SUMMARY_DATE -> preparedCatalog?.summary?.date.orEmpty()
        SearchUiTextKey.SUMMARY_GENDERS -> preparedCatalog?.summary?.genders.orEmpty()
        SearchUiTextKey.SUMMARY_SUBMISSION_TYPES ->
            preparedCatalog?.summary?.submissionTypes.orEmpty()
        SearchUiTextKey.FILTER_SORT_CRITERIA -> preparedCatalog?.filters?.sortCriteria.orEmpty()
        SearchUiTextKey.FILTER_SORT_DIRECTION -> preparedCatalog?.filters?.sortDirection.orEmpty()
        SearchUiTextKey.FILTER_DATE -> preparedCatalog?.filters?.dateFilter.orEmpty()
        SearchUiTextKey.FILTER_GENDER_KEYWORDS -> preparedCatalog?.filters?.genderKeywords.orEmpty()
        SearchUiTextKey.FILTER_SUBMISSION_TYPES ->
            preparedCatalog?.filters?.submissionTypes.orEmpty()
        SearchUiTextKey.PHRASE_ANY -> preparedCatalog?.phrases?.any.orEmpty()
        SearchUiTextKey.PHRASE_NONE -> preparedCatalog?.phrases?.none.orEmpty()
        SearchUiTextKey.PHRASE_FROM -> preparedCatalog?.phrases?.from.orEmpty()
        SearchUiTextKey.PHRASE_TO -> preparedCatalog?.phrases?.to.orEmpty()
        SearchUiTextKey.LABEL_VALUE_SEPARATOR ->
            preparedCatalog?.punctuation?.labelValueSeparator.orEmpty()
      }

  private fun optionMap(key: SearchUiOptionKey): Map<String, Map<String, String>> =
      when (key) {
        SearchUiOptionKey.ORDER_BY -> preparedCatalog?.options?.orderBy.orEmpty()
        SearchUiOptionKey.ORDER_DIRECTION -> preparedCatalog?.options?.orderDirection.orEmpty()
        SearchUiOptionKey.RANGE -> preparedCatalog?.options?.range.orEmpty()
        SearchUiOptionKey.GENDER -> preparedCatalog?.options?.genders.orEmpty()
        SearchUiOptionKey.RATING -> preparedCatalog?.options?.ratings.orEmpty()
        SearchUiOptionKey.SUBMISSION_TYPE -> preparedCatalog?.options?.submissionTypes.orEmpty()
      }

  private fun optionFallback(key: SearchUiOptionKey, value: String, language: AppLanguage): String =
      optionMap(key)[value].orEmpty().localizedFor(language, fallback = value)
}

private fun Map<String, String>.englishLabel(): String = localizedFor(AppLanguage.EN)

private fun textFallback(key: SearchUiTextKey): String =
    if (key == SearchUiTextKey.LABEL_VALUE_SEPARATOR) ": " else key.name

private fun Map<String, Map<String, String>>.labelOf(
    key: String,
    language: AppLanguage,
    fallback: String,
): String = get(key).orEmpty().localizedFor(language, fallback)
