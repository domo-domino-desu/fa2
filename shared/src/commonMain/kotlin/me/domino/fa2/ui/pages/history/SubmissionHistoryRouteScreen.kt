package me.domino.fa2.ui.pages.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import fa2.shared.generated.resources.*
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.repository.ActivityHistoryRepository
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.ui.components.submission.SubmissionWaterfall
import me.domino.fa2.ui.layouts.SubmissionHistoryRouteTopBar
import me.domino.fa2.ui.navigation.goBackHome
import me.domino.fa2.ui.navigation.rootNavigator
import me.domino.fa2.ui.pages.submission.SubmissionContextScreenModel
import me.domino.fa2.ui.pages.submission.SubmissionContextSourceKind
import me.domino.fa2.ui.pages.submission.SubmissionRouteScreen
import me.domino.fa2.ui.pages.submission.WaterfallViewportState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** 投稿浏览历史页面（瀑布流）。 */
class SubmissionHistoryRouteScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val rootNavigator = navigator.rootNavigator()
    val historyRepository = koinInject<ActivityHistoryRepository>()
    val settingsService = koinInject<AppSettingsService>()
    val settings by settingsService.settings.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var submissions by remember { mutableStateOf<List<SubmissionThumbnail>>(emptyList()) }
    val holderTag = "submission-list-holder:history"
    val contextScreenModel =
        rootNavigator.rememberNavigatorScreenModel<SubmissionContextScreenModel>(
            tag = "submission-context"
        ) {
          SubmissionContextScreenModel()
        }
    val contextState by contextScreenModel.state(holderTag).collectAsState()
    val initialViewport =
        remember(contextScreenModel, holderTag) {
          contextScreenModel.snapshot(holderTag)?.waterfallViewport ?: WaterfallViewportState()
        }
    val waterfallState =
        rememberLazyStaggeredGridState(
            initialFirstVisibleItemIndex = initialViewport.firstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset = initialViewport.firstVisibleItemScrollOffset,
        )

    LaunchedEffect(Unit) {
      submissions = historyRepository.loadSubmissionHistory()
      loading = false
    }

    LaunchedEffect(submissions) {
      if (submissions.isNotEmpty()) {
        contextScreenModel.ensureSeedContext(
            contextId = holderTag,
            sourceKind = SubmissionContextSourceKind.HISTORY,
            items = submissions,
            selectedSid = submissions.firstOrNull()?.id,
        )
      }
    }

    Column(modifier = Modifier.fillMaxSize()) {
      SubmissionHistoryRouteTopBar(
          onBack = { navigator.pop() },
          onGoHome = { navigator.goBackHome() },
          onTitleClick = { coroutineScope.launch { waterfallState.animateScrollToItem(0) } },
      )

      when {
        loading -> {
          Text(
              text = stringResource(Res.string.loading_submission_history),
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
          )
        }

        submissions.isEmpty() -> {
          Text(
              text = stringResource(Res.string.empty_submission_history),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
          )
        }

        else -> {
          SubmissionWaterfall(
              items = submissions,
              onItemClick = { item ->
                contextScreenModel.selectSubmission(holderTag, item.id)
                navigator.push(SubmissionRouteScreen(initialSid = item.id, contextId = holderTag))
              },
              onLastVisibleIndexChanged = {},
              canLoadMore = false,
              loadingMore = false,
              appendErrorMessage = null,
              onRetryLoadMore = {},
              state = waterfallState,
              minCardWidthDp = settings.waterfallMinCardWidthDp,
              blockedSubmissionMode = settings.blockedSubmissionWaterfallMode,
              pendingScrollRequest = contextState?.waterfallViewport?.scrollRequest,
              onConsumeScrollRequest = { version ->
                contextScreenModel.consumeWaterfallScrollRequest(holderTag, version)
              },
              onViewportChanged = { viewport ->
                contextScreenModel.updateWaterfallViewport(
                    contextId = holderTag,
                    firstVisibleItemIndex = viewport.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = viewport.firstVisibleItemScrollOffset,
                    anchorSid = viewport.anchorSid,
                    currentPageNumber = null,
                )
              },
          )
        }
      }
    }
  }
}
