package me.domino.fa2.ui.pages.submission

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import fa2.shared.generated.resources.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.domino.fa2.application.submissionseries.SubmissionSeriesResolvedSeries
import me.domino.fa2.application.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.SubmissionThumbnail
import me.domino.fa2.data.repository.ActivityHistoryRepository
import me.domino.fa2.data.repository.SubmissionRepository
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.i18n.SystemLanguageProvider
import me.domino.fa2.ui.components.LocalShowToast
import me.domino.fa2.ui.components.platform.PlatformBackHandler
import me.domino.fa2.ui.components.platform.PlatformDownloadRequest
import me.domino.fa2.ui.components.platform.PlatformDownloadResult
import me.domino.fa2.ui.components.platform.rememberPlatformTextCopier
import me.domino.fa2.ui.components.platform.rememberPlatformUrlDownloader
import me.domino.fa2.ui.navigation.goBackHome
import me.domino.fa2.ui.navigation.openSubmissionSeries
import me.domino.fa2.ui.pages.browse.BrowseFilterState
import me.domino.fa2.ui.pages.browse.BrowseRouteScreen
import me.domino.fa2.ui.pages.search.SearchRouteScreen
import me.domino.fa2.ui.pages.user.route.UserChildRoute
import me.domino.fa2.ui.pages.user.route.UserRouteScreen
import me.domino.fa2.util.FaUrls
import me.domino.fa2.util.deriveSubmissionThumbnailUrlFromFullImage
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** 投稿详情浏览路由页面。 */
class SubmissionRouteScreen(
    /** 初始投稿 ID。 */
    private val initialSid: Int,
    /** 共享 context id。 */
    private val contextId: String,
    /** 当 holder 不含 sid 时的占位链接。 */
    private val seedSubmissionUrl: String? = null,
    /** 独立 submission 系列。 */
    private val submissionSeries: SubmissionSeriesResolvedSeries? = null,
) : Screen {
  override val key: String =
      "submission:$contextId:$initialSid:${seedSubmissionUrl.orEmpty()}:${submissionSeries?.candidateKey.orEmpty()}"

  /** 页面内容。 */
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val submissionRepository = koinInject<SubmissionRepository>()
    val translationService = koinInject<SubmissionDescriptionTranslationService>()
    val systemLanguageProvider = koinInject<SystemLanguageProvider>()
    val settingsService = koinInject<AppSettingsService>()
    val contextScreenModel =
        navigator.rememberNavigatorScreenModel<SubmissionContextScreenModel>(
            tag = "submission-context"
        ) {
          SubmissionContextScreenModel()
        }
    val fallbackSubmissionTitle = stringResource(Res.string.submission_fallback_title, initialSid)
    LaunchedEffect(contextId, initialSid, seedSubmissionUrl, submissionSeries?.candidateKey) {
      when {
        submissionSeries != null -> {
          val adapter =
              SeriesSubmissionSourceAdapter(
                  repository = submissionRepository,
                  series = submissionSeries,
              )
          when (val initialPage = adapter.loadInitialPage()) {
            is PageState.Success ->
                contextScreenModel.ensureSeedContext(
                    contextId = contextId,
                    adapter = adapter,
                    initialPage = initialPage.data,
                    selectedSid = initialSid,
                    revisionKey = submissionSeries.candidateKey,
                )

            else ->
                contextScreenModel.ensureSeedContext(
                    contextId = contextId,
                    sourceKind = SubmissionContextSourceKind.SEQUENCE,
                    items = submissionSeries.seedSubmissions,
                    selectedSid = initialSid,
                )
          }
        }

        seedSubmissionUrl != null ->
            contextScreenModel.ensureSeedContext(
                contextId = contextId,
                sourceKind = SubmissionContextSourceKind.DETACHED,
                items =
                    listOf(
                        SubmissionThumbnail(
                            id = initialSid,
                            submissionUrl = seedSubmissionUrl,
                            title = fallbackSubmissionTitle,
                            author = "",
                            thumbnailUrl = "",
                            thumbnailAspectRatio = 1f,
                            categoryTag = "",
                        )
                    ),
                selectedSid = initialSid,
            )
      }
    }
    val screenModel =
        navigator.rememberNavigatorScreenModel<SubmissionScreenModel>(
            tag = "submission-screen:$contextId"
        ) {
          SubmissionScreenModel(
              initialSid = initialSid,
              contextId = contextId,
              contextScreenModel = contextScreenModel,
              submissionSource = SubmissionPagerDetailSourceImpl(submissionRepository),
              translationService = translationService,
              settingsService = settingsService,
              systemLanguageProvider = systemLanguageProvider,
          )
        }
    val settings by settingsService.settings.collectAsState()
    val historyRepository = koinInject<ActivityHistoryRepository>()
    val state by screenModel.state.collectAsState()
    val pageState by screenModel.pageState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val downloadUrlHandler = rememberPlatformUrlDownloader()
    val copyTextToClipboard = rememberPlatformTextCopier()
    val showToast = LocalShowToast.current
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val tagCopiedText = stringResource(Res.string.tag_copied)
    val linkCopiedText = stringResource(Res.string.link_copied)
    val requestPagerFocus =
        remember(focusRequester) {
          {
            focusRequester.requestFocus()
            Unit
          }
        }
    val topBarActions = resolveTopBarActions(initialSid = initialSid, state = state)
    val zoomOverlayVisible = remember { mutableStateOf(false) }

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
    val handleBackNavigation = {
      if (settings.returnToCurrentSubmissionInWaterfall) {
        contextScreenModel.requestScrollToSelectedSubmission(contextId)
      }
      navigator.pop()
      Unit
    }
    PlatformBackHandler(enabled = true, onBack = handleBackNavigation)

    Column(
        modifier =
            Modifier.fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
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
                .testTag("submission-route")
    ) {
      SubmissionRouteChrome(
          state = state,
          pageState = pageState,
          topBarActions = topBarActions,
          zoomOverlayVisible = zoomOverlayVisible.value,
          blockedSubmissionMode = settings.blockedSubmissionPagerMode,
          onBack = handleBackNavigation,
          onGoHome = { navigator.goBackHome() },
          onDownload = { downloadRequest ->
            coroutineScope.launch {
              when (val result = downloadUrlHandler(downloadRequest)) {
                PlatformDownloadResult.NotHandled -> uriHandler.openUri(downloadRequest.downloadUrl)
                PlatformDownloadResult.Saved -> Unit
                is PlatformDownloadResult.HandledFailure -> showToast(result.message)
              }
            }
          },
          onRequestScrollToTop = screenModel::requestCurrentPageScrollToTop,
          onRetryPage = screenModel::retryCurrentDetail,
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
              showToast(tagCopiedText)
            }
          },
          onOpenBrowseFilter = { category, type, species ->
            navigator.push(
                BrowseRouteScreen(
                    initialFilter =
                        BrowseFilterState(
                            category = category,
                            type = type,
                            species = species,
                        )
                )
            )
          },
          onCopySubmissionUrl = { submissionUrl ->
            val normalizedUrl = submissionUrl.trim()
            if (normalizedUrl.isNotBlank() && copyTextToClipboard(normalizedUrl)) {
              showToast(linkCopiedText)
            }
          },
          onLoadAttachmentText = screenModel::loadAttachmentTextCurrent,
          onTranslateDescription = {
            if (settings.translationEnabled) {
              screenModel.translateDescriptionCurrent()
            }
          },
          onWrapDescriptionText = {
            if (settings.translationEnabled) {
              screenModel.toggleDescriptionWrapCurrent()
            }
          },
          onTranslateAttachment = {
            if (settings.translationEnabled) {
              screenModel.translateAttachmentCurrent()
            }
          },
          onWrapAttachmentText = {
            if (settings.translationEnabled) {
              screenModel.toggleAttachmentWrapCurrent()
            }
          },
          onOpenSubmissionSeries = { series -> navigator.openSubmissionSeries(series) },
          scrollOffsetOfSid = screenModel::scrollOffsetForSid,
          requestPagerFocus = requestPagerFocus,
          onZoomOverlayVisibilityChanged = { visible -> zoomOverlayVisible.value = visible },
          onPageScrollOffsetChanged = screenModel::setCurrentPageScrollOffset,
          onToggleFavorite = screenModel::toggleFavoriteCurrent,
      )
    }
  }
}

internal data class SubmissionTopBarActions(
    val shareUrl: String,
    val downloadRequest: PlatformDownloadRequest?,
)

private fun resolveTopBarActions(
    initialSid: Int,
    state: SubmissionPagerUiState,
): SubmissionTopBarActions {
  return when (state) {
    SubmissionPagerUiState.Empty ->
        SubmissionTopBarActions(shareUrl = FaUrls.submission(initialSid), downloadRequest = null)

    is SubmissionPagerUiState.Data -> {
      val currentItem = state.submissions.getOrNull(state.currentIndex)
      val currentDetail =
          currentItem?.let { item ->
            state.detailBySid[item.id] as? SubmissionDetailUiState.Success
          }
      SubmissionTopBarActions(
          shareUrl =
              currentDetail?.detail?.submissionUrl
                  ?: currentItem?.submissionUrl?.ifBlank { null }
                  ?: currentItem?.let { item -> FaUrls.submission(item.id) }
                  ?: FaUrls.submission(initialSid),
          downloadRequest =
              currentDetail?.detail?.let { detail ->
                val normalizedDownloadUrl = detail.downloadUrl?.trim().orEmpty()
                if (normalizedDownloadUrl.isBlank()) {
                  null
                } else {
                  PlatformDownloadRequest(
                      downloadUrl = normalizedDownloadUrl,
                      submissionId = detail.id,
                      title = detail.title,
                      username = detail.author,
                      category = detail.category,
                      rating = detail.rating,
                      type = detail.type,
                      species = detail.species,
                      downloadFileNameHint = detail.downloadFileName,
                  )
                }
              },
      )
    }
  }
}

@Composable
internal fun SubmissionImagePrefetchEffect(snapshot: SubmissionPagerUiState.Data) {
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
                deriveSubmissionThumbnailUrlFromFullImage(
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
