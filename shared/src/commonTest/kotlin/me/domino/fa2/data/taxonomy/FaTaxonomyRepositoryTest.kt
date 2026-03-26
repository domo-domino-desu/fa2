package me.domino.fa2.data.taxonomy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.MetadataDisplayMode
import me.domino.fa2.data.settings.TranslationTargetLanguage
import me.domino.fa2.data.settings.UiLanguageSetting
import me.domino.fa2.i18n.AppI18nSnapshot
import me.domino.fa2.i18n.SystemLanguageProvider

class FaTaxonomyRepositoryTest {
  @Test
  fun loads_expected_examples_and_order() = runTest {
    val repository = FaTaxonomyRepository()

    repository.ensureLoaded()

    assertEquals(32, repository.findCategoryIdByEnglishLabel("Food / Recipes"))
    assertTrue(!repository.categoryDisplayNameById(32).isNullOrBlank())
    assertEquals(13, repository.findTypeIdByEnglishLabel("Tutorials"))
    assertEquals(3, repository.findTypeIdByEnglishLabel("Animal related (non-anthro)"))
    assertEquals(5001, repository.findSpeciesIdByEnglishLabel("Alien (Other)"))

    val speciesGroups = repository.speciesOptionGroups()
    assertTrue(speciesGroups.isNotEmpty())
    assertEquals("", speciesGroups.first().label)
    assertEquals(1, speciesGroups.first().options.single().value)
    assertTrue(speciesGroups.first().options.single().label.isNotBlank())

    val speciesGroup = assertNotNull(repository.speciesGroupById(1))
    assertEquals("sg_ungrouped_0", speciesGroup.key)

    val categoryGroups = repository.categoryOptionGroups()
    assertTrue(categoryGroups.size >= 2)
    assertEquals(1, categoryGroups[0].options.first().value)
    assertEquals(37, categoryGroups[1].options.first().value)
    assertEquals("image", repository.categoryCardIconByTag("c_wallpaper"))
    assertEquals("category", repository.categoryCardIconByTag("c_other"))
  }

  @Test
  fun translatedMetadataFollowsUiLanguageInsteadOfTranslationTargetLanguage() = runTest {
    val repository = FaTaxonomyRepository()

    repository.ensureLoaded()

    val enMetadata =
        AppI18nSnapshot.from(
                settings =
                    AppSettings(
                        uiLanguage = UiLanguageSetting.EN,
                        translationEnabled = true,
                        translationTargetLanguage = TranslationTargetLanguage.ZH_CN,
                        metadataDisplayMode = MetadataDisplayMode.TRANSLATED,
                    ),
                systemLanguageProvider = FakeSystemLanguageProvider("zh-Hans"),
            )
            .metadata
    val zhMetadata =
        AppI18nSnapshot.from(
                settings =
                    AppSettings(
                        uiLanguage = UiLanguageSetting.ZH_HANS,
                        translationEnabled = true,
                        translationTargetLanguage = TranslationTargetLanguage.EN,
                        metadataDisplayMode = MetadataDisplayMode.TRANSLATED,
                    ),
                systemLanguageProvider = FakeSystemLanguageProvider("en"),
            )
            .metadata

    assertEquals("Artwork (Digital)", repository.categoryDisplayNameById(2, enMetadata))
    assertEquals("数字绘画", repository.categoryDisplayNameById(2, zhMetadata))
  }

  @Test
  fun disabledTranslationForcesMetadataBackToEnglish() = runTest {
    val repository = FaTaxonomyRepository()

    repository.ensureLoaded()

    val metadata =
        AppI18nSnapshot.from(
                settings =
                    AppSettings(
                        uiLanguage = UiLanguageSetting.ZH_HANS,
                        translationEnabled = false,
                        translationTargetLanguage = TranslationTargetLanguage.ZH_CN,
                        metadataDisplayMode = MetadataDisplayMode.TRANSLATED,
                    ),
                systemLanguageProvider = FakeSystemLanguageProvider("zh-Hans"),
            )
            .metadata

    assertEquals("Artwork (Digital)", repository.categoryDisplayNameById(2, metadata))
  }
}

private class FakeSystemLanguageProvider(private val languageTag: String) : SystemLanguageProvider {
  override fun currentLanguageTag(): String = languageTag
}
