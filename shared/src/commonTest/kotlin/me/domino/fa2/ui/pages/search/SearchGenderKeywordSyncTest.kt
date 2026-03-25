package me.domino.fa2.ui.pages.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.search.SearchUiLabelsRepository
import me.domino.fa2.data.taxonomy.FaTaxonomyRepository

/** Search gender 与 query 同步测试。 */
class SearchGenderKeywordSyncTest {
  @Test
  fun parsesGendersFromKeywordsSegment() {
    val genders = parseGendersFromQuery("wolf @keywords female trans_female")
    assertTrue(SearchGender.Female in genders)
    assertTrue(SearchGender.TransFemale in genders)
    assertEquals(2, genders.size)
  }

  @Test
  fun rewritesKeywordsWhenGenderSelectionChanges() {
    val rewritten =
        rewriteQueryWithGenders(
            query = "wolf @keywords female cute",
            genders = setOf(SearchGender.TransFemale),
        )
    assertEquals("wolf @keywords cute trans_female", rewritten)
  }

  @Test
  fun filterSummarySkipsDefaultConditions() = runTest {
    val taxonomyRepository = FaTaxonomyRepository()
    val searchUiLabelsRepository = SearchUiLabelsRepository()
    taxonomyRepository.ensureLoaded()
    searchUiLabelsRepository.ensureLoaded()
    val summary =
        buildSearchFiltersSummary(
            SearchFormState(),
            taxonomyRepository,
            searchUiLabelsRepository,
        )
    assertEquals("", summary)
  }

  @Test
  fun filterSummaryContainsNonDefaultConditionsOnly() = runTest {
    val taxonomyRepository = FaTaxonomyRepository()
    val searchUiLabelsRepository = SearchUiLabelsRepository()
    taxonomyRepository.ensureLoaded()
    searchUiLabelsRepository.ensureLoaded()
    val summary =
        buildSearchFiltersSummary(
            SearchFormState(
                query = "wolf",
                category = 2,
                orderBy = "date",
                range = "manual",
                rangeFrom = "2024-01-01",
                ratingAdult = false,
                selectedGenders = setOf(SearchGender.Female),
            ),
            taxonomyRepository,
            searchUiLabelsRepository,
        )

    assertTrue(summary.contains("类别：数字绘画"))
    assertTrue(
        summary.contains(
            "${searchUiLabelsRepository.summarySortLabel()}：" +
                searchUiLabelsRepository.orderByLabel("date")
        )
    )
    assertTrue(
        summary.contains(
            "${searchUiLabelsRepository.summaryDateLabel()}：" +
                searchUiLabelsRepository.rangeLabel("manual") +
                "（${searchUiLabelsRepository.fromLabel()} 2024-01-01）"
        )
    )
    assertTrue(summary.contains("分级：General, Mature"))
    assertTrue(
        summary.contains(
            "${searchUiLabelsRepository.summaryGendersLabel()}：" +
                searchUiLabelsRepository.genderLabel("female")
        )
    )
    assertTrue(!summary.contains("${searchUiLabelsRepository.summaryDirectionLabel()}："))
    assertTrue(!summary.contains("类型："))
    assertTrue(!summary.contains("物种："))
  }
}
