package me.domino.fa2.ui.pages.settings

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.ui.navigation.goBackHome
import org.koin.compose.koinInject

/** 应用设置页面。 */
class SettingsRouteScreen : Screen {
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val settingsService = koinInject<AppSettingsService>()
    SettingsScreen(
        settingsService = settingsService,
        onBack = { navigator.pop() },
        onGoHome = { navigator.goBackHome() },
    )
  }
}
