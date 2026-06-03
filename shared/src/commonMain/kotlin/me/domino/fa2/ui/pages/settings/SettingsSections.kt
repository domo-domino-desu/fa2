package me.domino.fa2.ui.pages.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.BlockedSubmissionPagerMode
import me.domino.fa2.data.settings.BlockedSubmissionWaterfallMode
import me.domino.fa2.data.settings.DownloadFileNameMode
import me.domino.fa2.data.settings.MetadataDisplayMode
import me.domino.fa2.data.settings.TranslationProvider
import me.domino.fa2.ui.components.ExpressiveTextButton
import me.domino.fa2.ui.components.platform.rememberPlatformDirectoryPicker
import me.domino.fa2.ui.components.settings.SettingsDropdownField
import me.domino.fa2.ui.components.settings.SettingsGroup
import me.domino.fa2.ui.components.settings.SettingsInputRow
import me.domino.fa2.ui.components.settings.SettingsNavigationRow
import me.domino.fa2.ui.components.settings.SettingsSwitchRow
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AppearanceSettingsSection(
    draft: SettingsDraft,
    onDraftChange: (SettingsDraft) -> Unit,
) {
  SettingsGroup(title = stringResource(Res.string.appearance), framed = false) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
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

      SettingsInputRow(
          value = draft.waterfallMinCardWidthInput,
          onValueChange = { next ->
            onDraftChange(draft.copy(waterfallMinCardWidthInput = next.filter(Char::isDigit)))
          },
          label = stringResource(Res.string.waterfall_min_column_width_dp),
      )

      SettingsSwitchRow(
          label = stringResource(Res.string.return_to_current_submission_in_waterfall),
          checked = draft.returnToCurrentSubmissionInWaterfall,
          onCheckedChange = { enabled ->
            onDraftChange(draft.copy(returnToCurrentSubmissionInWaterfall = enabled))
          },
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
  SettingsGroup(title = stringResource(Res.string.translation), framed = false) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
      SettingsSwitchRow(
          label = stringResource(Res.string.enable_translation),
          checked = draft.translationEnabled,
          onCheckedChange = { enabled -> onDraftChange(draft.copy(translationEnabled = enabled)) },
      )

      if (draft.translationEnabled) {
        SettingsDropdownField(
            label = stringResource(Res.string.provider),
            selected = draft.translationProvider,
            options = AppSettings.supportedTranslationProviders,
            optionLabel = { option -> translationProviderLabel(option) },
            onSelect = { selected -> onDraftChange(draft.copy(translationProvider = selected)) },
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

        SettingsSwitchRow(
            label = stringResource(Res.string.field_translation),
            checked = draft.metadataDisplayMode == MetadataDisplayMode.TRANSLATED,
            onCheckedChange = { enabled ->
              onDraftChange(
                  draft.copy(
                      metadataDisplayMode =
                          if (enabled) {
                            MetadataDisplayMode.TRANSLATED
                          } else {
                            MetadataDisplayMode.ORIGINAL
                          }
                  )
              )
            },
        )

        SettingsInputRow(
            value = draft.chunkWordLimitInput,
            onValueChange = { next ->
              onDraftChange(draft.copy(chunkWordLimitInput = next.filter(Char::isDigit)))
            },
            label = stringResource(Res.string.chunk_word_limit),
            supportingText =
                stringResource(
                    Res.string.numeric_range,
                    AppSettings.minTranslationChunkWordLimit,
                    AppSettings.maxTranslationChunkWordLimit,
                ),
        )

        SettingsInputRow(
            value = draft.maxConcurrencyInput,
            onValueChange = { next ->
              onDraftChange(draft.copy(maxConcurrencyInput = next.filter(Char::isDigit)))
            },
            label = stringResource(Res.string.max_concurrency),
            supportingText =
                stringResource(
                    Res.string.numeric_range,
                    AppSettings.minTranslationMaxConcurrency,
                    AppSettings.maxTranslationMaxConcurrency,
                ),
        )

        if (draft.translationProvider == TranslationProvider.OPENAI_COMPATIBLE) {
          SettingsInputRow(
              value = draft.openAiBaseUrl,
              onValueChange = { next -> onDraftChange(draft.copy(openAiBaseUrl = next)) },
              label = stringResource(Res.string.base_url),
          )

          SettingsInputRow(
              value = draft.openAiApiKey,
              onValueChange = { next -> onDraftChange(draft.copy(openAiApiKey = next)) },
              label = stringResource(Res.string.api_key),
              visualTransformation =
                  if (showApiKey) {
                    VisualTransformation.None
                  } else {
                    PasswordVisualTransformation()
                  },
              trailingIcon = {
                ExpressiveTextButton(onClick = onToggleShowApiKey) {
                  Text(
                      if (showApiKey) stringResource(Res.string.hide)
                      else stringResource(Res.string.show)
                  )
                }
              },
          )

          SettingsInputRow(
              value = draft.openAiModel,
              onValueChange = { next -> onDraftChange(draft.copy(openAiModel = next)) },
              label = stringResource(Res.string.model),
          )

          SettingsInputRow(
              value = draft.openAiPromptTemplate,
              onValueChange = { next -> onDraftChange(draft.copy(openAiPromptTemplate = next)) },
              label = stringResource(Res.string.prompt_template),
              supportingText = stringResource(Res.string.prompt_template_variables_hint),
              singleLine = false,
              minLines = 5,
          )
        }
      }
    }
  }
}

@Composable
internal fun DownloadSettingsSection(
    draft: SettingsDraft,
    onDraftChange: (SettingsDraft) -> Unit,
) {
  val triggerDirectoryPicker = rememberPlatformDirectoryPicker { selectedPath ->
    if (selectedPath != null) {
      onDraftChange(draft.copy(downloadSavePath = selectedPath))
    }
  }
  SettingsGroup(title = stringResource(Res.string.download_settings), framed = false) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
      SettingsNavigationRow(
          title = stringResource(Res.string.download_save_path),
          subtitle =
              draft.downloadSavePath.takeIf { it.isNotBlank() }
                  ?: stringResource(Res.string.accessibility_not_set),
          onClick = triggerDirectoryPicker,
      )

      SettingsSwitchRow(
          label = stringResource(Res.string.download_allow_media_indexing),
          checked = draft.downloadAllowMediaIndexing,
          onCheckedChange = { enabled ->
            onDraftChange(draft.copy(downloadAllowMediaIndexing = enabled))
          },
      )

      SettingsDropdownField(
          label = stringResource(Res.string.download_subfolder_mode),
          selected = draft.downloadSubfolderMode,
          options = AppSettings.supportedDownloadSubfolderModes,
          optionLabel = { option -> downloadSubfolderModeLabel(option) },
          onSelect = { selected -> onDraftChange(draft.copy(downloadSubfolderMode = selected)) },
      )

      SettingsDropdownField(
          label = stringResource(Res.string.download_file_name_mode),
          selected = draft.downloadFileNameMode,
          options = AppSettings.supportedDownloadFileNameModes,
          optionLabel = { option -> downloadFileNameModeLabel(option) },
          onSelect = { selected -> onDraftChange(draft.copy(downloadFileNameMode = selected)) },
      )

      if (draft.downloadFileNameMode == DownloadFileNameMode.CUSTOM) {
        SettingsInputRow(
            value = draft.downloadCustomFileNameTemplate,
            onValueChange = { next ->
              onDraftChange(draft.copy(downloadCustomFileNameTemplate = next))
            },
            label = stringResource(Res.string.download_file_name_template),
            supportingText = stringResource(Res.string.download_file_name_template_variables_hint),
        )
      }
    }
  }
}

@Composable
internal fun RecommendationSettingsSection(
    draft: SettingsDraft,
    onDraftChange: (SettingsDraft) -> Unit,
    onOpenBlocklistManager: () -> Unit,
) {
  SettingsGroup(title = stringResource(Res.string.following_recommendation), framed = false) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
      SettingsInputRow(
          value = draft.watchRecommendationPageSizeInput,
          onValueChange = { next ->
            onDraftChange(draft.copy(watchRecommendationPageSizeInput = next.filter(Char::isDigit)))
          },
          label = stringResource(Res.string.watch_recommendation_page_size),
          supportingText =
              stringResource(
                  Res.string.numeric_range,
                  AppSettings.minWatchRecommendationPageSize,
                  AppSettings.maxWatchRecommendationPageSize,
              ),
      )

      SettingsNavigationRow(
          title = stringResource(Res.string.following_recommendation_blocklist),
          subtitle = stringResource(Res.string.following_recommendation_blocklist_summary),
          onClick = onOpenBlocklistManager,
      )
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
        verticalArrangement = Arrangement.spacedBy(0.dp),
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
