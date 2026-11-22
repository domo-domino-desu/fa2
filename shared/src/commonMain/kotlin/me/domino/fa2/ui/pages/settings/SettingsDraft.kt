package me.domino.fa2.ui.pages.settings

import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.BlockedSubmissionPagerMode
import me.domino.fa2.data.settings.BlockedSubmissionWaterfallMode
import me.domino.fa2.data.settings.DownloadFileNameMode
import me.domino.fa2.data.settings.DownloadSubfolderMode
import me.domino.fa2.data.settings.IndexTranslateModel
import me.domino.fa2.data.settings.MetadataDisplayMode
import me.domino.fa2.data.settings.OpenAiTranslationConfig
import me.domino.fa2.data.settings.ThemeMode
import me.domino.fa2.data.settings.TranslationProvider
import me.domino.fa2.data.settings.TranslationTargetLanguage
import me.domino.fa2.data.settings.UiLanguageSetting

internal enum class SettingsField {
  ChunkWordLimit,
  MaxConcurrency,
  WaterfallMinColumnWidth,
  WatchRecommendationPageSize,
  ThemeMode,
  AppLanguage,
  TranslationTargetLanguage,
  MetadataDisplay,
  BlurInWaterfalls,
  BlurInDetailPages,
  DownloadSubfolderMode,
  DownloadFileNameMode,
  BaseUrl,
  ApiKey,
  Model,
  PromptTemplate,
}

internal sealed interface SettingsValidationIssue {
  val field: SettingsField

  data class MustBeNumber(override val field: SettingsField) : SettingsValidationIssue

  data class Unsupported(override val field: SettingsField) : SettingsValidationIssue

  data class OutOfRange(
      override val field: SettingsField,
      val min: Int,
      val max: Int,
      val suffix: String = "",
  ) : SettingsValidationIssue

  data class CannotBeEmpty(override val field: SettingsField) : SettingsValidationIssue

  data class MustStartWithHttp(override val field: SettingsField) : SettingsValidationIssue
}

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
    val indexTranslateModel: IndexTranslateModel = IndexTranslateModel.default,
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
        indexTranslateModel = indexTranslateModel,
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

  fun validate(): SettingsValidationIssue? {
    val chunkWordLimit =
        chunkWordLimitInput.toIntOrNull()
            ?: return SettingsValidationIssue.MustBeNumber(SettingsField.ChunkWordLimit)
    val maxConcurrency =
        maxConcurrencyInput.toIntOrNull()
            ?: return SettingsValidationIssue.MustBeNumber(SettingsField.MaxConcurrency)
    val waterfallWidth =
        waterfallMinCardWidthInput.toIntOrNull()
            ?: return SettingsValidationIssue.MustBeNumber(SettingsField.WaterfallMinColumnWidth)
    val watchRecommendationPageSize =
        watchRecommendationPageSizeInput.toIntOrNull()
            ?: return SettingsValidationIssue.MustBeNumber(
                SettingsField.WatchRecommendationPageSize
            )

    if (themeMode !in AppSettings.supportedThemeModes) {
      return SettingsValidationIssue.Unsupported(SettingsField.ThemeMode)
    }
    if (uiLanguage !in AppSettings.supportedUiLanguages) {
      return SettingsValidationIssue.Unsupported(SettingsField.AppLanguage)
    }
    if (translationTargetLanguage !in AppSettings.supportedTranslationTargetLanguages) {
      return SettingsValidationIssue.Unsupported(SettingsField.TranslationTargetLanguage)
    }
    if (metadataDisplayMode !in AppSettings.supportedMetadataDisplayModes) {
      return SettingsValidationIssue.Unsupported(SettingsField.MetadataDisplay)
    }
    if (blockedSubmissionWaterfallMode !in AppSettings.supportedBlockedSubmissionWaterfallModes) {
      return SettingsValidationIssue.Unsupported(SettingsField.BlurInWaterfalls)
    }
    if (blockedSubmissionPagerMode !in AppSettings.supportedBlockedSubmissionPagerModes) {
      return SettingsValidationIssue.Unsupported(SettingsField.BlurInDetailPages)
    }
    if (downloadSubfolderMode !in AppSettings.supportedDownloadSubfolderModes) {
      return SettingsValidationIssue.Unsupported(SettingsField.DownloadSubfolderMode)
    }
    if (downloadFileNameMode !in AppSettings.supportedDownloadFileNameModes) {
      return SettingsValidationIssue.Unsupported(SettingsField.DownloadFileNameMode)
    }

    if (
        chunkWordLimit !in
            AppSettings.minTranslationChunkWordLimit..AppSettings.maxTranslationChunkWordLimit
    ) {
      return SettingsValidationIssue.OutOfRange(
          SettingsField.ChunkWordLimit,
          min = AppSettings.minTranslationChunkWordLimit,
          max = AppSettings.maxTranslationChunkWordLimit,
      )
    }

    if (
        maxConcurrency !in
            AppSettings.minTranslationMaxConcurrency..AppSettings.maxTranslationMaxConcurrency
    ) {
      return SettingsValidationIssue.OutOfRange(
          SettingsField.MaxConcurrency,
          min = AppSettings.minTranslationMaxConcurrency,
          max = AppSettings.maxTranslationMaxConcurrency,
      )
    }

    if (
        waterfallWidth !in
            AppSettings.minWaterfallMinCardWidthDp..AppSettings.maxWaterfallMinCardWidthDp
    ) {
      return SettingsValidationIssue.OutOfRange(
          SettingsField.WaterfallMinColumnWidth,
          min = AppSettings.minWaterfallMinCardWidthDp,
          max = AppSettings.maxWaterfallMinCardWidthDp,
          suffix = "dp",
      )
    }

    if (
        watchRecommendationPageSize !in
            AppSettings.minWatchRecommendationPageSize..AppSettings.maxWatchRecommendationPageSize
    ) {
      return SettingsValidationIssue.OutOfRange(
          SettingsField.WatchRecommendationPageSize,
          min = AppSettings.minWatchRecommendationPageSize,
          max = AppSettings.maxWatchRecommendationPageSize,
      )
    }

    if (translationEnabled && translationProvider == TranslationProvider.OPENAI_COMPATIBLE) {
      val baseUrl = openAiBaseUrl.trim()
      if (baseUrl.isBlank()) {
        return SettingsValidationIssue.CannotBeEmpty(SettingsField.BaseUrl)
      }
      if (!baseUrl.startsWith("https://") && !baseUrl.startsWith("http://")) {
        return SettingsValidationIssue.MustStartWithHttp(SettingsField.BaseUrl)
      }
      if (openAiApiKey.isBlank()) {
        return SettingsValidationIssue.CannotBeEmpty(SettingsField.ApiKey)
      }
      if (openAiModel.isBlank()) {
        return SettingsValidationIssue.CannotBeEmpty(SettingsField.Model)
      }
      if (openAiPromptTemplate.isBlank()) {
        return SettingsValidationIssue.CannotBeEmpty(SettingsField.PromptTemplate)
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
            indexTranslateModel = settings.indexTranslateModel,
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
