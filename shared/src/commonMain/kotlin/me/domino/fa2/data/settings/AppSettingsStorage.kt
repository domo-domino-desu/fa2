package me.domino.fa2.data.settings

import me.domino.fa2.data.local.KeyValueStorage

/** 基于 KV 的设置持久化。 */
class AppSettingsStorage(private val kv: KeyValueStorage) {
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
            apiKey = kv.load(KEY_OPENAI_API_KEY).orEmpty(),
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
    kv.save(KEY_OPENAI_API_KEY, normalized.openAiTranslationConfig.apiKey)
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
  }

  companion object {
    const val KEY_UI_LANGUAGE: String = "settings.i18n.uiLanguage"
    const val KEY_TRANSLATION_ENABLED: String = "settings.translation.enabled"
    const val KEY_TRANSLATION_TARGET_LANGUAGE: String = "settings.translation.targetLanguage"
    const val KEY_METADATA_DISPLAY_MODE: String = "settings.translation.metadataDisplayMode"
    const val KEY_TRANSLATION_PROVIDER: String = "settings.submission.translation.provider"
    const val KEY_OPENAI_BASE_URL: String = "settings.submission.translation.openai.baseUrl"
    const val KEY_OPENAI_API_KEY: String = "settings.submission.translation.openai.apiKey"
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
  }
}
