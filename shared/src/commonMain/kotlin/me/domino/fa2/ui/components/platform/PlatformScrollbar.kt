package me.domino.fa2.ui.components.platform

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 在通用 UI 中使用平台滚动条。 */
@Composable
expect fun PlatformVerticalScrollbar(scrollState: ScrollState, modifier: Modifier = Modifier)
