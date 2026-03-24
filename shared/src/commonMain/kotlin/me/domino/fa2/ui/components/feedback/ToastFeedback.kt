package me.domino.fa2.ui.components

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/** 统一 toast/snackbar 文案反馈入口（默认无操作）。 */
val LocalShowToast: ProvidableCompositionLocal<(String) -> Unit> = staticCompositionLocalOf { {} }
