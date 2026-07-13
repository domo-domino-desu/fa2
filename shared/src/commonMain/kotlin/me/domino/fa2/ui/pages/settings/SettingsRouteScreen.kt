package me.domino.fa2.ui.pages.settings

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.serialization.Serializable
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.ui.app.navigation.AppScreen
import me.domino.fa2.ui.app.navigation.goBackHome
import me.domino.fa2.ui.pages.watchrecommendation.WatchRecommendationBlocklistRouteScreen
import org.koin.compose.koinInject

/** 应用设置页面。 */
@Serializable
class SettingsRouteScreen : AppScreen {
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val settingsService = koinInject<AppSettingsService>()
    SettingsScreen(
        settingsService = settingsService,
        onBack = { navigator.pop() },
        onGoHome = { navigator.goBackHome() },
        onOpenRecommendationBlocklist = {
          navigator.push(WatchRecommendationBlocklistRouteScreen())
        },
    )
  }
}
