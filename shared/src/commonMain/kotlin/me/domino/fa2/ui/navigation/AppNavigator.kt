package me.domino.fa2.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.domino.fa2.ui.pages.auth.AuthRouteScreen

/** 应用导航容器。 */
@Composable
fun AppNavigator(externalFaLinkEvents: Flow<String> = emptyFlow()) {
  Navigator(screen = AuthRouteScreen()) {
    val defaultUriHandler = LocalUriHandler.current
    val rootNavigator = LocalNavigator.currentOrThrow.rootNavigator()
    val faLinkUriHandler =
        remember(rootNavigator, defaultUriHandler) {
          FaLinkUriHandler(navigator = rootNavigator, fallback = defaultUriHandler)
        }
    var pendingExternalUri by remember { mutableStateOf<String?>(null) }
    val currentRootScreen = rootNavigator.lastItem

    LaunchedEffect(externalFaLinkEvents) {
      externalFaLinkEvents.collect { incomingUri ->
        pendingExternalUri = incomingUri.trim().ifBlank { null }
      }
    }

    LaunchedEffect(currentRootScreen, pendingExternalUri, faLinkUriHandler) {
      val nextUri = pendingExternalUri ?: return@LaunchedEffect
      if (currentRootScreen is AuthRouteScreen) return@LaunchedEffect
      faLinkUriHandler.openUri(nextUri)
      pendingExternalUri = null
    }

    CompositionLocalProvider(LocalUriHandler provides faLinkUriHandler) { CurrentScreen() }
  }
}
