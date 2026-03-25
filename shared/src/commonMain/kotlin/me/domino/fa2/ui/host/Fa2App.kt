package me.domino.fa2.ui.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.domino.fa2.data.search.SearchUiLabelsRepository
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.taxonomy.FaTaxonomyRepository
import me.domino.fa2.ui.components.AppFeedbackHost
import me.domino.fa2.ui.components.challenge.CfChallengeOverlayHost
import me.domino.fa2.ui.navigation.AppNavigator
import me.domino.fa2.ui.theme.Fa2Theme
import org.koin.compose.koinInject

/** 应用根入口：负责主题、全局反馈与导航编排。 */
@Composable
fun Fa2App(externalFaLinkEvents: Flow<String> = emptyFlow()) {
  val settingsService = koinInject<AppSettingsService>()
  val taxonomyRepository = koinInject<FaTaxonomyRepository>()
  val searchUiLabelsRepository = koinInject<SearchUiLabelsRepository>()
  val settings by settingsService.settings.collectAsState()

  LaunchedEffect(settingsService) { settingsService.ensureLoaded() }
  LaunchedEffect(taxonomyRepository) { taxonomyRepository.ensureLoaded() }
  LaunchedEffect(searchUiLabelsRepository) { searchUiLabelsRepository.ensureLoaded() }

  Fa2Theme(themeMode = settings.themeMode) {
    AppFeedbackHost {
      Box(modifier = Modifier.fillMaxSize()) {
        AppNavigator(externalFaLinkEvents = externalFaLinkEvents)
        CfChallengeOverlayHost(modifier = Modifier.fillMaxSize())
      }
    }
  }
}
