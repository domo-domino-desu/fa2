package me.domino.fa2.ui.screen.feed

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.repository.ActivityHistoryRepository
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.ui.component.LocalShowToast
import me.domino.fa2.ui.component.platform.rememberPlatformTextCopier
import me.domino.fa2.ui.component.platform.rememberPlatformUrlDownloader
import me.domino.fa2.ui.component.submission.SubmissionPager
import me.domino.fa2.ui.component.topbar.SubmissionRouteTopBar
import me.domino.fa2.ui.navigation.SubmissionListHolder
import me.domino.fa2.ui.navigation.goBackHome
import me.domino.fa2.ui.screen.browse.BrowseFilterState
import me.domino.fa2.ui.screen.browse.BrowseRouteScreen
import me.domino.fa2.ui.screen.search.SearchRouteScreen
import me.domino.fa2.ui.screen.submission.SubmissionDetailUiState
import me.domino.fa2.ui.screen.submission.SubmissionPagerUiState
import me.domino.fa2.ui.screen.submission.SubmissionScreenModel
import me.domino.fa2.ui.screen.submission.computeSubmissionPrefetchIndices
import me.domino.fa2.ui.screen.submission.pagerPrefetchDebounceMs
import me.domino.fa2.ui.screen.user.UserChildRoute
import me.domino.fa2.ui.screen.user.UserRouteScreen
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.ParserUtils
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/** 投稿详情浏览路由页面。 */
class SubmissionRouteScreen(
  /** 初始投稿 ID。 */
  private val initialSid: Int,
  /** 列表共享持有器 tag。 */
  private val holderTag: String = "submission-list-holder",
  /** 当 holder 不含 sid 时的占位链接。 */
  private val seedSubmissionUrl: String? = null,
) : Screen {
  override val key: String = "submission:$holderTag:$initialSid:${seedSubmissionUrl.orEmpty()}"

  /** 页面内容。 */
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val submissionListHolder =
      navigator.rememberNavigatorScreenModel<SubmissionListHolder>(tag = holderTag) {
        SubmissionListHolder()
      }
    if (seedSubmissionUrl != null && !submissionListHolder.setCurrentBySid(initialSid)) {
      submissionListHolder.replace(
        submissions =
          listOf(
            SubmissionThumbnail(
              id = initialSid,
              submissionUrl = seedSubmissionUrl,
              title = "Submission #$initialSid",
              author = "",
              thumbnailUrl = "",
              thumbnailAspectRatio = 1f,
            )
          ),
        nextPageUrl = null,
      )
    }
    val screenModel =
      koinScreenModel<SubmissionScreenModel> { parametersOf(initialSid, submissionListHolder) }
    val settingsService = koinInject<AppSettingsService>()
    val settings by settingsService.settings.collectAsState()
    val descriptionTranslationService = koinInject<SubmissionDescriptionTranslationService>()
    val historyRepository = koinInject<ActivityHistoryRepository>()
    val state by screenModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val downloadUrlHandler = rememberPlatformUrlDownloader()
    val copyTextToClipboard = rememberPlatformTextCopier()
    val showToast = LocalShowToast.current
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val requestPagerFocus =
      remember(focusRequester) {
        {
          focusRequester.requestFocus()
          Unit
        }
      }
    val topBarActions = resolveTopBarActions(initialSid = initialSid, state = state)
    var zoomOverlayVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { requestPagerFocus() }
    LaunchedEffect(screenModel, showToast) {
      screenModel.toastMessages.collect { message -> showToast(message) }
    }
    val currentHistoryItem =
      when (val snapshot = state) {
        SubmissionPagerUiState.Empty -> null
        is SubmissionPagerUiState.Data -> snapshot.submissions.getOrNull(snapshot.currentIndex)
      }
    LaunchedEffect(currentHistoryItem?.id) {
      currentHistoryItem?.let { item -> historyRepository.recordSubmissionVisit(item) }
    }

    Column(
      modifier =
        Modifier.fillMaxSize().focusRequester(focusRequester).focusable().onPreviewKeyEvent { event
          ->
          if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
          when (event.key) {
            Key.DirectionLeft -> {
              screenModel.previous()
              true
            }

            Key.DirectionRight -> {
              screenModel.next()
              true
            }

            else -> false
          }
        }
    ) {
      if (!zoomOverlayVisible) {
        SubmissionRouteTopBar(
          onBack = { navigator.pop() },
          onGoHome = { navigator.goBackHome() },
          shareUrl = topBarActions.shareUrl,
          downloadUrl = topBarActions.downloadUrl,
          onDownload = {
            topBarActions.downloadUrl?.let { downloadUrl ->
              coroutineScope.launch {
                if (!downloadUrlHandler(downloadUrl)) {
                  uriHandler.openUri(downloadUrl)
                }
              }
            }
          },
        )
      }
      when (val snapshot = state) {
        SubmissionPagerUiState.Empty -> {
          Text(
            text = "当前没有可浏览内容。",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
          )
        }

        is SubmissionPagerUiState.Data -> {
          SubmissionImagePrefetchEffect(snapshot = snapshot)
          Box(modifier = Modifier.fillMaxSize()) {
            SubmissionPager(
              submissions = snapshot.submissions,
              detailBySid = snapshot.detailBySid,
              initialIndex = snapshot.currentIndex,
              onRetryCurrentDetail = screenModel::retryCurrentDetail,
              onPageChanged = screenModel::onPageChanged,
              onOpenAuthor = { author ->
                val normalized = author.trim()
                if (normalized.isNotBlank()) {
                  navigator.push(
                    UserRouteScreen(
                      username = normalized,
                      initialChildRoute = UserChildRoute.Gallery,
                    )
                  )
                }
              },
              onSearchKeyword = { query ->
                val normalized = query.trim()
                if (normalized.isNotBlank()) {
                  navigator.push(SearchRouteScreen(initialQuery = normalized))
                }
              },
              onKeywordLongPress = { tag ->
                val normalizedTag = tag.trim()
                if (normalizedTag.isNotBlank() && copyTextToClipboard(normalizedTag)) {
                  showToast("标签已复制")
                }
              },
              onOpenBrowseFilter = { category, type, species ->
                navigator.push(
                  BrowseRouteScreen(
                    initialFilter =
                      BrowseFilterState(category = category, type = type, species = species)
                  )
                )
              },
              descriptionTranslationService = descriptionTranslationService,
              requestPagerFocus = requestPagerFocus,
              onZoomOverlayVisibilityChanged = { visible -> zoomOverlayVisible = visible },
              blockedSubmissionMode = settings.blockedSubmissionPagerMode,
            )

            val currentItem = snapshot.submissions.getOrNull(snapshot.currentIndex)
            val detailState = currentItem?.let { item ->
              snapshot.detailBySid[item.id] as? SubmissionDetailUiState.Success
            }
            if (
              !zoomOverlayVisible &&
                detailState != null &&
                detailState.detail.favoriteActionUrl.isNotBlank()
            ) {
              FloatingActionButton(
                onClick = {
                  screenModel.toggleFavoriteCurrent()
                  requestPagerFocus()
                },
                modifier =
                  Modifier.align(Alignment.BottomEnd).padding(20.dp).focusProperties {
                    canFocus = false
                  },
                containerColor =
                  if (detailState.detail.isFavorited) {
                    MaterialTheme.colorScheme.primaryContainer
                  } else {
                    MaterialTheme.colorScheme.surface
                  },
              ) {
                if (detailState.favoriteUpdating) {
                  LoadingIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.primary,
                  )
                } else {
                  Icon(
                    imageVector =
                      if (detailState.detail.isFavorited) {
                        Icons.Filled.Favorite
                      } else {
                        Icons.Filled.FavoriteBorder
                      },
                    contentDescription =
                      if (detailState.detail.isFavorited) {
                        "Unfavorite"
                      } else {
                        "Favorite"
                      },
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

private data class SubmissionTopBarActions(val shareUrl: String, val downloadUrl: String?)

private fun resolveTopBarActions(
  initialSid: Int,
  state: SubmissionPagerUiState,
): SubmissionTopBarActions {
  return when (state) {
    SubmissionPagerUiState.Empty ->
      SubmissionTopBarActions(shareUrl = FaUrls.submission(initialSid), downloadUrl = null)

    is SubmissionPagerUiState.Data -> {
      val currentItem = state.submissions.getOrNull(state.currentIndex)
      val currentDetail = currentItem?.let { item ->
        state.detailBySid[item.id] as? SubmissionDetailUiState.Success
      }
      SubmissionTopBarActions(
        shareUrl =
          currentDetail?.detail?.submissionUrl
            ?: currentItem?.submissionUrl?.ifBlank { null }
            ?: currentItem?.let { item -> FaUrls.submission(item.id) }
            ?: FaUrls.submission(initialSid),
        downloadUrl =
          currentDetail?.detail?.downloadUrl?.trim()?.takeIf { value -> value.isNotBlank() },
      )
    }
  }
}

@Composable
private fun SubmissionImagePrefetchEffect(snapshot: SubmissionPagerUiState.Data) {
  val platformContext = LocalPlatformContext.current
  val prefetchedUrls = remember { mutableSetOf<String>() }

  LaunchedEffect(snapshot.currentIndex, snapshot.submissions, snapshot.detailBySid) {
    delay(pagerPrefetchDebounceMs)

    if (snapshot.submissions.isEmpty()) return@LaunchedEffect
    val imageLoader = SingletonImageLoader.get(platformContext)
    val targetIndices =
      computeSubmissionPrefetchIndices(
        currentIndex = snapshot.currentIndex,
        lastIndex = snapshot.submissions.lastIndex,
      )

    targetIndices.forEach { index ->
      val item = snapshot.submissions.getOrNull(index) ?: return@forEach
      val detailState = snapshot.detailBySid[item.id]
      val derivedThumbnailUrl =
        (detailState as? SubmissionDetailUiState.Success)
          ?.detail
          ?.fullImageUrl
          ?.let { fullImageUrl ->
            ParserUtils.deriveSubmissionThumbnailUrlFromFullImage(
              sid = item.id,
              fullImageUrl = fullImageUrl,
            )
          }
          .orEmpty()
      val url =
        when (detailState) {
          is SubmissionDetailUiState.Success -> {
            detailState.detail.fullImageUrl
              .ifBlank { detailState.detail.previewImageUrl }
              .ifBlank { item.thumbnailUrl.ifBlank { derivedThumbnailUrl } }
          }

          else -> item.thumbnailUrl.ifBlank { derivedThumbnailUrl }
        }.normalizePrefetchUrl()

      if (url.isBlank() || !prefetchedUrls.add(url)) return@forEach
      imageLoader.enqueue(ImageRequest.Builder(platformContext).data(url).build())
    }
  }
}

private fun String.normalizePrefetchUrl(): String {
  val normalized = trim()
  if (normalized.startsWith("//")) return "https:$normalized"
  return normalized
}
