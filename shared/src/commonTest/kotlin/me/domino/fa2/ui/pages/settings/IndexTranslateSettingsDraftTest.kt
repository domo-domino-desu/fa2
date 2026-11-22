package me.domino.fa2.ui.pages.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.IndexTranslateModel
import me.domino.fa2.data.settings.TranslationProvider

class IndexTranslateSettingsDraftTest {
  @Test
  fun modelsSurviveDraftSaveAndRestoringPersistedSettings() {
    for (provider in listOf(TranslationProvider.GOOGLE, TranslationProvider.INDEX_TRANSLATE)) {
      for (model in IndexTranslateModel.entries) {
        val original = AppSettings(translationProvider = provider, indexTranslateModel = model)
        val draft = SettingsDraft.fromSettings(original)
        assertEquals(model, draft.indexTranslateModel)
        assertEquals(model, assertNotNull(draft.toAppSettingsOrNull()).indexTranslateModel)
        val edited = draft.copy(indexTranslateModel = IndexTranslateModel.TWO_B)
        assertEquals(
            IndexTranslateModel.TWO_B,
            assertNotNull(edited.toAppSettingsOrNull()).indexTranslateModel,
        )
        assertEquals(model, SettingsDraft.fromSettings(original).indexTranslateModel)
      }
    }
  }
}
