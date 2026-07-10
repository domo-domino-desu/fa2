package me.domino.fa2.data.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 全局设置服务。 */
class AppSettingsService(private val storage: AppSettingsStore) {
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
    mutex.withLock { persistLocked(next) }
  }

  /** Atomically derives and persists a settings update from the latest committed snapshot. */
  suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
    ensureLoaded()
    mutex.withLock { persistLocked(transform(mutableSettings.value)) }
  }

  suspend fun updateUiLanguage(uiLanguage: UiLanguageSetting) {
    updateSettings { current -> current.copy(uiLanguage = uiLanguage) }
  }

  suspend fun updateTranslationEnabled(enabled: Boolean) {
    updateSettings { current -> current.copy(translationEnabled = enabled) }
  }

  suspend fun updateTranslationTargetLanguage(language: TranslationTargetLanguage) {
    updateSettings { current -> current.copy(translationTargetLanguage = language) }
  }

  suspend fun updateMetadataDisplayMode(mode: MetadataDisplayMode) {
    updateSettings { current -> current.copy(metadataDisplayMode = mode) }
  }

  suspend fun updateTranslationProvider(provider: TranslationProvider) {
    updateSettings { current -> current.copy(translationProvider = provider) }
  }

  suspend fun updateOpenAiTranslationConfig(config: OpenAiTranslationConfig) {
    updateSettings { current ->
      current.copy(openAiTranslationConfig = OpenAiTranslationConfig.normalize(config))
    }
  }

  suspend fun updateChunkWordLimit(wordLimit: Int) {
    updateSettings { current -> current.copy(translationChunkWordLimit = wordLimit) }
  }

  suspend fun updateMaxConcurrency(maxConcurrency: Int) {
    updateSettings { current -> current.copy(translationMaxConcurrency = maxConcurrency) }
  }

  suspend fun updateThemeMode(themeMode: ThemeMode) {
    updateSettings { current -> current.copy(themeMode = themeMode) }
  }

  suspend fun updateWaterfallMinCardWidthDp(minWidthDp: Int) {
    updateSettings { current -> current.copy(waterfallMinCardWidthDp = minWidthDp) }
  }

  suspend fun updateBlockedSubmissionWaterfallMode(mode: BlockedSubmissionWaterfallMode) {
    updateSettings { current -> current.copy(blockedSubmissionWaterfallMode = mode) }
  }

  suspend fun updateBlockedSubmissionPagerMode(mode: BlockedSubmissionPagerMode) {
    updateSettings { current -> current.copy(blockedSubmissionPagerMode = mode) }
  }

  suspend fun updateReturnToCurrentSubmissionInWaterfall(enabled: Boolean) {
    updateSettings { current -> current.copy(returnToCurrentSubmissionInWaterfall = enabled) }
  }

  suspend fun updateDownloadSavePath(path: String) {
    updateSettings { current -> current.copy(downloadSavePath = path) }
  }

  suspend fun updateDownloadAllowMediaIndexing(allow: Boolean) {
    updateSettings { current -> current.copy(downloadAllowMediaIndexing = allow) }
  }

  suspend fun updateDownloadSubfolderMode(mode: DownloadSubfolderMode) {
    updateSettings { current -> current.copy(downloadSubfolderMode = mode) }
  }

  suspend fun updateDownloadFileNameMode(mode: DownloadFileNameMode) {
    updateSettings { current -> current.copy(downloadFileNameMode = mode) }
  }

  suspend fun updateDownloadCustomFileNameTemplate(template: String) {
    updateSettings { current -> current.copy(downloadCustomFileNameTemplate = template) }
  }

  suspend fun updateWatchRecommendationPageSize(pageSize: Int) {
    updateSettings { current -> current.copy(watchRecommendationPageSize = pageSize) }
  }

  suspend fun updateLogLevel(logLevel: LogLevelSetting) {
    updateSettings { current -> current.copy(logLevel = logLevel) }
  }

  private suspend fun persistLocked(next: AppSettings) {
    val normalized = AppSettings.normalize(next)
    if (normalized == mutableSettings.value) return
    storage.save(normalized)
    mutableSettings.value = normalized
  }
}
