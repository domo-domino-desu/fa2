package me.domino.fa2.data.settings

/** 全局应用设置。 */
data class AppSettings(
  val translationProvider: TranslationProvider = defaultTranslationProvider,
  val openAiTranslationConfig: OpenAiTranslationConfig = OpenAiTranslationConfig(),
  val translationChunkWordLimit: Int = defaultTranslationChunkWordLimit,
  val translationMaxConcurrency: Int = defaultTranslationMaxConcurrency,
  val themeMode: ThemeMode = defaultThemeMode,
  val waterfallMinCardWidthDp: Int = defaultWaterfallMinCardWidthDp,
  val blockedSubmissionWaterfallMode: BlockedSubmissionWaterfallMode =
    defaultBlockedSubmissionWaterfallMode,
  val blockedSubmissionPagerMode: BlockedSubmissionPagerMode = defaultBlockedSubmissionPagerMode,
) {
  companion object {
    val supportedTranslationProviders: List<TranslationProvider> =
      listOf(
        TranslationProvider.GOOGLE,
        TranslationProvider.MICROSOFT,
        TranslationProvider.OPENAI_COMPATIBLE,
      )
    val supportedThemeModes: List<ThemeMode> =
      listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
    val supportedBlockedSubmissionWaterfallModes: List<BlockedSubmissionWaterfallMode> =
      listOf(BlockedSubmissionWaterfallMode.SHOW, BlockedSubmissionWaterfallMode.BLUR_THEN_OPEN)
    val supportedBlockedSubmissionPagerModes: List<BlockedSubmissionPagerMode> =
      listOf(BlockedSubmissionPagerMode.SHOW, BlockedSubmissionPagerMode.BLUR_THEN_OPEN)

    val defaultTranslationProvider: TranslationProvider = TranslationProvider.GOOGLE
    const val defaultTranslationChunkWordLimit: Int = 1024
    const val defaultTranslationMaxConcurrency: Int = 3
    val defaultThemeMode: ThemeMode = ThemeMode.SYSTEM
    const val defaultWaterfallMinCardWidthDp: Int = 220
    val defaultBlockedSubmissionWaterfallMode: BlockedSubmissionWaterfallMode =
      BlockedSubmissionWaterfallMode.SHOW
    val defaultBlockedSubmissionPagerMode: BlockedSubmissionPagerMode =
      BlockedSubmissionPagerMode.SHOW

    const val minTranslationChunkWordLimit: Int = 50
    const val maxTranslationChunkWordLimit: Int = 10000

    const val minTranslationMaxConcurrency: Int = 1
    const val maxTranslationMaxConcurrency: Int = 8

    const val minWaterfallMinCardWidthDp: Int = 120
    const val maxWaterfallMinCardWidthDp: Int = 480

    fun normalize(raw: AppSettings): AppSettings =
      raw.copy(
        openAiTranslationConfig = OpenAiTranslationConfig.normalize(raw.openAiTranslationConfig),
        translationChunkWordLimit =
          raw.translationChunkWordLimit.coerceIn(
            minTranslationChunkWordLimit,
            maxTranslationChunkWordLimit,
          ),
        translationMaxConcurrency =
          raw.translationMaxConcurrency.coerceIn(
            minTranslationMaxConcurrency,
            maxTranslationMaxConcurrency,
          ),
        waterfallMinCardWidthDp =
          raw.waterfallMinCardWidthDp.coerceIn(
            minWaterfallMinCardWidthDp,
            maxWaterfallMinCardWidthDp,
          ),
        blockedSubmissionWaterfallMode =
          raw.blockedSubmissionWaterfallMode.takeIf { mode ->
            mode in supportedBlockedSubmissionWaterfallModes
          } ?: defaultBlockedSubmissionWaterfallMode,
        blockedSubmissionPagerMode =
          raw.blockedSubmissionPagerMode.takeIf { mode ->
            mode in supportedBlockedSubmissionPagerModes
          } ?: defaultBlockedSubmissionPagerMode,
      )
  }
}

/** 描述翻译 Provider。 */
enum class TranslationProvider(val persistedValue: String) {
  GOOGLE("google"),
  MICROSOFT("microsoft"),
  OPENAI_COMPATIBLE("openai_compatible");

  companion object {
    fun fromPersistedValue(raw: String?): TranslationProvider? = entries.firstOrNull {
      it.persistedValue == raw?.trim()
    }
  }
}

/** 主题模式。 */
enum class ThemeMode(val persistedValue: String) {
  SYSTEM("system"),
  LIGHT("light"),
  DARK("dark");

  companion object {
    fun fromPersistedValue(raw: String?): ThemeMode? = entries.firstOrNull {
      it.persistedValue == raw?.trim()
    }
  }
}

/** 瀑布流中的被屏蔽投稿显示策略。 */
enum class BlockedSubmissionWaterfallMode(val persistedValue: String) {
  SHOW("show"),
  BLUR_THEN_OPEN("blur_then_open");

  companion object {
    fun fromPersistedValue(raw: String?): BlockedSubmissionWaterfallMode? = entries.firstOrNull {
      it.persistedValue == raw?.trim()
    }
  }
}

/** 左右滑中的被屏蔽投稿策略。 */
enum class BlockedSubmissionPagerMode(val persistedValue: String) {
  SHOW("show"),
  BLUR_THEN_OPEN("blur_then_open");

  companion object {
    fun fromPersistedValue(raw: String?): BlockedSubmissionPagerMode? = entries.firstOrNull {
      it.persistedValue == raw?.trim()
    }
  }
}

/** OpenAI 兼容翻译配置。 */
data class OpenAiTranslationConfig(
  val baseUrl: String = defaultBaseUrl,
  val apiKey: String = "",
  val model: String = defaultModel,
  val promptTemplate: String = defaultPromptTemplate,
) {
  companion object {
    const val defaultBaseUrl: String = "https://api.openai.com/v1"
    const val defaultModel: String = "gpt-4o-mini"
    val defaultPromptTemplate: String =
      """
      你是专业翻译助手。请把输入文本翻译为 [TARGET_LANG]。
      请严格保留段落分隔标记 [SEPARATOR] 的数量和顺序。
      只输出译文，不要输出解释或额外内容。

      [INPUT]
      """
        .trimIndent()

    fun normalize(raw: OpenAiTranslationConfig): OpenAiTranslationConfig {
      val normalizedBaseUrl = raw.baseUrl.trim().trimEnd('/')
      return raw.copy(
        baseUrl = if (normalizedBaseUrl.isBlank()) defaultBaseUrl else normalizedBaseUrl,
        apiKey = raw.apiKey.trim(),
        model = raw.model.trim().ifBlank { defaultModel },
        promptTemplate = raw.promptTemplate.trim().ifBlank { defaultPromptTemplate },
      )
    }
  }
}
