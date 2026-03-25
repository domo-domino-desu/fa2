package me.domino.fa2.data.search

import fa2.shared.generated.resources.Res
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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
    val options: SearchOptionLabels = SearchOptionLabels(),
    val phrases: SearchPhraseLabels = SearchPhraseLabels(),
    val punctuation: SearchPunctuationLabels = SearchPunctuationLabels(),
)

@Serializable
data class SearchSummaryLabels(
    val sort: String = "排序",
    val direction: String = "方向",
    val date: String = "日期",
    val genders: String = "性别",
    val submissionTypes: String = "投稿类型",
)

@Serializable
data class SearchFilterLabels(
    val sortCriteria: String = "排序依据",
    val sortDirection: String = "排序方向",
    val dateFilter: String = "日期范围",
    val genderKeywords: String = "性别关键词",
    val submissionTypes: String = "投稿类型",
)

@Serializable
data class SearchOptionLabels(
    val orderBy: Map<String, String> = emptyMap(),
    val orderDirection: Map<String, String> = emptyMap(),
    val range: Map<String, String> = emptyMap(),
    val genders: Map<String, String> = emptyMap(),
    val submissionTypes: Map<String, String> = emptyMap(),
)

@Serializable
data class SearchPhraseLabels(
    val any: String = "任意",
    val none: String = "无",
    val from: String = "自",
    val to: String = "至",
)

@Serializable
data class SearchPunctuationLabels(
    val labelValueSeparator: String = "：",
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

  fun summarySortLabel(): String = preparedCatalog?.summary?.sort.orFallback("排序")

  fun summaryDirectionLabel(): String = preparedCatalog?.summary?.direction.orFallback("方向")

  fun summaryDateLabel(): String = preparedCatalog?.summary?.date.orFallback("日期")

  fun summaryGendersLabel(): String = preparedCatalog?.summary?.genders.orFallback("性别")

  fun summarySubmissionTypesLabel(): String =
      preparedCatalog?.summary?.submissionTypes.orFallback("投稿类型")

  fun filterSortCriteriaLabel(): String = preparedCatalog?.filters?.sortCriteria.orFallback("排序依据")

  fun filterSortDirectionLabel(): String =
      preparedCatalog?.filters?.sortDirection.orFallback("排序方向")

  fun filterDateLabel(): String = preparedCatalog?.filters?.dateFilter.orFallback("日期范围")

  fun filterGenderKeywordsLabel(): String =
      preparedCatalog?.filters?.genderKeywords.orFallback("性别关键词")

  fun filterSubmissionTypesLabel(): String =
      preparedCatalog?.filters?.submissionTypes.orFallback("投稿类型")

  fun orderByLabel(value: String): String =
      preparedCatalog
          ?.options
          ?.orderBy
          .labelOf(
              key = value,
              fallback =
                  when (value) {
                    "relevancy" -> "相关度"
                    "date" -> "日期"
                    "popularity" -> "热度"
                    else -> value
                  },
          )

  fun orderDirectionLabel(value: String): String =
      preparedCatalog
          ?.options
          ?.orderDirection
          .labelOf(
              key = value,
              fallback =
                  when (value) {
                    "desc" -> "降序"
                    "asc" -> "升序"
                    else -> value
                  },
          )

  fun rangeLabel(value: String): String =
      preparedCatalog
          ?.options
          ?.range
          .labelOf(
              key = value,
              fallback =
                  when (value) {
                    "1day" -> "1 天"
                    "3days" -> "3 天"
                    "7days" -> "7 天"
                    "30days" -> "30 天"
                    "90days" -> "90 天"
                    "1year" -> "1 年"
                    "3years" -> "3 年"
                    "5years" -> "5 年"
                    "all" -> "全部时间"
                    "manual" -> "自定义"
                    else -> value
                  },
          )

  fun genderLabel(value: String): String =
      preparedCatalog
          ?.options
          ?.genders
          .labelOf(
              key = value,
              fallback =
                  when (value) {
                    "" -> "任意"
                    "male" -> "雄性"
                    "female" -> "雌性"
                    "trans_male" -> "跨性别雄性"
                    "trans_female" -> "跨性别雌性"
                    "intersex" -> "两性兼有"
                    "non_binary" -> "非二元性别"
                    else -> value
                  },
          )

  fun anyLabel(): String = preparedCatalog?.phrases?.any.orFallback("任意")

  fun submissionTypeLabel(value: String): String =
      preparedCatalog
          ?.options
          ?.submissionTypes
          .labelOf(
              key = value,
              fallback =
                  when (value) {
                    "art" -> "艺术"
                    "music" -> "音乐"
                    "flash" -> "Flash"
                    "story" -> "故事"
                    "photo" -> "摄影"
                    "poetry" -> "诗歌"
                    else -> value
                  },
          )

  fun noneLabel(): String = preparedCatalog?.phrases?.none.orFallback("无")

  fun fromLabel(): String = preparedCatalog?.phrases?.from.orFallback("自")

  fun toLabel(): String = preparedCatalog?.phrases?.to.orFallback("至")

  fun labelValueSeparator(): String =
      preparedCatalog?.punctuation?.labelValueSeparator.orFallback("：")

  fun formatLabelValue(label: String, value: String): String =
      "$label${labelValueSeparator()}$value"
}

private fun String?.orFallback(fallback: String): String =
    this?.takeIf { it.isNotBlank() } ?: fallback

private fun Map<String, String>?.labelOf(key: String, fallback: String): String =
    this?.get(key)?.takeIf { it.isNotBlank() } ?: fallback
