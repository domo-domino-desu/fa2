package me.domino.fa2.i18n

import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.settings.MetadataDisplayMode
import me.domino.fa2.data.settings.UiLanguageSetting

enum class AppLanguage(val localeKey: String) {
  ZH_HANS("zh-Hans"),
  EN("en");

  val resourceLocaleTag: String
    get() =
        when (this) {
          ZH_HANS -> "zh-CN"
          EN -> "en"
        }

  companion object {
    fun fromLanguageTag(raw: String?): AppLanguage =
        when (raw?.trim()?.lowercase()) {
          "en",
          "en-us",
          "en-gb" -> EN
          else -> ZH_HANS
        }
  }
}

interface SystemLanguageProvider {
  fun currentLanguageTag(): String
}

data class MetadataDisplayPreferences(
    val translationEnabled: Boolean,
    val displayMode: MetadataDisplayMode,
    val displayLanguage: AppLanguage,
) {
  val showLocalized: Boolean
    get() = translationEnabled && displayMode == MetadataDisplayMode.TRANSLATED
}

val defaultMetadataDisplayPreferences =
    MetadataDisplayPreferences(
        translationEnabled = true,
        displayMode = MetadataDisplayMode.TRANSLATED,
        displayLanguage = AppLanguage.ZH_HANS,
    )

data class AppI18nSnapshot(
    val uiLanguage: AppLanguage,
    val metadata: MetadataDisplayPreferences,
) {
  companion object {
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

fun MetadataDisplayPreferences.localizedOrOriginal(
    localized: Map<String, String>,
    original: String,
): String =
    if (!showLocalized) {
      original
    } else {
      localized.localizedFor(displayLanguage, fallback = original)
    }

fun Map<String, String>.localizedFor(language: AppLanguage, fallback: String = ""): String =
    when (language) {
          AppLanguage.ZH_HANS -> this["zh-Hans"].orEmpty()
          AppLanguage.EN -> this["en"].orEmpty()
        }
        .ifBlank { this["en"].orEmpty() }
        .ifBlank { fallback }

fun AppLanguage.uiText(zhHans: String, en: String): String =
    when (this) {
      AppLanguage.ZH_HANS -> zhHans
      AppLanguage.EN -> en
    }

fun AppI18nSnapshot.uiText(zhHans: String, en: String): String = uiLanguage.uiText(zhHans, en)

fun AppSettingsService.currentAppI18n(
    systemLanguageProvider: SystemLanguageProvider
): AppI18nSnapshot = AppI18nSnapshot.from(settings.value, systemLanguageProvider)
