package me.domino.fa2.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.domino.fa2.application.auth.AuthSessionController
import me.domino.fa2.application.auth.PendingFaRouteStore
import me.domino.fa2.ui.pages.auth.AuthRouteScreen
import org.koin.compose.koinInject

/** 应用导航容器。 */
@Composable
fun AppNavigator(externalFaLinkEvents: Flow<String> = emptyFlow()) {
  Navigator(screen = BootRouteScreen()) {
    val defaultUriHandler = LocalUriHandler.current
    val rootNavigator = LocalNavigator.currentOrThrow.rootNavigator()
    val pendingFaRouteStore = koinInject<PendingFaRouteStore>()
    val authSessionController = koinInject<AuthSessionController>()
    val faLinkUriHandler =
        remember(rootNavigator, defaultUriHandler) {
          FaLinkUriHandler(navigator = rootNavigator, fallback = defaultUriHandler)
        }
    val currentRootScreen = rootNavigator.lastItem
    val pendingExternalUri by pendingFaRouteStore.pendingUri.collectAsState()
    val authSessionState by authSessionController.state.collectAsState()

    LaunchedEffect(externalFaLinkEvents) {
      externalFaLinkEvents.collect { incomingUri -> pendingFaRouteStore.save(incomingUri) }
    }

    LaunchedEffect(authSessionState.reloginRequestVersion, currentRootScreen) {
      if (authSessionState.reloginRequestVersion <= 0L) return@LaunchedEffect
      if (currentRootScreen is AuthRouteScreen) return@LaunchedEffect
      rootNavigator.replaceAll(AuthRouteScreen())
    }

    LaunchedEffect(currentRootScreen, pendingExternalUri, faLinkUriHandler) {
      val nextUri = pendingExternalUri ?: return@LaunchedEffect
      if (currentRootScreen is AuthRouteScreen || currentRootScreen is BootRouteScreen)
          return@LaunchedEffect
      faLinkUriHandler.openUri(nextUri)
      pendingFaRouteStore.consume(nextUri)
    }

    CompositionLocalProvider(LocalUriHandler provides faLinkUriHandler) { CurrentScreen() }
  }
}
