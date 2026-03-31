package me.domino.fa2.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import fa2.shared.generated.resources.*
import me.domino.fa2.data.model.PageState
import org.jetbrains.compose.resources.stringResource

internal enum class PageFailureDisplayMode {
  PassThrough,
  HardFallback,
}

internal fun <T> resolvePageFailureDisplayMode(
    state: PageState<T>,
    hasContent: Boolean,
): PageFailureDisplayMode =
    when (state) {
      is PageState.AuthRequired,
      PageState.CfChallenge,
      is PageState.MatureBlocked -> PageFailureDisplayMode.HardFallback
      is PageState.Error ->
          if (hasContent) {
            PageFailureDisplayMode.PassThrough
          } else {
            PageFailureDisplayMode.HardFallback
          }
      is PageState.Success,
      PageState.Loading -> PageFailureDisplayMode.PassThrough
    }

/** 统一渲染 `PageState` 的基础组件。 */
@Composable
fun <T> PageStateWrapper(
    /** 当前页面状态。 */
    state: PageState<T>,
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
          is PageState.AuthRequired -> {
            HardFallbackScreen(
                title = stringResource(Res.string.auth_probe_failed_title),
                body = state.message,
                onAction = onRetry,
            )
          }

          PageState.CfChallenge -> {
            HardFallbackScreen(
                title = stringResource(Res.string.cloudflare_challenge_title),
                body = stringResource(Res.string.cloudflare_challenge_body),
                onAction = onRetry,
                modifier = Modifier.testTag("cf-challenge-status"),
            )
          }

          is PageState.MatureBlocked -> {
            HardFallbackScreen(
                title = stringResource(Res.string.page_blocked),
                body = state.reason,
                onAction = onRetry,
            )
          }

          is PageState.Error -> {
            HardFallbackScreen(
                title = stringResource(Res.string.load_failed),
                body =
                    state.exception.message
                        ?: state.exception::class.simpleName
                        ?: stringResource(Res.string.unknown_error),
                onAction = onRetry,
            )
          }

          is PageState.Success,
          PageState.Loading -> {
            content()
          }
        }
  }
}
