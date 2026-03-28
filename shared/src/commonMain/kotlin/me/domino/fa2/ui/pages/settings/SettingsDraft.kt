package me.domino.fa2.ui.pages.settings

import fa2.shared.generated.resources.*
import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.BlockedSubmissionPagerMode
import me.domino.fa2.data.settings.BlockedSubmissionWaterfallMode
import me.domino.fa2.data.settings.MetadataDisplayMode
import me.domino.fa2.data.settings.OpenAiTranslationConfig
import me.domino.fa2.data.settings.ThemeMode
import me.domino.fa2.data.settings.TranslationProvider
import me.domino.fa2.data.settings.TranslationTargetLanguage
import me.domino.fa2.data.settings.UiLanguageSetting
import me.domino.fa2.i18n.appString
import me.domino.fa2.i18n.mustBeInRangeText

internal data class SettingsDraft(
    val uiLanguage: UiLanguageSetting,
    val translationEnabled: Boolean,
    val translationTargetLanguage: TranslationTargetLanguage,
    val metadataDisplayMode: MetadataDisplayMode,
    val translationProvider: TranslationProvider,
    val themeMode: ThemeMode,
    val blockedSubmissionWaterfallMode: BlockedSubmissionWaterfallMode,
    val blockedSubmissionPagerMode: BlockedSubmissionPagerMode,
    val returnToCurrentSubmissionInWaterfall: Boolean,
    val watchRecommendationPageSizeInput: String,
    val chunkWordLimitInput: String,
    val maxConcurrencyInput: String,
    val waterfallMinCardWidthInput: String,
    val openAiBaseUrl: String,
    val openAiApiKey: String,
    val openAiModel: String,
    val openAiPromptTemplate: String,
) {
  fun toAppSettingsOrNull(): AppSettings? {
    val chunkWordLimit = chunkWordLimitInput.toIntOrNull() ?: return null
    val maxConcurrency = maxConcurrencyInput.toIntOrNull() ?: return null
    val waterfallWidth = waterfallMinCardWidthInput.toIntOrNull() ?: return null
    val watchRecommendationPageSize = watchRecommendationPageSizeInput.toIntOrNull() ?: return null

    return AppSettings(
        uiLanguage = uiLanguage,
        translationEnabled = translationEnabled,
        translationTargetLanguage = translationTargetLanguage,
        metadataDisplayMode = metadataDisplayMode,
        translationProvider = translationProvider,
        themeMode = themeMode,
        blockedSubmissionWaterfallMode = blockedSubmissionWaterfallMode,
        blockedSubmissionPagerMode = blockedSubmissionPagerMode,
        returnToCurrentSubmissionInWaterfall = returnToCurrentSubmissionInWaterfall,
        watchRecommendationPageSize = watchRecommendationPageSize,
        translationChunkWordLimit = chunkWordLimit,
        translationMaxConcurrency = maxConcurrency,
        waterfallMinCardWidthDp = waterfallWidth,
        openAiTranslationConfig =
            OpenAiTranslationConfig(
                baseUrl = openAiBaseUrl,
                apiKey = openAiApiKey,
                model = openAiModel,
                promptTemplate = openAiPromptTemplate,
            ),
    )
  }

  fun hasChangesComparedTo(settings: AppSettings): Boolean {
    val current = toAppSettingsOrNull() ?: return true
    return AppSettings.normalize(current) != settings
  }

  fun validate(): String? {
    val chunkWordLimitLabel = appString(Res.string.chunk_word_limit)
    val maxConcurrencyLabel = appString(Res.string.max_concurrency)
    val waterfallMinColumnWidthLabel = appString(Res.string.waterfall_min_column_width_dp)
    val watchRecommendationPageSizeLabel = appString(Res.string.watch_recommendation_page_size)
    val themeModeLabel = appString(Res.string.theme_mode)
    val appLanguageLabel = appString(Res.string.app_language)
    val translationTargetLanguageLabel = appString(Res.string.translation_target_language)
    val metadataDisplayLabel = appString(Res.string.metadata_display)
    val blurInWaterfallsLabel = appString(Res.string.blur_blocked_submissions_in_waterfalls)
    val blurInDetailPagesLabel = appString(Res.string.blur_blocked_submissions_in_detail_pages)
    val baseUrlLabel = appString(Res.string.base_url)
    val apiKeyLabel = appString(Res.string.api_key)
    val modelLabel = appString(Res.string.model)
    val promptTemplateLabel = appString(Res.string.prompt_template)

    val chunkWordLimit =
        chunkWordLimitInput.toIntOrNull()
            ?: return appString(Res.string.must_be_number, chunkWordLimitLabel)
    val maxConcurrency =
        maxConcurrencyInput.toIntOrNull()
            ?: return appString(Res.string.must_be_number, maxConcurrencyLabel)
    val waterfallWidth =
        waterfallMinCardWidthInput.toIntOrNull()
            ?: return appString(Res.string.must_be_number, waterfallMinColumnWidthLabel)
    val watchRecommendationPageSize =
        watchRecommendationPageSizeInput.toIntOrNull()
            ?: return appString(Res.string.must_be_number, watchRecommendationPageSizeLabel)

    if (themeMode !in AppSettings.supportedThemeModes) {
      return appString(Res.string.unsupported_setting, themeModeLabel)
    }
    if (uiLanguage !in AppSettings.supportedUiLanguages) {
      return appString(Res.string.unsupported_setting, appLanguageLabel)
    }
    if (translationTargetLanguage !in AppSettings.supportedTranslationTargetLanguages) {
      return appString(Res.string.unsupported_setting, translationTargetLanguageLabel)
    }
    if (metadataDisplayMode !in AppSettings.supportedMetadataDisplayModes) {
      return appString(Res.string.unsupported_setting, metadataDisplayLabel)
    }
    if (blockedSubmissionWaterfallMode !in AppSettings.supportedBlockedSubmissionWaterfallModes) {
      return appString(Res.string.unsupported_setting, blurInWaterfallsLabel)
    }
    if (blockedSubmissionPagerMode !in AppSettings.supportedBlockedSubmissionPagerModes) {
      return appString(Res.string.unsupported_setting, blurInDetailPagesLabel)
    }

    if (
        chunkWordLimit !in
            AppSettings.minTranslationChunkWordLimit..AppSettings.maxTranslationChunkWordLimit
    ) {
      return mustBeInRangeText(
          chunkWordLimitLabel,
          AppSettings.minTranslationChunkWordLimit,
          AppSettings.maxTranslationChunkWordLimit,
      )
    }

    if (
        maxConcurrency !in
            AppSettings.minTranslationMaxConcurrency..AppSettings.maxTranslationMaxConcurrency
    ) {
      return mustBeInRangeText(
          maxConcurrencyLabel,
          AppSettings.minTranslationMaxConcurrency,
          AppSettings.maxTranslationMaxConcurrency,
      )
    }

    if (
        waterfallWidth !in
            AppSettings.minWaterfallMinCardWidthDp..AppSettings.maxWaterfallMinCardWidthDp
    ) {
      return mustBeInRangeText(
          waterfallMinColumnWidthLabel,
          AppSettings.minWaterfallMinCardWidthDp,
          AppSettings.maxWaterfallMinCardWidthDp,
          suffix = "dp",
      )
    }

    if (
        watchRecommendationPageSize !in
            AppSettings.minWatchRecommendationPageSize..AppSettings.maxWatchRecommendationPageSize
    ) {
      return mustBeInRangeText(
          watchRecommendationPageSizeLabel,
          AppSettings.minWatchRecommendationPageSize,
          AppSettings.maxWatchRecommendationPageSize,
      )
    }

    if (translationEnabled && translationProvider == TranslationProvider.OPENAI_COMPATIBLE) {
      val baseUrl = openAiBaseUrl.trim()
      if (baseUrl.isBlank()) return appString(Res.string.cannot_be_empty, baseUrlLabel)
      if (!baseUrl.startsWith("https://") && !baseUrl.startsWith("http://")) {
        return appString(Res.string.must_start_with_http_or_https, baseUrlLabel)
      }
      if (openAiApiKey.isBlank()) return appString(Res.string.cannot_be_empty, apiKeyLabel)
      if (openAiModel.isBlank()) return appString(Res.string.cannot_be_empty, modelLabel)
      if (openAiPromptTemplate.isBlank()) {
        return appString(Res.string.cannot_be_empty, promptTemplateLabel)
      }
    }

    return null
  }

  companion object {
    fun fromSettings(settings: AppSettings): SettingsDraft =
        SettingsDraft(
            uiLanguage = settings.uiLanguage,
            translationEnabled = settings.translationEnabled,
            translationTargetLanguage = settings.translationTargetLanguage,
            metadataDisplayMode = settings.metadataDisplayMode,
            translationProvider = settings.translationProvider,
            themeMode = settings.themeMode,
            blockedSubmissionWaterfallMode = settings.blockedSubmissionWaterfallMode,
            blockedSubmissionPagerMode = settings.blockedSubmissionPagerMode,
            returnToCurrentSubmissionInWaterfall = settings.returnToCurrentSubmissionInWaterfall,
            watchRecommendationPageSizeInput = settings.watchRecommendationPageSize.toString(),
            chunkWordLimitInput = settings.translationChunkWordLimit.toString(),
            maxConcurrencyInput = settings.translationMaxConcurrency.toString(),
            waterfallMinCardWidthInput = settings.waterfallMinCardWidthDp.toString(),
            openAiBaseUrl = settings.openAiTranslationConfig.baseUrl,
            openAiApiKey = settings.openAiTranslationConfig.apiKey,
            openAiModel = settings.openAiTranslationConfig.model,
            openAiPromptTemplate = settings.openAiTranslationConfig.promptTemplate,
        )
  }
}
