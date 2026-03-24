package me.domino.fa2.ui.components.platform

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformVerticalScrollbar(scrollState: ScrollState, modifier: Modifier) {
  // Android 默认不显示桌面式滚动条。
}
