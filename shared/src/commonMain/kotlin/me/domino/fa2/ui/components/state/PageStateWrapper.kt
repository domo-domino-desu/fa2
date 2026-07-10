package me.domino.fa2.ui.components.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import fa2.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal enum class PageFailureDisplayMode {
  PassThrough,
  HardFallback,
}

internal fun <T> resolvePageFailureDisplayMode(
    state: UiPageState<T>,
    hasContent: Boolean,
): PageFailureDisplayMode =
    when (state) {
      is UiPageState.AuthRequired,
      UiPageState.CfChallenge,
      is UiPageState.MatureBlocked -> PageFailureDisplayMode.HardFallback
      is UiPageState.Error ->
          if (hasContent) {
            PageFailureDisplayMode.PassThrough
          } else {
            PageFailureDisplayMode.HardFallback
          }
      is UiPageState.Success,
      UiPageState.Loading -> PageFailureDisplayMode.PassThrough
    }

/** 统一渲染 UI 页面状态的基础组件。 */
@Composable
fun <T> PageStateWrapper(
    /** 当前页面状态。 */
    state: UiPageState<T>,
    /** 当前页面是否已有可展示内容。 */
    hasContent: Boolean,
    /** 重试回调。 */
    onRetry: () -> Unit,
    /** 页面内容渲染器。 */
    content: @Composable () -> Unit,
) {
  when (resolvePageFailureDisplayMode(state = state, hasContent = hasContent)) {
    PageFailureDisplayMode.PassThrough -> {
      content()
    }

    PageFailureDisplayMode.HardFallback ->
        when (state) {
          is UiPageState.AuthRequired -> {
            HardFallbackScreen(
                title = stringResource(Res.string.auth_probe_failed_title),
                body = state.message,
                onAction = onRetry,
            )
          }

          UiPageState.CfChallenge -> {
            HardFallbackScreen(
                title = stringResource(Res.string.cloudflare_challenge_title),
                body = stringResource(Res.string.cloudflare_challenge_body),
                onAction = onRetry,
                modifier = Modifier.testTag("cf-challenge-status"),
            )
          }

          is UiPageState.MatureBlocked -> {
            HardFallbackScreen(
                title = stringResource(Res.string.page_blocked),
                body = state.reason,
                onAction = onRetry,
            )
          }

          is UiPageState.Error -> {
            HardFallbackScreen(
                title = stringResource(Res.string.load_failed),
                body =
                    state.exception.message
                        ?: state.exception::class.simpleName
                        ?: stringResource(Res.string.unknown_error),
                onAction = onRetry,
            )
          }

          is UiPageState.Success,
          UiPageState.Loading -> {
            content()
          }
        }
  }
}
