package me.domino.fa2.ui.screen.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import me.domino.fa2.ui.navigation.MainRouteScreen

/**
 * 认证路由页面。
 */
class AuthRouteScreen : Screen {
    /**
     * 页面内容。
     */
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<AuthScreenModel>()
        val state by screenModel.state.collectAsState()
        val cookieDraft by screenModel.cookieDraft().collectAsState()

        LaunchedEffect(Unit) {
            screenModel.bootstrap()
        }

        LaunchedEffect(state) {
            val snapshot = state
            if (snapshot is AuthUiState.Authenticated) {
                navigator.replaceAll(MainRouteScreen(snapshot.username ?: "unknown"))
            }
        }

        when (val snapshot = state) {
            AuthUiState.Loading -> {
                AuthLoadingScreen()
            }

            is AuthUiState.AuthInvalid -> {
                AuthScreen(
                    state = snapshot,
                    cookieDraft = cookieDraft,
                    onCookieDraftChange = screenModel::updateCookieDraft,
                    onSubmit = screenModel::submitCookie,
                    onRetry = screenModel::retryProbe,
                )
            }

            is AuthUiState.Authenticated -> {
                // 导航 effect 会在上方处理，这里保持占位防止闪屏。
                AuthLoadingScreen()
            }
        }
    }
}
