package me.domino.fa2.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal enum class PaginationRetryDirection {
  Prepend,
  Append,
}

internal enum class PaginationRetryTextKey {
  PreviousLoading,
  PreviousError,
  PreviousHint,
  AppendLoading,
  AppendError,
  AppendHint,
  End,
}

internal enum class PaginationRetryActionKey {
  LoadPrevious,
  ManualLoadPrevious,
  LoadNext,
  ManualLoadNext,
}

internal data class PaginationRetryBarModel(
    val visible: Boolean,
    val textKey: PaginationRetryTextKey,
    val actionKey: PaginationRetryActionKey? = null,
)

internal fun resolvePaginationRetryBarModel(
    direction: PaginationRetryDirection,
    canLoad: Boolean,
    loading: Boolean,
    hasError: Boolean,
): PaginationRetryBarModel =
    when (direction) {
      PaginationRetryDirection.Prepend ->
          when {
            loading -> PaginationRetryBarModel(true, PaginationRetryTextKey.PreviousLoading)
            hasError ->
                PaginationRetryBarModel(
                    visible = true,
                    textKey = PaginationRetryTextKey.PreviousError,
                    actionKey =
                        if (canLoad) {
                          PaginationRetryActionKey.ManualLoadPrevious
                        } else {
                          null
                        },
                )
            canLoad ->
                PaginationRetryBarModel(
                    visible = true,
                    textKey = PaginationRetryTextKey.PreviousHint,
                    actionKey = PaginationRetryActionKey.LoadPrevious,
                )
            else -> PaginationRetryBarModel(false, PaginationRetryTextKey.PreviousHint)
          }

      PaginationRetryDirection.Append ->
          when {
            loading -> PaginationRetryBarModel(true, PaginationRetryTextKey.AppendLoading)
            hasError && canLoad ->
                PaginationRetryBarModel(
                    visible = true,
                    textKey = PaginationRetryTextKey.AppendError,
                    actionKey = PaginationRetryActionKey.ManualLoadNext,
                )
            canLoad ->
                PaginationRetryBarModel(
                    visible = true,
                    textKey = PaginationRetryTextKey.AppendHint,
                    actionKey = PaginationRetryActionKey.LoadNext,
                )
            else -> PaginationRetryBarModel(true, PaginationRetryTextKey.End)
          }
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PaginationRetryBar(
    direction: PaginationRetryDirection,
    canLoad: Boolean,
    loading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val model =
      resolvePaginationRetryBarModel(
          direction = direction,
          canLoad = canLoad,
          loading = loading,
          hasError = !errorMessage.isNullOrBlank(),
      )
  if (!model.visible) {
    return
  }

  Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
      modifier = modifier.fillMaxWidth(),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text =
              when (model.textKey) {
                PaginationRetryTextKey.PreviousLoading ->
                    stringResource(Res.string.loading_previous_page_content)
                PaginationRetryTextKey.PreviousError ->
                    stringResource(Res.string.auto_load_failed_manual_previous_page)
                PaginationRetryTextKey.PreviousHint ->
                    stringResource(Res.string.continue_auto_load_previous_page_with_manual_fallback)
                PaginationRetryTextKey.AppendLoading ->
                    stringResource(Res.string.loading_more_content)
                PaginationRetryTextKey.AppendError ->
                    stringResource(Res.string.auto_load_failed_manual_next_page)
                PaginationRetryTextKey.AppendHint ->
                    stringResource(Res.string.continue_auto_load_next_page_with_manual_fallback)
                PaginationRetryTextKey.End -> stringResource(Res.string.reached_current_results_end)
              },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      when {
        loading -> {
          Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            LoadingIndicator(
                modifier = Modifier.padding(top = 2.dp).size(22.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(Res.string.loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
          }
        }

        model.actionKey != null -> {
          AssistChip(
              onClick = onRetry,
              label = {
                val actionKey = model.actionKey
                Text(
                    text =
                        when (actionKey) {
                          PaginationRetryActionKey.LoadPrevious ->
                              stringResource(Res.string.load_previous_page)
                          PaginationRetryActionKey.ManualLoadPrevious ->
                              stringResource(Res.string.manual_load_previous_page)
                          PaginationRetryActionKey.LoadNext ->
                              stringResource(Res.string.load_next_page)
                          PaginationRetryActionKey.ManualLoadNext ->
                              stringResource(Res.string.manual_load_next_page)
                        }
                )
              },
          )
        }
      }
    }
  }
}
