package me.domino.fa2.ui.pages.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.ui.layouts.SettingsRouteTopBar
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onGoHome: () -> Unit) {
  val settingsService = koinInject<AppSettingsService>()
  val scope = rememberCoroutineScope()

  val loaded by settingsService.isLoaded.collectAsState()
  val settings by settingsService.settings.collectAsState()

  var draft by remember { mutableStateOf(SettingsDraft.fromSettings(AppSettings())) }
  var initialized by remember { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  var showApiKey by remember { mutableStateOf(false) }
  var saveStatusText by remember { mutableStateOf<String?>(null) }
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
    saveStatusText = "已恢复为已保存配置"
  }

  fun saveDraft() {
    val target = draft.toAppSettingsOrNull()
    if (target == null) {
      saveStatusText = "保存失败：请输入合法数字"
      return
    }
    if (validationMessage != null) {
      saveStatusText = "保存失败：$validationMessage"
      return
    }

    saving = true
    saveStatusText = null
    scope.launch {
      runCatching { settingsService.updateSettings(target) }
          .onSuccess { saveStatusText = "设置已保存" }
          .onFailure { error ->
            saveStatusText = "保存失败：${error.message ?: error::class.simpleName.orEmpty()}"
          }
      saving = false
    }
  }

  Scaffold(
      containerColor = MaterialTheme.colorScheme.surface,
      topBar = {
        SettingsRouteTopBar(
            onBack = onBack,
            onGoHome = onGoHome,
            showActions = loaded && initialized,
            saving = saving,
            hasUnsavedChanges = hasUnsavedChanges,
            validationMessage = validationMessage,
            onResetDraft = ::resetDraftToPersisted,
            onSaveDraft = ::saveDraft,
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
        Text(text = "正在加载设置...", style = MaterialTheme.typography.bodyMedium)
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
}
