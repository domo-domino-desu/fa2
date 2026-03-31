package me.domino.fa2.ui.pages.settings

import androidx.compose.runtime.Composable
import fa2.shared.generated.resources.*
import me.domino.fa2.data.settings.DownloadFileNameMode
import me.domino.fa2.data.settings.DownloadSubfolderMode
import me.domino.fa2.data.settings.MetadataDisplayMode
import me.domino.fa2.data.settings.ThemeMode
import me.domino.fa2.data.settings.TranslationProvider
import me.domino.fa2.data.settings.TranslationTargetLanguage
import me.domino.fa2.data.settings.UiLanguageSetting
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun uiLanguageLabel(language: UiLanguageSetting): String =
    when (language) {
      UiLanguageSetting.SYSTEM -> stringResource(Res.string.ui_language_system)
      UiLanguageSetting.ZH_HANS -> stringResource(Res.string.ui_language_zh_hans)
      UiLanguageSetting.EN -> stringResource(Res.string.ui_language_en)
    }

@Composable
internal fun translationTargetLanguageLabel(language: TranslationTargetLanguage): String =
    when (language) {
      TranslationTargetLanguage.ZH_CN ->
          stringResource(Res.string.translation_target_language_zh_cn)
      TranslationTargetLanguage.EN -> stringResource(Res.string.translation_target_language_en)
    }

@Composable
internal fun metadataDisplayModeLabel(mode: MetadataDisplayMode): String =
    when (mode) {
      MetadataDisplayMode.ORIGINAL -> stringResource(Res.string.metadata_display_mode_original)
      MetadataDisplayMode.TRANSLATED -> stringResource(Res.string.metadata_display_mode_translated)
    }

@Composable
internal fun translationProviderLabel(provider: TranslationProvider): String =
    when (provider) {
      TranslationProvider.GOOGLE -> stringResource(Res.string.translation_provider_google)
      TranslationProvider.MICROSOFT -> stringResource(Res.string.translation_provider_microsoft)
      TranslationProvider.OPENAI_COMPATIBLE ->
          stringResource(Res.string.translation_provider_openai_compatible)
    }

@Composable
internal fun themeModeLabel(themeMode: ThemeMode): String =
    when (themeMode) {
      ThemeMode.SYSTEM -> stringResource(Res.string.theme_mode_system)
      ThemeMode.LIGHT -> stringResource(Res.string.theme_mode_light)
      ThemeMode.DARK -> stringResource(Res.string.theme_mode_dark)
    }

@Composable
internal fun downloadSubfolderModeLabel(mode: DownloadSubfolderMode): String =
    when (mode) {
      DownloadSubfolderMode.FLAT -> stringResource(Res.string.download_subfolder_mode_flat)
      DownloadSubfolderMode.BY_USERNAME ->
          stringResource(Res.string.download_subfolder_mode_by_username)
    }

@Composable
internal fun downloadFileNameModeLabel(mode: DownloadFileNameMode): String =
    when (mode) {
      DownloadFileNameMode.ID_TITLE -> stringResource(Res.string.download_file_name_mode_id_title)
      DownloadFileNameMode.USERNAME_ID ->
          stringResource(Res.string.download_file_name_mode_username_id)
      DownloadFileNameMode.USERNAME_ID_TITLE ->
          stringResource(Res.string.download_file_name_mode_username_id_title)
      DownloadFileNameMode.CUSTOM -> stringResource(Res.string.download_file_name_mode_custom)
    }
