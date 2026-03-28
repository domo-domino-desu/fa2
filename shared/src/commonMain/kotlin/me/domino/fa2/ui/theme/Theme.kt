package me.domino.fa2.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.domino.fa2.data.settings.ThemeMode

/** 应用主题入口。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Fa2Theme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 主题包裹的页面内容。 */
    content: @Composable () -> Unit,
) {
  val forceDarkMode =
      when (themeMode) {
        ThemeMode.SYSTEM -> null
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
      }

  MaterialExpressiveTheme(
      colorScheme = rememberPlatformColorScheme(forceDarkMode),
      content = {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
          content()
        }
      },
  )
}

@Composable internal expect fun rememberPlatformColorScheme(forceDarkMode: Boolean?): ColorScheme
