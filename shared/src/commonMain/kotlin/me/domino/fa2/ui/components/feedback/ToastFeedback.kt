package me.domino.fa2.ui.components

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

data class AppFeedbackRequest(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (suspend () -> Unit)? = null,
)

val LocalShowFeedback: ProvidableCompositionLocal<(AppFeedbackRequest) -> Unit> =
    staticCompositionLocalOf {
      {}
    }

/** 统一 toast/snackbar 文案反馈入口（默认无操作）。 */
val LocalShowToast: ProvidableCompositionLocal<(String) -> Unit> = staticCompositionLocalOf { {} }
