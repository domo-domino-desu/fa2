package me.domino.fa2.ui.components.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import fa2.shared.generated.resources.*
import me.domino.fa2.data.model.QueryError
import me.domino.fa2.data.model.QueryState
import org.jetbrains.compose.resources.stringResource

@Composable
fun <T> PageStateWrapper(
    state: QueryState<T>,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
  if (shouldShowQueryContent(state)) {
    content()
    return
  }
  val error = (state as QueryState.Failure).error
  when (error) {
    is QueryError.AuthRequired ->
        HardFallbackScreen(
            title = stringResource(Res.string.auth_probe_failed_title),
            body = error.message,
            onAction = onRetry,
        )
    QueryError.CfChallenge ->
        HardFallbackScreen(
            title = stringResource(Res.string.cloudflare_challenge_title),
            body = stringResource(Res.string.cloudflare_challenge_body),
            onAction = onRetry,
            modifier = Modifier.testTag("cf-challenge-status"),
        )
    is QueryError.MatureBlocked ->
        HardFallbackScreen(
            title = stringResource(Res.string.page_blocked),
            body = error.reason,
            onAction = onRetry,
        )
    is QueryError.Exception ->
        HardFallbackScreen(
            title = stringResource(Res.string.load_failed),
            body = error.cause.message ?: error.cause::class.simpleName.orEmpty(),
            onAction = onRetry,
        )
  }
}

internal fun QueryState<*>.hasRenderableData(): Boolean = data != null

internal fun shouldShowQueryContent(state: QueryState<*>): Boolean =
    state !is QueryState.Failure || state.hasRenderableData()
