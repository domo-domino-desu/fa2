package me.domino.fa2.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberPlatformUrlDownloader(): suspend (String) -> Boolean {
  return remember { { _ -> false } }
}
