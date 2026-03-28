package me.domino.fa2.data.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 全局设置服务。 */
class AppSettingsService(private val storage: AppSettingsStorage) {
  private val mutex = Mutex()
  private var loaded: Boolean = false

  private val mutableSettings = MutableStateFlow(AppSettings())
  private val mutableLoaded = MutableStateFlow(false)

  val settings: StateFlow<AppSettings> = mutableSettings.asStateFlow()
  val isLoaded: StateFlow<Boolean> = mutableLoaded.asStateFlow()

  suspend fun ensureLoaded() {
    mutex.withLock {
      if (loaded) {
        mutableLoaded.value = true
        return
      }
      val persisted = storage.load()
      val normalized = AppSettings.normalize(persisted)
      mutableSettings.value = normalized
      if (normalized != persisted) {
        storage.save(normalized)
      }
      loaded = true
      mutableLoaded.value = true
    }
  }

  suspend fun updateSettings(next: AppSettings) {
    ensureLoaded()
    mutex.withLock {
      val normalized = AppSettings.normalize(next)
      if (normalized == mutableSettings.value) return
      storage.save(normalized)
      mutableSettings.value = normalized
    }
  }

  suspend fun updateUiLanguage(uiLanguage: UiLanguageSetting) {
    updateSettings(mutableSettings.value.copy(uiLanguage = uiLanguage))
  }

  suspend fun updateTranslationEnabled(enabled: Boolean) {
    updateSettings(mutableSettings.value.copy(translationEnabled = enabled))
  }

  suspend fun updateTranslationTargetLanguage(language: TranslationTargetLanguage) {
    updateSettings(mutableSettings.value.copy(translationTargetLanguage = language))
  }

  suspend fun updateMetadataDisplayMode(mode: MetadataDisplayMode) {
    updateSettings(mutableSettings.value.copy(metadataDisplayMode = mode))
  }

  suspend fun updateTranslationProvider(provider: TranslationProvider) {
    updateSettings(mutableSettings.value.copy(translationProvider = provider))
  }

  suspend fun updateOpenAiTranslationConfig(config: OpenAiTranslationConfig) {
    updateSettings(
        mutableSettings.value.copy(
            openAiTranslationConfig = OpenAiTranslationConfig.normalize(config)
        )
    )
  }

  suspend fun updateChunkWordLimit(wordLimit: Int) {
    updateSettings(mutableSettings.value.copy(translationChunkWordLimit = wordLimit))
  }

  suspend fun updateMaxConcurrency(maxConcurrency: Int) {
    updateSettings(mutableSettings.value.copy(translationMaxConcurrency = maxConcurrency))
  }

  suspend fun updateThemeMode(themeMode: ThemeMode) {
    updateSettings(mutableSettings.value.copy(themeMode = themeMode))
  }

  suspend fun updateWaterfallMinCardWidthDp(minWidthDp: Int) {
    updateSettings(mutableSettings.value.copy(waterfallMinCardWidthDp = minWidthDp))
  }

  suspend fun updateBlockedSubmissionWaterfallMode(mode: BlockedSubmissionWaterfallMode) {
    updateSettings(mutableSettings.value.copy(blockedSubmissionWaterfallMode = mode))
  }

  suspend fun updateBlockedSubmissionPagerMode(mode: BlockedSubmissionPagerMode) {
    updateSettings(mutableSettings.value.copy(blockedSubmissionPagerMode = mode))
  }

  suspend fun updateReturnToCurrentSubmissionInWaterfall(enabled: Boolean) {
    updateSettings(mutableSettings.value.copy(returnToCurrentSubmissionInWaterfall = enabled))
  }

  suspend fun updateWatchRecommendationPageSize(pageSize: Int) {
    updateSettings(mutableSettings.value.copy(watchRecommendationPageSize = pageSize))
  }
}
