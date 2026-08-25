package me.domino.fa2.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.domino.fa2.ui.app.challenge.CfChallengeOverlayHost
import me.domino.fa2.ui.app.navigation.AppNavigator
import me.domino.fa2.ui.app.theme.ProvideFa2Theme
import me.domino.fa2.ui.components.feedback.AppFeedbackHost

/** 应用根入口：负责主题、全局反馈与导航编排。 */
@Composable
fun Fa2App(externalFaLinkEvents: Flow<String> = emptyFlow()) {
  Fa2AppEnvironment { Fa2AppContent(externalFaLinkEvents = externalFaLinkEvents) }
}

/** 提供应用设置与主题，使桌面窗口装饰和应用内容共享同一套动态配色。 */
@Composable
fun Fa2AppEnvironment(content: @Composable () -> Unit) {
  ProvideFa2UiDependencies {
    ProvideFa2Theme(themeMode = LocalAppSettings.current.themeMode, content = content)
  }
}

/** 应用窗口内的纯内容；调用方需要先提供 [Fa2AppEnvironment]。 */
@Composable
fun Fa2AppContent(externalFaLinkEvents: Flow<String> = emptyFlow()) {
  Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
      contentColor = MaterialTheme.colorScheme.onBackground,
  ) {
    AppFeedbackHost {
      Box(modifier = Modifier.fillMaxSize()) {
        AppNavigator(externalFaLinkEvents = externalFaLinkEvents)
        CfChallengeOverlayHost(modifier = Modifier.fillMaxSize())
      }
    }
  }
}
