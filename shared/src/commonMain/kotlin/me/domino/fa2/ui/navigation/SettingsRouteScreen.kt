package me.domino.fa2.ui.navigation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import me.domino.fa2.ui.screen.settings.SettingsScreen

/**
 * 应用设置页面。
 */
class SettingsRouteScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        SettingsScreen(
            onBack = { navigator.pop() },
            onGoHome = { navigator.goBackHome() },
        )
    }
}
