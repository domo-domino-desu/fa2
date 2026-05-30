package me.domino.fa2.i18n

import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.settings.MetadataDisplayMode
import me.domino.fa2.data.settings.UiLanguageSetting

/** 应用支持的 UI 语言枚举。 */
enum class AppLanguage(
    /** 本地化资源键。 */
    val localeKey: String
) {
  /** 简体中文。 */
  ZH_HANS("zh-Hans"),
  /** 英文。 */
  EN("en");

  /** 用于 Compose 资源加载的区域标签。 */
  val resourceLocaleTag: String
    get() =
        when (this) {
          ZH_HANS -> "zh-CN"
          EN -> "en"
        }

  companion object {
    /** 根据 BCP-47 语言标签解析对应的 AppLanguage，未知标签默认返回简体中文。 */
    fun fromLanguageTag(raw: String?): AppLanguage =
        when (raw?.trim()?.lowercase()) {
          "en",
          "en-us",
          "en-gb" -> EN
          else -> ZH_HANS
        }
  }
}

/** 获取系统当前语言标签的平台接口。 */
interface SystemLanguageProvider {
  /** 返回当前系统语言的 BCP-47 标签（如 "zh-Hans"、"en-US"）。 */
  fun currentLanguageTag(): String
}

/** 元数据展示偏好设置，决定是否及如何展示本地化内容。 */
data class MetadataDisplayPreferences(
    /** 是否启用翻译功能。 */
    val translationEnabled: Boolean,
    /** 元数据展示模式。 */
    val displayMode: MetadataDisplayMode,
    /** 展示所用的目标语言。 */
    val displayLanguage: AppLanguage,
) {
  /** 当前配置下是否实际展示本地化内容。 */
  val showLocalized: Boolean
    get() = translationEnabled && displayMode == MetadataDisplayMode.TRANSLATED
}

/** 默认元数据展示偏好：启用翻译、展示译文、目标语言为简体中文。 */
val defaultMetadataDisplayPreferences =
    MetadataDisplayPreferences(
        translationEnabled = true,
        displayMode = MetadataDisplayMode.TRANSLATED,
        displayLanguage = AppLanguage.ZH_HANS,
    )

/** 应用 i18n 快照，包含当前 UI 语言与元数据展示偏好。 */
data class AppI18nSnapshot(
    /** 当前 UI 语言。 */
    val uiLanguage: AppLanguage,
    /** 元数据展示偏好。 */
    val metadata: MetadataDisplayPreferences,
) {
  companion object {
    /** 从 AppSettings 与系统语言提供者构造 i18n 快照。 */
    fun from(
        settings: AppSettings,
        systemLanguageProvider: SystemLanguageProvider,
    ): AppI18nSnapshot {
      val systemLanguage = AppLanguage.fromLanguageTag(systemLanguageProvider.currentLanguageTag())
      val uiLanguage =
          when (settings.uiLanguage) {
            UiLanguageSetting.SYSTEM -> systemLanguage
            UiLanguageSetting.ZH_HANS -> AppLanguage.ZH_HANS
            UiLanguageSetting.EN -> AppLanguage.EN
          }
      return AppI18nSnapshot(
          uiLanguage = uiLanguage,
          metadata =
              MetadataDisplayPreferences(
                  translationEnabled = settings.translationEnabled,
                  displayMode = settings.metadataDisplayMode,
                  displayLanguage = uiLanguage,
              ),
      )
    }
  }
}

/** 根据展示偏好返回本地化文本，未启用本地化时返回原始文本。 */
fun MetadataDisplayPreferences.localizedOrOriginal(
    localized: Map<String, String>,
    original: String,
): String =
    if (!showLocalized) {
      original
    } else {
      localized.localizedFor(displayLanguage, fallback = original)
    }

/** 从多语言映射中取出指定语言的文本，不存在时回退英文，再回退 fallback。 */
fun Map<String, String>.localizedFor(language: AppLanguage, fallback: String = ""): String =
    when (language) {
          AppLanguage.ZH_HANS -> this["zh-Hans"].orEmpty()
          AppLanguage.EN -> this["en"].orEmpty()
        }
        .ifBlank { this["en"].orEmpty() }
        .ifBlank { fallback }

/** 根据语言返回对应的 UI 字符串。 */
fun AppLanguage.uiText(zhHans: String, en: String): String =
    when (this) {
      AppLanguage.ZH_HANS -> zhHans
      AppLanguage.EN -> en
    }

/** 根据快照中的 UI 语言返回对应的字符串。 */
fun AppI18nSnapshot.uiText(zhHans: String, en: String): String = uiLanguage.uiText(zhHans, en)

/** 从 AppSettingsService 获取当前应用的 i18n 快照。 */
fun AppSettingsService.currentAppI18n(
    systemLanguageProvider: SystemLanguageProvider
): AppI18nSnapshot = AppI18nSnapshot.from(settings.value, systemLanguageProvider)
