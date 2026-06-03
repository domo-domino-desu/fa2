package me.domino.fa2.ui.pages.settings

import fa2.shared.generated.resources.*
import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.BlockedSubmissionPagerMode
import me.domino.fa2.data.settings.BlockedSubmissionWaterfallMode
import me.domino.fa2.data.settings.DownloadFileNameMode
import me.domino.fa2.data.settings.DownloadSubfolderMode
import me.domino.fa2.data.settings.MetadataDisplayMode
import me.domino.fa2.data.settings.OpenAiTranslationConfig
import me.domino.fa2.data.settings.ThemeMode
import me.domino.fa2.data.settings.TranslationProvider
import me.domino.fa2.data.settings.TranslationTargetLanguage
import me.domino.fa2.data.settings.UiLanguageSetting
import me.domino.fa2.i18n.appString
import me.domino.fa2.i18n.mustBeInRangeText

/** 设置页面的草稿状态，持有用户编辑中的各项配置输入值。 */
internal data class SettingsDraft(
    /** UI 语言设置。 */
    val uiLanguage: UiLanguageSetting,
    /** 是否启用翻译功能。 */
    val translationEnabled: Boolean,
    /** 翻译目标语言。 */
    val translationTargetLanguage: TranslationTargetLanguage,
    /** 元数据展示模式。 */
    val metadataDisplayMode: MetadataDisplayMode,
    /** 翻译服务提供商。 */
    val translationProvider: TranslationProvider,
    /** 主题模式。 */
    val themeMode: ThemeMode,
    /** 瀑布流中屏蔽作品的展示模式。 */
    val blockedSubmissionWaterfallMode: BlockedSubmissionWaterfallMode,
    /** 翻页器中屏蔽作品的展示模式。 */
    val blockedSubmissionPagerMode: BlockedSubmissionPagerMode,
    /** 是否在瀑布流中返回当前作品位置。 */
    val returnToCurrentSubmissionInWaterfall: Boolean,
    /** 下载保存路径。 */
    val downloadSavePath: String,
    /** 是否允许下载文件被媒体索引。 */
    val downloadAllowMediaIndexing: Boolean,
    /** 下载子目录模式。 */
    val downloadSubfolderMode: DownloadSubfolderMode,
    /** 下载文件名模式。 */
    val downloadFileNameMode: DownloadFileNameMode,
    /** 自定义下载文件名模板字符串。 */
    val downloadCustomFileNameTemplate: String,
    /** 关注推荐每页数量的输入字符串。 */
    val watchRecommendationPageSizeInput: String,
    /** 翻译分块词数上限的输入字符串。 */
    val chunkWordLimitInput: String,
    /** 翻译最大并发数的输入字符串。 */
    val maxConcurrencyInput: String,
    /** 瀑布流最小卡片宽度（dp）的输入字符串。 */
    val waterfallMinCardWidthInput: String,
    /** OpenAI 兼容接口的 Base URL。 */
    val openAiBaseUrl: String,
    /** OpenAI 兼容接口的 API Key。 */
    val openAiApiKey: String,
    /** OpenAI 兼容接口使用的模型名称。 */
    val openAiModel: String,
    /** OpenAI 兼容接口的翻译提示词模板。 */
    val openAiPromptTemplate: String,
) {
  /** 将草稿转换为 AppSettings，输入值不合法时返回 null。 */
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
        downloadSavePath = downloadSavePath,
        downloadAllowMediaIndexing = downloadAllowMediaIndexing,
        downloadSubfolderMode = downloadSubfolderMode,
        downloadFileNameMode = downloadFileNameMode,
        downloadCustomFileNameTemplate = downloadCustomFileNameTemplate,
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
    val metadataDisplayLabel = appString(Res.string.field_translation)
    val blurInWaterfallsLabel = appString(Res.string.blur_blocked_submissions_in_waterfalls)
    val blurInDetailPagesLabel = appString(Res.string.blur_blocked_submissions_in_detail_pages)
    val downloadSubfolderModeLabel = appString(Res.string.download_subfolder_mode)
    val downloadFileNameModeLabel = appString(Res.string.download_file_name_mode)
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
    if (downloadSubfolderMode !in AppSettings.supportedDownloadSubfolderModes) {
      return appString(Res.string.unsupported_setting, downloadSubfolderModeLabel)
    }
    if (downloadFileNameMode !in AppSettings.supportedDownloadFileNameModes) {
      return appString(Res.string.unsupported_setting, downloadFileNameModeLabel)
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
            downloadSavePath = settings.downloadSavePath,
            downloadAllowMediaIndexing = settings.downloadAllowMediaIndexing,
            downloadSubfolderMode = settings.downloadSubfolderMode,
            downloadFileNameMode = settings.downloadFileNameMode,
            downloadCustomFileNameTemplate = settings.downloadCustomFileNameTemplate,
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
