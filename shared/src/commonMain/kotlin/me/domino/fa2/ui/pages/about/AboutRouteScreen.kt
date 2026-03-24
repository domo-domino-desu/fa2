package me.domino.fa2.ui.pages.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import me.domino.fa2.ui.layouts.AboutRouteTopBar
import me.domino.fa2.ui.navigation.goBackHome

/** 开源许可页面。 */
class AboutRouteScreen : Screen {
  override val key: String = "about-route"

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val libraries by produceLibraries { loadAboutLibrariesJson() }

    Column(modifier = Modifier.fillMaxSize()) {
      AboutRouteTopBar(onBack = { navigator.pop() }, onGoHome = { navigator.goBackHome() })

      LibrariesContainer(
        libraries = libraries,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        header = {
          item {
            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
              Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(text = "fa2", style = MaterialTheme.typography.titleMedium)
                Text(
                  text = "本页由 AboutLibraries 生成依赖与许可证信息",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        },
        footer = {
          item {
            Text(
              text = "许可证与依赖元数据来源：AboutLibraries",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
          }
        },
      )
    }
  }
}
