package me.domino.fa2.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class AppSettingsServiceTest {
  @Test
  fun concurrentFieldUpdatesComposeFromLatestSnapshot() = runTest {
    val store = InMemoryAppSettingsStore()
    val service = AppSettingsService(store)

    coroutineScope {
      launch { service.updateThemeMode(ThemeMode.DARK) }
      launch { service.updateWatchRecommendationPageSize(42) }
    }

    assertEquals(ThemeMode.DARK, service.settings.value.themeMode)
    assertEquals(42, service.settings.value.watchRecommendationPageSize)
    assertEquals(service.settings.value, store.saved)
  }
}

private class InMemoryAppSettingsStore : AppSettingsStore {
  var saved: AppSettings = AppSettings()

  override suspend fun load(): AppSettings = saved

  override suspend fun save(settings: AppSettings) {
    saved = settings
  }
}
