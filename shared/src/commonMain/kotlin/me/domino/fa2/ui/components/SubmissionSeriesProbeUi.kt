package me.domino.fa2.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.series_probe_detect_content_description
import fa2.shared.generated.resources.series_probe_loading
import fa2.shared.generated.resources.series_probe_not_found
import fa2.shared.generated.resources.series_probe_open_first_content_description
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.domino.fa2.application.submissionseries.SubmissionSeriesResolvedSeries
import me.domino.fa2.application.submissionseries.SubmissionSeriesResolver
import me.domino.fa2.domain.translation.SubmissionDescriptionBlock
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

internal const val submissionSeriesMinimumLoadingMs: Long = 500L

internal data class SubmissionSeriesProbeConfig(
    val sourceKey: String = "",
    val sourceBlocks: List<SubmissionDescriptionBlock> = emptyList(),
    val baseUrl: String,
    val onOpenSeries: (SubmissionSeriesResolvedSeries) -> Unit,
)

internal data class SubmissionSeriesTrailingAction(
    val state: SubmissionSeriesTrailingActionState,
    val contentDescription: String,
    val onClick: () -> Unit,
)

internal enum class SubmissionSeriesTrailingActionState {
  IDLE,
  LOADING,
  RESOLVED,
}

private sealed interface SubmissionSeriesProbeUiState {
  data object Idle : SubmissionSeriesProbeUiState

  data object Loading : SubmissionSeriesProbeUiState

  data class Resolved(val series: SubmissionSeriesResolvedSeries) : SubmissionSeriesProbeUiState
}

@Composable
internal fun rememberSubmissionSeriesTrailingActions(
    config: SubmissionSeriesProbeConfig?,
): Map<Int, SubmissionSeriesTrailingAction> {
  if (config == null) return emptyMap()

  val resolver = koinInject<SubmissionSeriesResolver>()
  val scope = rememberCoroutineScope()
  val showToast = LocalShowToast.current
  val candidate =
      remember(config.sourceKey, config.baseUrl, config.sourceBlocks) {
        resolver.detectCandidate(
            sourceBlocks = config.sourceBlocks,
            baseUrl = config.baseUrl,
        )
      } ?: return emptyMap()

  var uiState by
      remember(config.sourceKey, candidate.candidateKey) {
        mutableStateOf<SubmissionSeriesProbeUiState>(SubmissionSeriesProbeUiState.Idle)
      }
  val detectContentDescription = stringResource(Res.string.series_probe_detect_content_description)
  val loadingContentDescription = stringResource(Res.string.series_probe_loading)
  val openFirstContentDescription =
      stringResource(Res.string.series_probe_open_first_content_description)
  val notFoundMessage = stringResource(Res.string.series_probe_not_found)
  val action =
      SubmissionSeriesTrailingAction(
          state =
              when (uiState) {
                SubmissionSeriesProbeUiState.Idle -> SubmissionSeriesTrailingActionState.IDLE
                SubmissionSeriesProbeUiState.Loading -> SubmissionSeriesTrailingActionState.LOADING
                is SubmissionSeriesProbeUiState.Resolved ->
                    SubmissionSeriesTrailingActionState.RESOLVED
              },
          contentDescription =
              when (uiState) {
                SubmissionSeriesProbeUiState.Idle -> detectContentDescription
                SubmissionSeriesProbeUiState.Loading -> loadingContentDescription
                is SubmissionSeriesProbeUiState.Resolved -> openFirstContentDescription
              },
          onClick = {
            when (val snapshot = uiState) {
              SubmissionSeriesProbeUiState.Loading -> Unit
              is SubmissionSeriesProbeUiState.Resolved -> config.onOpenSeries(snapshot.series)
              SubmissionSeriesProbeUiState.Idle -> {
                scope.launch {
                  uiState = SubmissionSeriesProbeUiState.Loading
                  val startMark = TimeSource.Monotonic.markNow()
                  val resolvedSeries = resolver.resolveSeries(candidate)
                  val remaining =
                      (submissionSeriesMinimumLoadingMs.milliseconds - startMark.elapsedNow())
                          .inWholeMilliseconds
                  if (remaining > 0L) {
                    delay(remaining)
                  }

                  if (resolvedSeries != null) {
                    uiState = SubmissionSeriesProbeUiState.Resolved(resolvedSeries)
                  } else {
                    uiState = SubmissionSeriesProbeUiState.Idle
                    showToast(notFoundMessage)
                  }
                }
              }
            }
          },
      )
  return remember(action, candidate.anchorBlockIndex) {
    mapOf(candidate.anchorBlockIndex to action)
  }
}
