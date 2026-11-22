package me.domino.fa2.ui.pages.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.api_key
import fa2.shared.generated.resources.app_language
import fa2.shared.generated.resources.base_url
import fa2.shared.generated.resources.blur_blocked_submissions_in_detail_pages
import fa2.shared.generated.resources.blur_blocked_submissions_in_waterfalls
import fa2.shared.generated.resources.cancel
import fa2.shared.generated.resources.cannot_be_empty
import fa2.shared.generated.resources.chunk_word_limit
import fa2.shared.generated.resources.close
import fa2.shared.generated.resources.download_file_name_mode
import fa2.shared.generated.resources.download_subfolder_mode
import fa2.shared.generated.resources.field_translation
import fa2.shared.generated.resources.loading_settings
import fa2.shared.generated.resources.max_concurrency
import fa2.shared.generated.resources.model
import fa2.shared.generated.resources.must_be_in_range
import fa2.shared.generated.resources.must_be_number
import fa2.shared.generated.resources.must_start_with_http_or_https
import fa2.shared.generated.resources.prompt_template
import fa2.shared.generated.resources.restored_saved_settings
import fa2.shared.generated.resources.save_failed
import fa2.shared.generated.resources.save_failed_enter_valid_numbers
import fa2.shared.generated.resources.settings_discard_changes
import fa2.shared.generated.resources.settings_save_and_exit
import fa2.shared.generated.resources.settings_saved
import fa2.shared.generated.resources.settings_unsaved_changes_body
import fa2.shared.generated.resources.settings_unsaved_changes_title
import fa2.shared.generated.resources.theme_mode
import fa2.shared.generated.resources.translation_target_language
import fa2.shared.generated.resources.unsupported_setting
import fa2.shared.generated.resources.watch_recommendation_page_size
import fa2.shared.generated.resources.waterfall_min_column_width_dp
import kotlinx.coroutines.launch
import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.ui.app.scaffold.SettingsRouteTopBar
import me.domino.fa2.ui.components.ExpressiveButton
import me.domino.fa2.ui.components.ExpressiveIconButton
import me.domino.fa2.ui.components.ExpressiveTextButton
import me.domino.fa2.ui.components.platform.PlatformBackHandler
import me.domino.fa2.ui.i18n.appString
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.utils.concurrency.runSuspendCatching
import org.jetbrains.compose.resources.stringResource

private enum class PendingExitAction {
  Back,
  Home,
}

@Composable
private fun settingsValidationMessage(issue: SettingsValidationIssue): String {
  val field =
      when (issue.field) {
        SettingsField.ChunkWordLimit -> stringResource(Res.string.chunk_word_limit)
        SettingsField.MaxConcurrency -> stringResource(Res.string.max_concurrency)
        SettingsField.WaterfallMinColumnWidth ->
            stringResource(Res.string.waterfall_min_column_width_dp)
        SettingsField.WatchRecommendationPageSize ->
            stringResource(Res.string.watch_recommendation_page_size)
        SettingsField.ThemeMode -> stringResource(Res.string.theme_mode)
        SettingsField.AppLanguage -> stringResource(Res.string.app_language)
        SettingsField.TranslationTargetLanguage ->
            stringResource(Res.string.translation_target_language)
        SettingsField.MetadataDisplay -> stringResource(Res.string.field_translation)
        SettingsField.BlurInWaterfalls ->
            stringResource(Res.string.blur_blocked_submissions_in_waterfalls)
        SettingsField.BlurInDetailPages ->
            stringResource(Res.string.blur_blocked_submissions_in_detail_pages)
        SettingsField.DownloadSubfolderMode -> stringResource(Res.string.download_subfolder_mode)
        SettingsField.DownloadFileNameMode -> stringResource(Res.string.download_file_name_mode)
        SettingsField.BaseUrl -> stringResource(Res.string.base_url)
        SettingsField.ApiKey -> stringResource(Res.string.api_key)
        SettingsField.Model -> stringResource(Res.string.model)
        SettingsField.PromptTemplate -> stringResource(Res.string.prompt_template)
      }
  return when (issue) {
    is SettingsValidationIssue.MustBeNumber -> stringResource(Res.string.must_be_number, field)
    is SettingsValidationIssue.Unsupported -> stringResource(Res.string.unsupported_setting, field)
    is SettingsValidationIssue.OutOfRange -> {
      val suffix = issue.suffix.trim().takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
      stringResource(Res.string.must_be_in_range, field, issue.min, issue.max, suffix)
    }
    is SettingsValidationIssue.CannotBeEmpty -> stringResource(Res.string.cannot_be_empty, field)
    is SettingsValidationIssue.MustStartWithHttp ->
        stringResource(Res.string.must_start_with_http_or_https, field)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsService: AppSettingsService,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onOpenRecommendationBlocklist: () -> Unit,
) {
  val scope = rememberCoroutineScope()

  val loaded by settingsService.isLoaded.collectAsState()
  val settings by settingsService.settings.collectAsState()

  var draft by remember { mutableStateOf(SettingsDraft.fromSettings(AppSettings())) }
  var initialized by remember { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  var showApiKey by remember { mutableStateOf(false) }
  var saveStatusText by remember { mutableStateOf<String?>(null) }
  var pendingExitAction by remember { mutableStateOf<PendingExitAction?>(null) }
  val listState = rememberLazyListState()

  LaunchedEffect(Unit) { settingsService.ensureLoaded() }

  LaunchedEffect(loaded, settings) {
    if (!loaded) return@LaunchedEffect
    if (!initialized) {
      draft = SettingsDraft.fromSettings(settings)
      initialized = true
      return@LaunchedEffect
    }
    if (!draft.hasChangesComparedTo(settings)) {
      draft = SettingsDraft.fromSettings(settings)
    }
  }

  val validationIssue = draft.validate()
  val validationMessage = validationIssue?.let { settingsValidationMessage(it) }
  val restoredSavedSettingsText = stringResource(Res.string.restored_saved_settings)
  val invalidNumbersText = stringResource(Res.string.save_failed_enter_valid_numbers)
  val validationSaveFailureText =
      validationMessage?.let { stringResource(Res.string.save_failed, it) }
  val hasUnsavedChanges = loaded && initialized && draft.hasChangesComparedTo(settings)

  fun resetDraftToPersisted() {
    draft = SettingsDraft.fromSettings(settings)
    saveStatusText = restoredSavedSettingsText
  }

  fun saveDraft(onSaved: (() -> Unit)? = null) {
    val target = draft.toAppSettingsOrNull()
    if (target == null) {
      saveStatusText = invalidNumbersText
      return
    }
    if (validationMessage != null) {
      saveStatusText = validationSaveFailureText
      return
    }

    saving = true
    saveStatusText = null
    scope.launch {
      runSuspendCatching { settingsService.updateSettings(target) }
          .onSuccess {
            saveStatusText = appString(Res.string.settings_saved)
            onSaved?.invoke()
          }
          .onFailure { error ->
            val detail = error.message ?: error::class.simpleName.orEmpty()
            saveStatusText = appString(Res.string.save_failed, detail)
          }
      saving = false
    }
  }

  fun executePendingExit(action: PendingExitAction) {
    when (action) {
      PendingExitAction.Back -> onBack()
      PendingExitAction.Home -> onGoHome()
    }
  }

  fun requestExit(action: PendingExitAction) {
    if (saving) return
    if (!hasUnsavedChanges) {
      executePendingExit(action)
      return
    }
    pendingExitAction = action
  }

  PlatformBackHandler(enabled = true) { requestExit(PendingExitAction.Back) }

  Scaffold(
      modifier = Modifier.testTag("settings-screen"),
      containerColor = MaterialTheme.colorScheme.background,
      topBar = {
        SettingsRouteTopBar(
            onBack = { requestExit(PendingExitAction.Back) },
            onGoHome = { requestExit(PendingExitAction.Home) },
            showActions = loaded && initialized,
            saving = saving,
            hasUnsavedChanges = hasUnsavedChanges,
            validationMessage = validationMessage,
            onResetDraft = ::resetDraftToPersisted,
            onSaveDraft = { saveDraft() },
            onTitleClick = { scope.launch { listState.animateScrollToItem(0) } },
        )
      },
  ) { innerPadding ->
    if (!loaded || !initialized) {
      Column(
          modifier =
              Modifier.fillMaxSize()
                  .padding(innerPadding)
                  .padding(horizontal = 16.dp, vertical = 12.dp)
      ) {
        Text(
            text = stringResource(Res.string.loading_settings),
            style = MaterialTheme.typography.bodyMedium,
        )
      }
      return@Scaffold
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item { AppearanceSettingsSection(draft = draft, onDraftChange = { next -> draft = next }) }

      item {
        TranslationSettingsSection(
            draft = draft,
            onDraftChange = { next -> draft = next },
            showApiKey = showApiKey,
            onToggleShowApiKey = { showApiKey = !showApiKey },
        )
      }

      item {
        RecommendationSettingsSection(
            draft = draft,
            onDraftChange = { next -> draft = next },
            onOpenBlocklistManager = onOpenRecommendationBlocklist,
        )
      }

      item {
        BlockedContentSettingsSection(draft = draft, onDraftChange = { next -> draft = next })
      }

      item { DownloadSettingsSection(draft = draft, onDraftChange = { next -> draft = next }) }

      validationMessage?.let { message ->
        item {
          Text(
              text = message,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(horizontal = 18.dp),
          )
        }
      }

      saveStatusText
          ?.takeIf { it.isNotBlank() }
          ?.let { message ->
            item {
              Text(
                  text = message,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 18.dp),
              )
            }
          }
    }
  }

  pendingExitAction?.let { action ->
    AlertDialog(
        onDismissRequest = { pendingExitAction = null },
        title = {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(stringResource(Res.string.settings_unsaved_changes_title))
            ExpressiveIconButton(onClick = { pendingExitAction = null }, enabled = !saving) {
              Icon(
                  imageVector = FaMaterialSymbols.Filled.Close,
                  contentDescription = stringResource(Res.string.close),
              )
            }
          }
        },
        text = { Text(stringResource(Res.string.settings_unsaved_changes_body)) },
        confirmButton = {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpressiveButton(
                onClick = {
                  saveDraft(
                      onSaved = {
                        pendingExitAction = null
                        executePendingExit(action)
                      }
                  )
                },
                enabled = !saving && validationMessage == null,
            ) {
              Text(stringResource(Res.string.settings_save_and_exit))
            }
            ExpressiveTextButton(
                onClick = {
                  pendingExitAction = null
                  executePendingExit(action)
                },
                enabled = !saving,
            ) {
              Text(stringResource(Res.string.settings_discard_changes))
            }
          }
        },
        dismissButton = {
          ExpressiveTextButton(onClick = { pendingExitAction = null }, enabled = !saving) {
            Text(stringResource(Res.string.cancel))
          }
        },
    )
  }
}
