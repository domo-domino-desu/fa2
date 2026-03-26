package me.domino.fa2.ui.pages.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.MetadataDisplayMode
import me.domino.fa2.data.settings.TranslationTargetLanguage
import me.domino.fa2.data.settings.UiLanguageSetting
import me.domino.fa2.data.taxonomy.FaTaxonomyRepository
import me.domino.fa2.i18n.AppI18nSnapshot
import me.domino.fa2.i18n.AppLanguage
import me.domino.fa2.i18n.SystemLanguageProvider
import me.domino.fa2.i18n.defaultMetadataDisplayPreferences
import me.domino.fa2.ui.search.SearchUiLabelsRepository
import me.domino.fa2.ui.search.SearchUiMetadataKey
import me.domino.fa2.ui.search.SearchUiOptionKey
import me.domino.fa2.ui.search.SearchUiTextKey

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
            AppI18nSnapshot(AppLanguage.ZH_HANS, defaultMetadataDisplayPreferences),
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
            AppI18nSnapshot(AppLanguage.ZH_HANS, defaultMetadataDisplayPreferences),
            taxonomyRepository,
            searchUiLabelsRepository,
        )

    assertTrue(summary.contains("类别：数字绘画"))
    assertTrue(
        summary.contains(
            "${searchUiLabelsRepository.text(SearchUiTextKey.SUMMARY_SORT)}：" +
                searchUiLabelsRepository.optionLabel(SearchUiOptionKey.ORDER_BY, "date")
        )
    )
    assertTrue(
        summary.contains(
            "${searchUiLabelsRepository.text(SearchUiTextKey.SUMMARY_DATE)}：" +
                searchUiLabelsRepository.optionLabel(SearchUiOptionKey.RANGE, "manual") +
                "（${searchUiLabelsRepository.text(SearchUiTextKey.PHRASE_FROM)} 2024-01-01）"
        )
    )
    assertTrue(
        summary.contains(
            searchUiLabelsRepository.formatLabelValue(
                searchUiLabelsRepository.metadataLabel(
                    SearchUiMetadataKey.RATING,
                    defaultMetadataDisplayPreferences,
                ),
                listOf(
                        searchUiLabelsRepository.metadataOptionLabel(
                            SearchUiOptionKey.RATING,
                            "general",
                            defaultMetadataDisplayPreferences,
                        ),
                        searchUiLabelsRepository.metadataOptionLabel(
                            SearchUiOptionKey.RATING,
                            "mature",
                            defaultMetadataDisplayPreferences,
                        ),
                    )
                    .joinToString(", "),
                AppLanguage.ZH_HANS,
            )
        )
    )
    assertTrue(
        summary.contains(
            searchUiLabelsRepository.formatLabelValue(
                searchUiLabelsRepository.metadataLabel(
                    SearchUiMetadataKey.GENDER,
                    defaultMetadataDisplayPreferences,
                ),
                searchUiLabelsRepository.metadataOptionLabel(
                    SearchUiOptionKey.GENDER,
                    "female",
                    defaultMetadataDisplayPreferences,
                ),
                AppLanguage.ZH_HANS,
            )
        )
    )
    assertTrue(
        !summary.contains("${searchUiLabelsRepository.text(SearchUiTextKey.SUMMARY_DIRECTION)}：")
    )
    assertTrue(!summary.contains("类型："))
    assertTrue(!summary.contains("物种："))
  }

  @Test
  fun filterSummaryUsesUiLanguageForMetadataWhenTranslated() = runTest {
    val taxonomyRepository = FaTaxonomyRepository()
    val searchUiLabelsRepository = SearchUiLabelsRepository()
    taxonomyRepository.ensureLoaded()
    searchUiLabelsRepository.ensureLoaded()

    val englishSummary =
        buildSearchFiltersSummary(
            SearchFormState(
                category = 2,
                selectedGenders = setOf(SearchGender.Female),
                ratingAdult = false,
            ),
            AppI18nSnapshot.from(
                settings =
                    AppSettings(
                        uiLanguage = UiLanguageSetting.EN,
                        translationEnabled = true,
                        translationTargetLanguage = TranslationTargetLanguage.ZH_CN,
                        metadataDisplayMode = MetadataDisplayMode.TRANSLATED,
                    ),
                systemLanguageProvider = TestSystemLanguageProvider("zh-Hans"),
            ),
            taxonomyRepository,
            searchUiLabelsRepository,
        )
    val chineseSummary =
        buildSearchFiltersSummary(
            SearchFormState(
                category = 2,
                selectedGenders = setOf(SearchGender.Female),
                ratingAdult = false,
            ),
            AppI18nSnapshot.from(
                settings =
                    AppSettings(
                        uiLanguage = UiLanguageSetting.ZH_HANS,
                        translationEnabled = true,
                        translationTargetLanguage = TranslationTargetLanguage.EN,
                        metadataDisplayMode = MetadataDisplayMode.TRANSLATED,
                    ),
                systemLanguageProvider = TestSystemLanguageProvider("en"),
            ),
            taxonomyRepository,
            searchUiLabelsRepository,
        )

    assertTrue(englishSummary.contains("Category: Artwork (Digital)"))
    assertTrue(englishSummary.contains("Gender: Female"))
    assertFalse(englishSummary.contains("类别"))
    assertTrue(chineseSummary.contains("类别：数字绘画"))
    assertTrue(chineseSummary.contains("性别：雌性"))
  }

  @Test
  fun filterSummaryFallsBackToEnglishMetadataWhenTranslationDisabled() = runTest {
    val taxonomyRepository = FaTaxonomyRepository()
    val searchUiLabelsRepository = SearchUiLabelsRepository()
    taxonomyRepository.ensureLoaded()
    searchUiLabelsRepository.ensureLoaded()

    val summary =
        buildSearchFiltersSummary(
            SearchFormState(
                category = 2,
                selectedGenders = setOf(SearchGender.Female),
                ratingAdult = false,
            ),
            AppI18nSnapshot.from(
                settings =
                    AppSettings(
                        uiLanguage = UiLanguageSetting.ZH_HANS,
                        translationEnabled = false,
                        translationTargetLanguage = TranslationTargetLanguage.ZH_CN,
                        metadataDisplayMode = MetadataDisplayMode.TRANSLATED,
                    ),
                systemLanguageProvider = TestSystemLanguageProvider("zh-Hans"),
            ),
            taxonomyRepository,
            searchUiLabelsRepository,
        )

    assertTrue(summary.contains("Category"))
    assertTrue(summary.contains("Artwork (Digital)"))
    assertTrue(summary.contains("Gender"))
    assertTrue(summary.contains("Female"))
  }
}

private class TestSystemLanguageProvider(private val languageTag: String) : SystemLanguageProvider {
  override fun currentLanguageTag(): String = languageTag
}
