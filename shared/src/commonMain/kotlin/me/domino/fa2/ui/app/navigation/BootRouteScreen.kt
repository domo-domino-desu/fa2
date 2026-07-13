package me.domino.fa2.ui.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import me.domino.fa2.data.remote.fa.auth.AuthRepository
import me.domino.fa2.data.remote.fa.session.AuthSessionController
import me.domino.fa2.data.remote.fa.session.PendingFaRouteStore
import me.domino.fa2.ui.pages.auth.AuthLoadingScreen
import me.domino.fa2.ui.pages.auth.AuthRouteScreen
import org.koin.compose.koinInject

/** 启动壳：仅恢复本地状态并决定进入登录页或主壳。 */
@Serializable
class BootRouteScreen : AppScreen {
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val authRepository = koinInject<AuthRepository>()
    val authSessionController = koinInject<AuthSessionController>()
    val pendingFaRouteStore = koinInject<PendingFaRouteStore>()

    LaunchedEffect(Unit) {
      authRepository.restorePersistedSession()
      yield()
      val hasAuthCookie = authRepository.hasAuthCookie()
      val needsRelogin = authSessionController.needsRelogin()
      if (hasAuthCookie && !needsRelogin) {
        navigator.replaceAll(
            MainRouteScreen(deferInitialFeedLoad = pendingFaRouteStore.peek() != null)
        )
      } else {
        navigator.replaceAll(AuthRouteScreen())
      }
    }

    AuthLoadingScreen()
  }
}
