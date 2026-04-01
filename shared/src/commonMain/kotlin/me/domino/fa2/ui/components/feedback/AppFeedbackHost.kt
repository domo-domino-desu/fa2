package me.domino.fa2.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 全局反馈宿主：负责提供 toast/snackbar 能力并渲染 SnackbarHost。 */
@Composable
fun AppFeedbackHost(content: @Composable () -> Unit) {
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()
  val showFeedback =
      remember(snackbarHostState, coroutineScope) {
        { request: AppFeedbackRequest ->
          val normalized = request.message.trim()
          if (normalized.isNotBlank()) {
            coroutineScope.launch {
              snackbarHostState.currentSnackbarData?.dismiss()
              val result =
                  snackbarHostState.showSnackbar(
                      message = normalized,
                      actionLabel = request.actionLabel?.trim()?.ifBlank { null },
                  )
              if (result == SnackbarResult.ActionPerformed) {
                request.onAction?.invoke()
              }
            }
          }
        }
      }
  val showToast =
      remember(showFeedback) { { message: String -> showFeedback(AppFeedbackRequest(message)) } }
  CompositionLocalProvider(
      LocalShowFeedback provides showFeedback,
      LocalShowToast provides showToast,
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      content()
      SnackbarHost(
          hostState = snackbarHostState,
          modifier =
              Modifier.align(Alignment.BottomCenter)
                  .padding(12.dp)
                  .then(accessibilitySnackbarModifier()),
      )
    }
  }
}
