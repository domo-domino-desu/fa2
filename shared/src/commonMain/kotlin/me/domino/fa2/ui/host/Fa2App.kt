package me.domino.fa2.ui.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.domino.fa2.ui.components.AppFeedbackHost
import me.domino.fa2.ui.components.challenge.CfChallengeOverlayHost
import me.domino.fa2.ui.navigation.AppNavigator
import me.domino.fa2.ui.theme.Fa2Theme

/** 应用根入口：负责主题、全局反馈与导航编排。 */
@Composable
fun Fa2App(externalFaLinkEvents: Flow<String> = emptyFlow()) {
  ProvideFa2UiDependencies {
    Fa2Theme(themeMode = LocalAppSettings.current.themeMode) {
      AppFeedbackHost {
        Box(modifier = Modifier.fillMaxSize()) {
          AppNavigator(externalFaLinkEvents = externalFaLinkEvents)
          CfChallengeOverlayHost(modifier = Modifier.fillMaxSize())
        }
      }
    }
  }
}
