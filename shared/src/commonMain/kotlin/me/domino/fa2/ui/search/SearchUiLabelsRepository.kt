package me.domino.fa2.ui.search

import fa2.shared.generated.resources.Res
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.domino.fa2.i18n.AppLanguage
import me.domino.fa2.i18n.MetadataDisplayPreferences
import me.domino.fa2.i18n.defaultMetadataDisplayPreferences
import me.domino.fa2.i18n.localizedFor
import me.domino.fa2.i18n.localizedOrOriginal
import me.domino.fa2.util.logging.FaLog

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
      when (key) {
        SearchUiTextKey.SUMMARY_SORT ->
            preparedCatalog?.summary?.sort.orEmpty().localizedFor(language, fallback = "Sort")
        SearchUiTextKey.SUMMARY_DIRECTION ->
            preparedCatalog
                ?.summary
                ?.direction
                .orEmpty()
                .localizedFor(
                    language,
                    fallback = "Direction",
                )
        SearchUiTextKey.SUMMARY_DATE ->
            preparedCatalog?.summary?.date.orEmpty().localizedFor(language, fallback = "Date")
        SearchUiTextKey.SUMMARY_GENDERS ->
            preparedCatalog?.summary?.genders.orEmpty().localizedFor(language, fallback = "Gender")
        SearchUiTextKey.SUMMARY_SUBMISSION_TYPES ->
            preparedCatalog
                ?.summary
                ?.submissionTypes
                .orEmpty()
                .localizedFor(
                    language,
                    fallback = "Submission Types",
                )
        SearchUiTextKey.FILTER_SORT_CRITERIA ->
            preparedCatalog
                ?.filters
                ?.sortCriteria
                .orEmpty()
                .localizedFor(
                    language,
                    fallback = "Sort Criteria",
                )
        SearchUiTextKey.FILTER_SORT_DIRECTION ->
            preparedCatalog
                ?.filters
                ?.sortDirection
                .orEmpty()
                .localizedFor(
                    language,
                    fallback = "Order",
                )
        SearchUiTextKey.FILTER_DATE ->
            preparedCatalog
                ?.filters
                ?.dateFilter
                .orEmpty()
                .localizedFor(
                    language,
                    fallback = "Date Filter",
                )
        SearchUiTextKey.FILTER_GENDER_KEYWORDS ->
            preparedCatalog
                ?.filters
                ?.genderKeywords
                .orEmpty()
                .localizedFor(
                    language,
                    fallback = "Gender Keywords",
                )
        SearchUiTextKey.FILTER_SUBMISSION_TYPES ->
            preparedCatalog
                ?.filters
                ?.submissionTypes
                .orEmpty()
                .localizedFor(
                    language,
                    fallback = "Submission Types",
                )
        SearchUiTextKey.PHRASE_ANY ->
            preparedCatalog?.phrases?.any.orEmpty().localizedFor(language, fallback = "Any")
        SearchUiTextKey.PHRASE_NONE ->
            preparedCatalog?.phrases?.none.orEmpty().localizedFor(language, fallback = "None")
        SearchUiTextKey.PHRASE_FROM ->
            preparedCatalog?.phrases?.from.orEmpty().localizedFor(language, fallback = "From")
        SearchUiTextKey.PHRASE_TO ->
            preparedCatalog?.phrases?.to.orEmpty().localizedFor(language, fallback = "To")
        SearchUiTextKey.LABEL_VALUE_SEPARATOR ->
            preparedCatalog
                ?.punctuation
                ?.labelValueSeparator
                .orEmpty()
                .localizedFor(
                    language,
                    fallback = ": ",
                )
      }

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
      when (key) {
        SearchUiOptionKey.ORDER_BY ->
            when (value) {
              "relevancy" -> if (language == AppLanguage.EN) "Relevancy" else "相关度"
              "date" -> if (language == AppLanguage.EN) "Date" else "日期"
              "popularity" -> if (language == AppLanguage.EN) "Popularity" else "热度"
              else -> value
            }
        SearchUiOptionKey.ORDER_DIRECTION ->
            when (value) {
              "desc" -> if (language == AppLanguage.EN) "Descending" else "降序"
              "asc" -> if (language == AppLanguage.EN) "Ascending" else "升序"
              else -> value
            }
        SearchUiOptionKey.RANGE ->
            when (value) {
              "1day" -> if (language == AppLanguage.EN) "1 day" else "1 天"
              "3days" -> if (language == AppLanguage.EN) "3 days" else "3 天"
              "7days" -> if (language == AppLanguage.EN) "7 days" else "7 天"
              "30days" -> if (language == AppLanguage.EN) "30 days" else "30 天"
              "90days" -> if (language == AppLanguage.EN) "90 days" else "90 天"
              "1year" -> if (language == AppLanguage.EN) "1 year" else "1 年"
              "3years" -> if (language == AppLanguage.EN) "3 years" else "3 年"
              "5years" -> if (language == AppLanguage.EN) "5 years" else "5 年"
              "all" -> if (language == AppLanguage.EN) "All Time" else "全部时间"
              "manual" -> if (language == AppLanguage.EN) "Custom" else "自定义"
              else -> value
            }
        SearchUiOptionKey.GENDER ->
            when (value) {
              "" -> if (language == AppLanguage.EN) "Any" else "任意"
              "male" -> if (language == AppLanguage.EN) "Male" else "雄性"
              "female" -> if (language == AppLanguage.EN) "Female" else "雌性"
              "trans_male" -> if (language == AppLanguage.EN) "Trans_Male" else "跨性别雄性"
              "trans_female" -> if (language == AppLanguage.EN) "Trans_Female" else "跨性别雌性"
              "intersex" -> if (language == AppLanguage.EN) "Intersex" else "两性兼有"
              "non_binary" -> if (language == AppLanguage.EN) "Non_Binary" else "非二元性别"
              else -> value
            }
        SearchUiOptionKey.RATING ->
            when (value) {
              "general" -> if (language == AppLanguage.EN) "General" else "全年龄"
              "mature" -> if (language == AppLanguage.EN) "Mature" else "成熟"
              "adult" -> if (language == AppLanguage.EN) "Adult" else "成人"
              "none" -> if (language == AppLanguage.EN) "None" else "无"
              else -> value
            }
        SearchUiOptionKey.SUBMISSION_TYPE ->
            when (value) {
              "art" -> if (language == AppLanguage.EN) "Art" else "艺术"
              "music" -> if (language == AppLanguage.EN) "Music" else "音乐"
              "flash" -> "Flash"
              "story" -> if (language == AppLanguage.EN) "Story" else "故事"
              "photo" -> if (language == AppLanguage.EN) "Photos" else "摄影"
              "poetry" -> if (language == AppLanguage.EN) "Poetry" else "诗歌"
              else -> value
            }
      }
}

private fun Map<String, String>.englishLabel(): String = localizedFor(AppLanguage.EN)

private fun Map<String, Map<String, String>>.labelOf(
    key: String,
    language: AppLanguage,
    fallback: String,
): String = get(key).orEmpty().localizedFor(language, fallback)
