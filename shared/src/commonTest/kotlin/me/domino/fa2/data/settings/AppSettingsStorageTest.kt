package me.domino.fa2.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.anifantakis.lib.ksafe.KSafe
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.local.KeyValueStorage
import okio.FileSystem
import okio.Path.Companion.toPath

class AppSettingsStorageTest {
  @Test
  fun watchRecommendationPageSizeUsesDefaultAndNormalization() = runTest {
    val randomSuffix = Random.nextLong().toString().replace('-', '0')
    val tempPath =
        "${FileSystem.SYSTEM_TEMPORARY_DIRECTORY}/fa2-settings-$randomSuffix.preferences_pb"
            .toPath()
    val dataStore = PreferenceDataStoreFactory.createWithPath(produceFile = { tempPath })
    val keyValueStorage = KeyValueStorage(dataStore)
    val secretVault = KSafe(fileName = "fa2_settings_secret_$randomSuffix")
    val storage = AppSettingsStorage(kv = keyValueStorage, secretVault = secretVault)

    assertEquals(
        AppSettings.defaultWatchRecommendationPageSize,
        storage.load().watchRecommendationPageSize,
    )

    storage.save(AppSettings(watchRecommendationPageSize = 999))

    assertEquals(
        AppSettings.maxWatchRecommendationPageSize,
        storage.load().watchRecommendationPageSize,
    )
  }

  @Test
  fun savesOpenAiApiKeyOnlyToSecretVault() = runTest {
    val randomSuffix = Random.nextLong().toString().replace('-', '0')
    val tempPath =
        "${FileSystem.SYSTEM_TEMPORARY_DIRECTORY}/fa2-settings-$randomSuffix.preferences_pb"
            .toPath()
    val dataStore = PreferenceDataStoreFactory.createWithPath(produceFile = { tempPath })
    val keyValueStorage = KeyValueStorage(dataStore)
    val secretVault = KSafe(fileName = "fa2_settings_secret_$randomSuffix")
    val storage = AppSettingsStorage(kv = keyValueStorage, secretVault = secretVault)

    storage.save(
        AppSettings(
            translationProvider = TranslationProvider.OPENAI_COMPATIBLE,
            openAiTranslationConfig =
                OpenAiTranslationConfig(
                    baseUrl = "https://api.openai.com/v1",
                    apiKey = "sk-test-secret",
                    model = "gpt-4o-mini",
                    promptTemplate = "prompt",
                ),
        )
    )

    val legacyValue =
        dataStore.data.first()[stringPreferencesKey(AppSettingsStorage.KEY_OPENAI_API_KEY)]
    assertNull(legacyValue)
    assertEquals(
        "sk-test-secret",
        secretVault.get(AppSettingsStorage.KEY_SECRET_OPENAI_API_KEY, defaultValue = ""),
    )
  }

  @Test
  fun ignoresLegacyDatastoreOpenAiApiKeyWhenSecretVaultIsEmpty() = runTest {
    val randomSuffix = Random.nextLong().toString().replace('-', '0')
    val tempPath =
        "${FileSystem.SYSTEM_TEMPORARY_DIRECTORY}/fa2-settings-$randomSuffix.preferences_pb"
            .toPath()
    val dataStore = PreferenceDataStoreFactory.createWithPath(produceFile = { tempPath })
    val keyValueStorage = KeyValueStorage(dataStore)
    val secretVault = KSafe(fileName = "fa2_settings_secret_$randomSuffix")
    val storage = AppSettingsStorage(kv = keyValueStorage, secretVault = secretVault)

    keyValueStorage.save(AppSettingsStorage.KEY_OPENAI_API_KEY, "legacy-key")

    val loaded = storage.load()

    assertEquals("", loaded.openAiTranslationConfig.apiKey)
    assertNull(secretVault.getKeyInfo(AppSettingsStorage.KEY_SECRET_OPENAI_API_KEY))
  }
}
