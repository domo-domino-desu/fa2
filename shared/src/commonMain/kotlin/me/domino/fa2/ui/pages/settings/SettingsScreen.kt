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
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.cancel
import fa2.shared.generated.resources.close
import fa2.shared.generated.resources.loading_settings
import fa2.shared.generated.resources.restored_saved_settings
import fa2.shared.generated.resources.save_failed
import fa2.shared.generated.resources.save_failed_enter_valid_numbers
import fa2.shared.generated.resources.settings_discard_changes
import fa2.shared.generated.resources.settings_save_and_exit
import fa2.shared.generated.resources.settings_saved
import fa2.shared.generated.resources.settings_unsaved_changes_body
import fa2.shared.generated.resources.settings_unsaved_changes_title
import kotlinx.coroutines.launch
import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.i18n.appString
import me.domino.fa2.ui.components.ExpressiveButton
import me.domino.fa2.ui.components.ExpressiveIconButton
import me.domino.fa2.ui.components.ExpressiveTextButton
import me.domino.fa2.ui.components.platform.PlatformBackHandler
import me.domino.fa2.ui.icons.FaMaterialSymbols
import me.domino.fa2.ui.layouts.SettingsRouteTopBar
import org.jetbrains.compose.resources.stringResource

private enum class PendingExitAction {
  Back,
  Home,
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

  val validationMessage = draft.validate()
  val hasUnsavedChanges = loaded && initialized && draft.hasChangesComparedTo(settings)

  fun resetDraftToPersisted() {
    draft = SettingsDraft.fromSettings(settings)
    saveStatusText = appString(Res.string.restored_saved_settings)
  }

  fun saveDraft(onSaved: (() -> Unit)? = null) {
    val target = draft.toAppSettingsOrNull()
    if (target == null) {
      saveStatusText = appString(Res.string.save_failed_enter_valid_numbers)
      return
    }
    if (validationMessage != null) {
      saveStatusText = appString(Res.string.save_failed, validationMessage)
      return
    }

    saving = true
    saveStatusText = null
    scope.launch {
      runCatching { settingsService.updateSettings(target) }
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
      containerColor = MaterialTheme.colorScheme.surface,
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
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item { AppearanceSettingsSection(draft = draft, onDraftChange = { next -> draft = next }) }

      item { DownloadSettingsSection(draft = draft, onDraftChange = { next -> draft = next }) }

      item {
        RecommendationSettingsSection(
            draft = draft,
            onDraftChange = { next -> draft = next },
            onOpenBlocklistManager = onOpenRecommendationBlocklist,
        )
      }

      item {
        TranslationSettingsSection(
            draft = draft,
            onDraftChange = { next -> draft = next },
            showApiKey = showApiKey,
            onToggleShowApiKey = { showApiKey = !showApiKey },
        )
      }

      item {
        BlockedContentSettingsSection(
            draft = draft,
            onDraftChange = { next -> draft = next },
        )
      }

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
