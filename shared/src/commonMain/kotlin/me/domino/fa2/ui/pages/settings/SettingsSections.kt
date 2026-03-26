package me.domino.fa2.ui.pages.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.BlockedSubmissionPagerMode
import me.domino.fa2.data.settings.BlockedSubmissionWaterfallMode
import me.domino.fa2.data.settings.TranslationProvider
import me.domino.fa2.ui.components.settings.SettingsDropdownField
import me.domino.fa2.ui.components.settings.SettingsGroup
import me.domino.fa2.ui.components.settings.SettingsSwitchRow
import me.domino.fa2.ui.host.LocalAppI18n
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AppearanceSettingsSection(
    draft: SettingsDraft,
    onDraftChange: (SettingsDraft) -> Unit,
) {
  val appI18n = LocalAppI18n.current
  SettingsGroup(title = stringResource(Res.string.appearance), framed = false) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      SettingsDropdownField(
          label = stringResource(Res.string.theme_mode),
          selected = draft.themeMode,
          options = AppSettings.supportedThemeModes,
          optionLabel = { option -> themeModeLabel(option) },
          onSelect = { selected -> onDraftChange(draft.copy(themeMode = selected)) },
      )

      SettingsDropdownField(
          label = stringResource(Res.string.app_language),
          selected = draft.uiLanguage,
          options = AppSettings.supportedUiLanguages,
          optionLabel = { option -> uiLanguageLabel(option) },
          onSelect = { selected -> onDraftChange(draft.copy(uiLanguage = selected)) },
      )

      OutlinedTextField(
          value = draft.waterfallMinCardWidthInput,
          onValueChange = { next ->
            onDraftChange(draft.copy(waterfallMinCardWidthInput = next.filter(Char::isDigit)))
          },
          label = { Text(stringResource(Res.string.waterfall_min_column_width_dp)) },
          supportingText = {
            Text(
                stringResource(
                    Res.string.waterfall_min_column_width_range,
                    AppSettings.minWaterfallMinCardWidthDp,
                    AppSettings.maxWaterfallMinCardWidthDp,
                )
            )
          },
          modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

@Composable
internal fun TranslationSettingsSection(
    draft: SettingsDraft,
    onDraftChange: (SettingsDraft) -> Unit,
    showApiKey: Boolean,
    onToggleShowApiKey: () -> Unit,
) {
  val appI18n = LocalAppI18n.current
  SettingsGroup(title = stringResource(Res.string.translation), framed = false) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      SettingsDropdownField(
          label = stringResource(Res.string.provider),
          selected = draft.translationProvider,
          options = AppSettings.supportedTranslationProviders,
          optionLabel = { option -> translationProviderLabel(option) },
          onSelect = { selected -> onDraftChange(draft.copy(translationProvider = selected)) },
      )

      SettingsSwitchRow(
          label = stringResource(Res.string.enable_translation),
          checked = draft.translationEnabled,
          onCheckedChange = { enabled -> onDraftChange(draft.copy(translationEnabled = enabled)) },
      )

      SettingsDropdownField(
          label = stringResource(Res.string.translation_target_language),
          selected = draft.translationTargetLanguage,
          options = AppSettings.supportedTranslationTargetLanguages,
          optionLabel = { option -> translationTargetLanguageLabel(option) },
          onSelect = { selected ->
            onDraftChange(draft.copy(translationTargetLanguage = selected))
          },
      )

      SettingsDropdownField(
          label = stringResource(Res.string.metadata_display),
          selected = draft.metadataDisplayMode,
          options = AppSettings.supportedMetadataDisplayModes,
          optionLabel = { option -> metadataDisplayModeLabel(option) },
          onSelect = { selected -> onDraftChange(draft.copy(metadataDisplayMode = selected)) },
      )

      OutlinedTextField(
          value = draft.chunkWordLimitInput,
          onValueChange = { next ->
            onDraftChange(draft.copy(chunkWordLimitInput = next.filter(Char::isDigit)))
          },
          label = { Text(stringResource(Res.string.chunk_word_limit)) },
          supportingText = {
            Text(
                stringResource(
                    Res.string.numeric_range,
                    AppSettings.minTranslationChunkWordLimit,
                    AppSettings.maxTranslationChunkWordLimit,
                )
            )
          },
          modifier = Modifier.fillMaxWidth(),
      )

      OutlinedTextField(
          value = draft.maxConcurrencyInput,
          onValueChange = { next ->
            onDraftChange(draft.copy(maxConcurrencyInput = next.filter(Char::isDigit)))
          },
          label = { Text(stringResource(Res.string.max_concurrency)) },
          supportingText = {
            Text(
                stringResource(
                    Res.string.numeric_range,
                    AppSettings.minTranslationMaxConcurrency,
                    AppSettings.maxTranslationMaxConcurrency,
                )
            )
          },
          modifier = Modifier.fillMaxWidth(),
      )

      if (
          draft.translationEnabled &&
              draft.translationProvider == TranslationProvider.OPENAI_COMPATIBLE
      ) {
        OutlinedTextField(
            value = draft.openAiBaseUrl,
            onValueChange = { next -> onDraftChange(draft.copy(openAiBaseUrl = next)) },
            label = { Text(stringResource(Res.string.base_url)) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = draft.openAiApiKey,
            onValueChange = { next -> onDraftChange(draft.copy(openAiApiKey = next)) },
            label = { Text(stringResource(Res.string.api_key)) },
            visualTransformation =
                if (showApiKey) {
                  VisualTransformation.None
                } else {
                  PasswordVisualTransformation()
                },
            trailingIcon = {
              TextButton(onClick = onToggleShowApiKey) {
                Text(
                    if (showApiKey) stringResource(Res.string.hide)
                    else stringResource(Res.string.show)
                )
              }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = draft.openAiModel,
            onValueChange = { next -> onDraftChange(draft.copy(openAiModel = next)) },
            label = { Text(stringResource(Res.string.model)) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = draft.openAiPromptTemplate,
            onValueChange = { next -> onDraftChange(draft.copy(openAiPromptTemplate = next)) },
            label = { Text(stringResource(Res.string.prompt_template)) },
            supportingText = { Text(stringResource(Res.string.prompt_template_variables_hint)) },
            minLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

@Composable
internal fun BlockedContentSettingsSection(
    draft: SettingsDraft,
    onDraftChange: (SettingsDraft) -> Unit,
) {
  SettingsGroup(title = stringResource(Res.string.blocked_content), framed = false) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      SettingsSwitchRow(
          label = stringResource(Res.string.blur_blocked_submissions_in_waterfalls),
          checked =
              draft.blockedSubmissionWaterfallMode == BlockedSubmissionWaterfallMode.BLUR_THEN_OPEN,
          onCheckedChange = { enabled ->
            onDraftChange(
                draft.copy(
                    blockedSubmissionWaterfallMode =
                        if (enabled) {
                          BlockedSubmissionWaterfallMode.BLUR_THEN_OPEN
                        } else {
                          BlockedSubmissionWaterfallMode.SHOW
                        }
                )
            )
          },
      )
      SettingsSwitchRow(
          label = stringResource(Res.string.blur_blocked_submissions_in_detail_pages),
          checked = draft.blockedSubmissionPagerMode == BlockedSubmissionPagerMode.BLUR_THEN_OPEN,
          onCheckedChange = { enabled ->
            onDraftChange(
                draft.copy(
                    blockedSubmissionPagerMode =
                        if (enabled) {
                          BlockedSubmissionPagerMode.BLUR_THEN_OPEN
                        } else {
                          BlockedSubmissionPagerMode.SHOW
                        }
                )
            )
          },
      )
    }
  }
}
