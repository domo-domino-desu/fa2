package me.domino.fa2.data.settings

import eu.anifantakis.lib.ksafe.KSafe
import me.domino.fa2.data.local.KeyValueStorage

/** 基于 KV 的设置持久化。 */
class AppSettingsStorage(
    private val kv: KeyValueStorage,
    private val secretVault: KSafe,
) {
  suspend fun load(): AppSettings {
    val uiLanguage =
        UiLanguageSetting.fromPersistedValue(kv.load(KEY_UI_LANGUAGE))
            ?: AppSettings.defaultUiLanguage

    val translationEnabled =
        kv.load(KEY_TRANSLATION_ENABLED)?.toBooleanStrictOrNull()
            ?: AppSettings.defaultTranslationEnabled

    val translationTargetLanguage =
        TranslationTargetLanguage.fromPersistedValue(kv.load(KEY_TRANSLATION_TARGET_LANGUAGE))
            ?: AppSettings.defaultTranslationTargetLanguage

    val metadataDisplayMode =
        MetadataDisplayMode.fromPersistedValue(kv.load(KEY_METADATA_DISPLAY_MODE))
            ?: AppSettings.defaultMetadataDisplayMode

    val provider =
        TranslationProvider.fromPersistedValue(kv.load(KEY_TRANSLATION_PROVIDER))
            ?: AppSettings.defaultTranslationProvider

    val rawOpenAiConfig =
        OpenAiTranslationConfig(
            baseUrl = kv.load(KEY_OPENAI_BASE_URL) ?: OpenAiTranslationConfig.defaultBaseUrl,
            apiKey = secretVault.get(KEY_SECRET_OPENAI_API_KEY, defaultValue = ""),
            model = kv.load(KEY_OPENAI_MODEL) ?: OpenAiTranslationConfig.defaultModel,
            promptTemplate =
                kv.load(KEY_OPENAI_PROMPT) ?: OpenAiTranslationConfig.defaultPromptTemplate,
        )

    val rawChunkWordLimit =
        kv.load(KEY_TRANSLATION_CHUNK_WORD_LIMIT)?.toIntOrNull()
            ?: AppSettings.defaultTranslationChunkWordLimit

    val rawMaxConcurrency =
        kv.load(KEY_TRANSLATION_MAX_CONCURRENCY)?.toIntOrNull()
            ?: AppSettings.defaultTranslationMaxConcurrency

    val themeMode =
        ThemeMode.fromPersistedValue(kv.load(KEY_THEME_MODE)) ?: AppSettings.defaultThemeMode

    val rawWaterfallMinCardWidthDp =
        kv.load(KEY_WATERFALL_MIN_CARD_WIDTH_DP)?.toIntOrNull()
            ?: AppSettings.defaultWaterfallMinCardWidthDp

    val blockedSubmissionWaterfallMode =
        BlockedSubmissionWaterfallMode.fromPersistedValue(
            kv.load(KEY_BLOCKED_SUBMISSION_WATERFALL_MODE)
        ) ?: AppSettings.defaultBlockedSubmissionWaterfallMode

    val blockedSubmissionPagerMode =
        BlockedSubmissionPagerMode.fromPersistedValue(kv.load(KEY_BLOCKED_SUBMISSION_PAGER_MODE))
            ?: AppSettings.defaultBlockedSubmissionPagerMode
    val returnToCurrentSubmissionInWaterfall =
        kv.load(KEY_RETURN_TO_CURRENT_SUBMISSION_IN_WATERFALL)?.toBooleanStrictOrNull()
            ?: AppSettings.defaultReturnToCurrentSubmissionInWaterfall
    val downloadSavePath = kv.load(KEY_DOWNLOAD_SAVE_PATH) ?: AppSettings.defaultDownloadSavePath
    val downloadAllowMediaIndexing =
        kv.load(KEY_DOWNLOAD_ALLOW_MEDIA_INDEXING)?.toBooleanStrictOrNull()
            ?: AppSettings.defaultDownloadAllowMediaIndexing
    val downloadSubfolderMode =
        DownloadSubfolderMode.fromPersistedValue(kv.load(KEY_DOWNLOAD_SUBFOLDER_MODE))
            ?: AppSettings.defaultDownloadSubfolderMode
    val downloadFileNameMode =
        DownloadFileNameMode.fromPersistedValue(kv.load(KEY_DOWNLOAD_FILE_NAME_MODE))
            ?: AppSettings.defaultDownloadFileNameMode
    val downloadCustomFileNameTemplate =
        kv.load(KEY_DOWNLOAD_CUSTOM_FILE_NAME_TEMPLATE)
            ?: AppSettings.defaultDownloadCustomFileNameTemplate
    val rawWatchRecommendationPageSize =
        kv.load(KEY_WATCH_RECOMMENDATION_PAGE_SIZE)?.toIntOrNull()
            ?: AppSettings.defaultWatchRecommendationPageSize

    return AppSettings.normalize(
        AppSettings(
            uiLanguage = uiLanguage,
            translationEnabled = translationEnabled,
            translationTargetLanguage = translationTargetLanguage,
            metadataDisplayMode = metadataDisplayMode,
            translationProvider = provider,
            openAiTranslationConfig = rawOpenAiConfig,
            translationChunkWordLimit = rawChunkWordLimit,
            translationMaxConcurrency = rawMaxConcurrency,
            themeMode = themeMode,
            waterfallMinCardWidthDp = rawWaterfallMinCardWidthDp,
            blockedSubmissionWaterfallMode = blockedSubmissionWaterfallMode,
            blockedSubmissionPagerMode = blockedSubmissionPagerMode,
            returnToCurrentSubmissionInWaterfall = returnToCurrentSubmissionInWaterfall,
            downloadSavePath = downloadSavePath,
            downloadAllowMediaIndexing = downloadAllowMediaIndexing,
            downloadSubfolderMode = downloadSubfolderMode,
            downloadFileNameMode = downloadFileNameMode,
            downloadCustomFileNameTemplate = downloadCustomFileNameTemplate,
            watchRecommendationPageSize = rawWatchRecommendationPageSize,
        )
    )
  }

  suspend fun save(settings: AppSettings) {
    val normalized = AppSettings.normalize(settings)
    kv.save(KEY_UI_LANGUAGE, normalized.uiLanguage.persistedValue)
    kv.save(KEY_TRANSLATION_ENABLED, normalized.translationEnabled.toString())
    kv.save(
        KEY_TRANSLATION_TARGET_LANGUAGE,
        normalized.translationTargetLanguage.persistedValue,
    )
    kv.save(KEY_METADATA_DISPLAY_MODE, normalized.metadataDisplayMode.persistedValue)
    kv.save(KEY_TRANSLATION_PROVIDER, normalized.translationProvider.persistedValue)
    kv.save(KEY_OPENAI_BASE_URL, normalized.openAiTranslationConfig.baseUrl)
    val apiKey = normalized.openAiTranslationConfig.apiKey.trim()
    if (apiKey.isBlank()) {
      secretVault.delete(KEY_SECRET_OPENAI_API_KEY)
    } else {
      secretVault.put(key = KEY_SECRET_OPENAI_API_KEY, value = apiKey)
    }
    kv.save(KEY_OPENAI_MODEL, normalized.openAiTranslationConfig.model)
    kv.save(KEY_OPENAI_PROMPT, normalized.openAiTranslationConfig.promptTemplate)
    kv.save(KEY_TRANSLATION_CHUNK_WORD_LIMIT, normalized.translationChunkWordLimit.toString())
    kv.save(KEY_TRANSLATION_MAX_CONCURRENCY, normalized.translationMaxConcurrency.toString())
    kv.save(KEY_THEME_MODE, normalized.themeMode.persistedValue)
    kv.save(KEY_WATERFALL_MIN_CARD_WIDTH_DP, normalized.waterfallMinCardWidthDp.toString())
    kv.save(
        KEY_BLOCKED_SUBMISSION_WATERFALL_MODE,
        normalized.blockedSubmissionWaterfallMode.persistedValue,
    )
    kv.save(
        KEY_BLOCKED_SUBMISSION_PAGER_MODE,
        normalized.blockedSubmissionPagerMode.persistedValue,
    )
    kv.save(
        KEY_RETURN_TO_CURRENT_SUBMISSION_IN_WATERFALL,
        normalized.returnToCurrentSubmissionInWaterfall.toString(),
    )
    kv.save(KEY_DOWNLOAD_SAVE_PATH, normalized.downloadSavePath)
    kv.save(
        KEY_DOWNLOAD_ALLOW_MEDIA_INDEXING,
        normalized.downloadAllowMediaIndexing.toString(),
    )
    kv.save(KEY_DOWNLOAD_SUBFOLDER_MODE, normalized.downloadSubfolderMode.persistedValue)
    kv.save(KEY_DOWNLOAD_FILE_NAME_MODE, normalized.downloadFileNameMode.persistedValue)
    kv.save(
        KEY_DOWNLOAD_CUSTOM_FILE_NAME_TEMPLATE,
        normalized.downloadCustomFileNameTemplate,
    )
    kv.save(
        KEY_WATCH_RECOMMENDATION_PAGE_SIZE,
        normalized.watchRecommendationPageSize.toString(),
    )
  }

  companion object {
    const val KEY_UI_LANGUAGE: String = "settings.i18n.uiLanguage"
    const val KEY_TRANSLATION_ENABLED: String = "settings.translation.enabled"
    const val KEY_TRANSLATION_TARGET_LANGUAGE: String = "settings.translation.targetLanguage"
    const val KEY_METADATA_DISPLAY_MODE: String = "settings.translation.metadataDisplayMode"
    const val KEY_TRANSLATION_PROVIDER: String = "settings.submission.translation.provider"
    const val KEY_OPENAI_BASE_URL: String = "settings.submission.translation.openai.baseUrl"
    const val KEY_OPENAI_API_KEY: String = "settings.submission.translation.openai.apiKey"
    const val KEY_SECRET_OPENAI_API_KEY: String = "settings_secret_openai_api_key"
    const val KEY_OPENAI_MODEL: String = "settings.submission.translation.openai.model"
    const val KEY_OPENAI_PROMPT: String = "settings.submission.translation.openai.prompt"
    const val KEY_TRANSLATION_CHUNK_WORD_LIMIT: String =
        "settings.submission.translation.chunkWordLimit"
    const val KEY_TRANSLATION_MAX_CONCURRENCY: String =
        "settings.submission.translation.maxConcurrency"
    const val KEY_THEME_MODE: String = "settings.appearance.themeMode"
    const val KEY_WATERFALL_MIN_CARD_WIDTH_DP: String =
        "settings.appearance.waterfallMinCardWidthDp"
    const val KEY_BLOCKED_SUBMISSION_WATERFALL_MODE: String =
        "settings.blockedSubmission.waterfall.mode"
    const val KEY_BLOCKED_SUBMISSION_PAGER_MODE: String = "settings.blockedSubmission.pager.mode"
    const val KEY_RETURN_TO_CURRENT_SUBMISSION_IN_WATERFALL: String =
        "settings.submission.returnToCurrentInWaterfall"
    const val KEY_DOWNLOAD_SAVE_PATH: String = "settings.download.savePath"
    const val KEY_DOWNLOAD_ALLOW_MEDIA_INDEXING: String = "settings.download.allowMediaIndexing"
    const val KEY_DOWNLOAD_SUBFOLDER_MODE: String = "settings.download.subfolderMode"
    const val KEY_DOWNLOAD_FILE_NAME_MODE: String = "settings.download.fileNameMode"
    const val KEY_DOWNLOAD_CUSTOM_FILE_NAME_TEMPLATE: String =
        "settings.download.customFileNameTemplate"
    const val KEY_WATCH_RECOMMENDATION_PAGE_SIZE: String = "settings.watchRecommendation.pageSize"
  }
}
