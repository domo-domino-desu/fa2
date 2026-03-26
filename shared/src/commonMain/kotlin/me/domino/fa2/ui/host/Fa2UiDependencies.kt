package me.domino.fa2.ui.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.taxonomy.FaTaxonomyCatalog
import me.domino.fa2.data.taxonomy.FaTaxonomyRepository
import me.domino.fa2.ui.search.SearchUiLabelsCatalog
import me.domino.fa2.ui.search.SearchUiLabelsRepository
import org.koin.compose.koinInject

internal val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

internal val LocalTaxonomyRepository = staticCompositionLocalOf { FaTaxonomyRepository() }

internal val LocalTaxonomyCatalog = staticCompositionLocalOf<FaTaxonomyCatalog?> { null }

internal val LocalSearchUiLabelsRepository = staticCompositionLocalOf { SearchUiLabelsRepository() }

internal val LocalSearchUiLabelsCatalog = staticCompositionLocalOf<SearchUiLabelsCatalog?> { null }

@Composable
internal fun ProvideFa2UiDependencies(content: @Composable () -> Unit) {
  val settingsService = koinInject<AppSettingsService>()
  val taxonomyRepository = koinInject<FaTaxonomyRepository>()
  val searchUiLabelsRepository = koinInject<SearchUiLabelsRepository>()
  val settings by settingsService.settings.collectAsState()
  val taxonomyCatalog by taxonomyRepository.catalog.collectAsState()
  val searchUiLabelsCatalog by searchUiLabelsRepository.catalog.collectAsState()

  LaunchedEffect(settingsService) { settingsService.ensureLoaded() }
  LaunchedEffect(taxonomyRepository) { taxonomyRepository.ensureLoaded() }
  LaunchedEffect(searchUiLabelsRepository) { searchUiLabelsRepository.ensureLoaded() }

  CompositionLocalProvider(
      LocalAppSettings provides settings,
      LocalTaxonomyRepository provides taxonomyRepository,
      LocalTaxonomyCatalog provides taxonomyCatalog,
      LocalSearchUiLabelsRepository provides searchUiLabelsRepository,
      LocalSearchUiLabelsCatalog provides searchUiLabelsCatalog,
  ) {
    content()
  }
}
