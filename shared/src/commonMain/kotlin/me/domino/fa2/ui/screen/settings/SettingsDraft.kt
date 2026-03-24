package me.domino.fa2.ui.screen.settings

import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.BlockedSubmissionPagerMode
import me.domino.fa2.data.settings.BlockedSubmissionWaterfallMode
import me.domino.fa2.data.settings.OpenAiTranslationConfig
import me.domino.fa2.data.settings.ThemeMode
import me.domino.fa2.data.settings.TranslationProvider

internal data class SettingsDraft(
    val translationProvider: TranslationProvider,
    val themeMode: ThemeMode,
    val blockedSubmissionWaterfallMode: BlockedSubmissionWaterfallMode,
    val blockedSubmissionPagerMode: BlockedSubmissionPagerMode,
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

        return AppSettings(
            translationProvider = translationProvider,
            themeMode = themeMode,
            blockedSubmissionWaterfallMode = blockedSubmissionWaterfallMode,
            blockedSubmissionPagerMode = blockedSubmissionPagerMode,
            translationChunkWordLimit = chunkWordLimit,
            translationMaxConcurrency = maxConcurrency,
            waterfallMinCardWidthDp = waterfallWidth,
            openAiTranslationConfig = OpenAiTranslationConfig(
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
        val chunkWordLimit = chunkWordLimitInput.toIntOrNull()
            ?: return "Chunk Word Limit 必须是数字"
        val maxConcurrency = maxConcurrencyInput.toIntOrNull()
            ?: return "Max Concurrency 必须是数字"
        val waterfallWidth = waterfallMinCardWidthInput.toIntOrNull()
            ?: return "瀑布流最小列宽必须是数字"

        if (themeMode !in AppSettings.supportedThemeModes) {
            return "主题模式不受支持"
        }
        if (blockedSubmissionWaterfallMode !in AppSettings.supportedBlockedSubmissionWaterfallModes) {
            return "瀑布流屏蔽策略不受支持"
        }
        if (blockedSubmissionPagerMode !in AppSettings.supportedBlockedSubmissionPagerModes) {
            return "左右滑屏蔽策略不受支持"
        }

        if (chunkWordLimit !in AppSettings.minTranslationChunkWordLimit..AppSettings.maxTranslationChunkWordLimit) {
            return "Chunk Word Limit 需在 ${AppSettings.minTranslationChunkWordLimit}-${AppSettings.maxTranslationChunkWordLimit}"
        }

        if (maxConcurrency !in AppSettings.minTranslationMaxConcurrency..AppSettings.maxTranslationMaxConcurrency) {
            return "Max Concurrency 需在 ${AppSettings.minTranslationMaxConcurrency}-${AppSettings.maxTranslationMaxConcurrency}"
        }

        if (waterfallWidth !in AppSettings.minWaterfallMinCardWidthDp..AppSettings.maxWaterfallMinCardWidthDp) {
            return "瀑布流最小列宽需在 ${AppSettings.minWaterfallMinCardWidthDp}-${AppSettings.maxWaterfallMinCardWidthDp} dp"
        }

        if (translationProvider == TranslationProvider.OPENAI_COMPATIBLE) {
            val baseUrl = openAiBaseUrl.trim()
            if (baseUrl.isBlank()) return "Base URL 不能为空"
            if (!baseUrl.startsWith("https://") && !baseUrl.startsWith("http://")) {
                return "Base URL 必须以 http:// 或 https:// 开头"
            }
            if (openAiApiKey.isBlank()) return "API Key 不能为空"
            if (openAiModel.isBlank()) return "Model 不能为空"
            if (openAiPromptTemplate.isBlank()) return "Prompt Template 不能为空"
        }

        return null
    }

    companion object {
        fun fromSettings(settings: AppSettings): SettingsDraft =
            SettingsDraft(
                translationProvider = settings.translationProvider,
                themeMode = settings.themeMode,
                blockedSubmissionWaterfallMode = settings.blockedSubmissionWaterfallMode,
                blockedSubmissionPagerMode = settings.blockedSubmissionPagerMode,
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
