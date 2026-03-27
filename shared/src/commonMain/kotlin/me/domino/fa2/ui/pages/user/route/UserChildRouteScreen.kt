package me.domino.fa2.ui.pages.user.route

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import me.domino.fa2.data.repository.FavoritesRepository
import me.domino.fa2.data.repository.GalleryRepository
import me.domino.fa2.ui.navigation.rootNavigator
import me.domino.fa2.ui.pages.submission.SubmissionContextScreenModel
import me.domino.fa2.ui.pages.submission.SubmissionContextSourceKind
import me.domino.fa2.ui.pages.submission.SubmissionLoadedPage
import me.domino.fa2.ui.pages.submission.SubmissionRouteScreen
import me.domino.fa2.ui.pages.submission.UserSubmissionSourceAdapter
import me.domino.fa2.ui.pages.submission.pageNumberForSid
import me.domino.fa2.ui.pages.submission.toWaterfallPageControls
import me.domino.fa2.ui.pages.user.gallery.UserSubmissionSectionScreen
import me.domino.fa2.ui.pages.user.gallery.UserSubmissionSectionScreenModel
import me.domino.fa2.ui.pages.user.journal.JournalDetailRouteScreen
import me.domino.fa2.ui.pages.user.journal.UserJournalsScreen
import me.domino.fa2.ui.pages.user.journal.UserJournalsScreenModel
import me.domino.fa2.ui.pages.user.profile.resolveInitialUserScrollPosition
import me.domino.fa2.ui.pages.user.profile.userJournalsScrollLayout
import me.domino.fa2.ui.pages.user.profile.userSubmissionSectionScrollLayout
import me.domino.fa2.util.FaUrls
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/** User 子路由 screen。 */
class UserChildRouteScreen(
    /** 用户名。 */
    private val username: String,
    /** 当前子路由。 */
    val route: UserChildRoute,
    /** 初始文件夹 URL（仅首次创建时生效）。 */
    private val initialFolderUrl: String? = null,
) : Screen {
  override val key: String =
      "user-child:${username.lowercase()}:${route.routeKey}:${initialFolderUrl.orEmpty()}"

  @Composable
  override fun Content() {
    val headerContent = LocalUserHeaderContent.current
    val refreshHeader = LocalUserHeaderRefreshAction.current
    val resolveFolderUrl = LocalUserSubmissionFolderResolver.current
    val updateFolderUrl = LocalUserSubmissionFolderUpdater.current
    val resolveSharedTopScrollState = LocalUserSharedTopScrollStateResolver.current
    val updateSharedTopScrollState = LocalUserSharedTopScrollStateUpdater.current
    val resolveBodyScrollPosition = LocalUserBodyScrollPositionResolver.current
    val updateBodyScrollPosition = LocalUserBodyScrollPositionUpdater.current
    val resolveSubmissionSnapshot = LocalUserSubmissionSnapshotResolver.current
    val updateSubmissionSnapshot = LocalUserSubmissionSnapshotUpdater.current
    val updateCurrentRouteScrollToTopAction = LocalUserCurrentRouteScrollToTopActionUpdater.current
    val routeInitialFolderUrl = resolveFolderUrl(route) ?: initialFolderUrl
    when (route) {
      UserChildRoute.Journals -> {
        val localNavigator = LocalNavigator.currentOrThrow
        val rootNavigator = localNavigator.rootNavigator()
        val screenModel = koinScreenModel<UserJournalsScreenModel> { parametersOf(username) }
        val state by screenModel.state.collectAsState()
        val scrollKey = remember(username) { buildUserJournalsScrollKey(username) }
        key(scrollKey) {
          val layout =
              remember(headerContent) {
                userJournalsScrollLayout(hasHeader = headerContent != null)
              }
          val initialScrollPosition =
              remember(scrollKey, headerContent) {
                resolveInitialUserScrollPosition(
                    sharedTopScrollState = resolveSharedTopScrollState(),
                    bodyScrollPosition = resolveBodyScrollPosition(scrollKey),
                    layout = layout,
                )
              }
          val listState =
              rememberLazyListState(
                  initialFirstVisibleItemIndex = initialScrollPosition.firstVisibleItemIndex,
                  initialFirstVisibleItemScrollOffset =
                      initialScrollPosition.firstVisibleItemScrollOffset,
              )
          val scope = rememberCoroutineScope()
          LaunchedEffect(listState, updateCurrentRouteScrollToTopAction) {
            updateCurrentRouteScrollToTopAction {
              scope.launch { listState.animateScrollToItem(0) }
            }
          }
          var deferredBodyScrollPosition by
              remember(scrollKey) {
                mutableStateOf(initialScrollPosition.deferredBodyScrollPosition)
              }

          UserJournalsScreen(
              state = state,
              onOpenJournal = { item ->
                rootNavigator.push(
                    JournalDetailRouteScreen(
                        journalId = item.id,
                        journalUrl = item.journalUrl,
                    )
                )
              },
              onRetry = { screenModel.load(forceRefresh = true) },
              onRefresh = {
                refreshHeader?.invoke()
                screenModel.load(forceRefresh = true)
              },
              onLastVisibleIndexChanged = screenModel::onLastVisibleIndexChanged,
              onRetryLoadMore = screenModel::retryLoadMore,
              currentRoute = route,
              onSelectRoute = { target ->
                if (target != route) {
                  localNavigator.replaceAll(
                      UserChildRouteScreen(
                          username = username,
                          route = target,
                          initialFolderUrl = resolveFolderUrl(target),
                      )
                  )
                }
              },
              listState = listState,
              deferredBodyScrollPosition = deferredBodyScrollPosition,
              onDeferredBodyScrollPositionConsumed = { deferredBodyScrollPosition = null },
              onSharedTopScrollChanged = updateSharedTopScrollState,
              onBodyScrollPositionChanged = { position ->
                updateBodyScrollPosition(scrollKey, position)
              },
              headerContent = headerContent,
          )
        }
      }

      else -> {
        val localNavigator = LocalNavigator.currentOrThrow
        val rootNavigator = localNavigator.rootNavigator()
        val contextScreenModel =
            rootNavigator.rememberNavigatorScreenModel<SubmissionContextScreenModel>(
                tag = "submission-context"
            ) {
              SubmissionContextScreenModel()
            }
        val galleryRepository = koinInject<GalleryRepository>()
        val favoritesRepository = koinInject<FavoritesRepository>()
        val holderTag = "user-submission-holder:${username.lowercase()}:${route.routeKey}"
        val scrollKey =
            remember(username, route, routeInitialFolderUrl) {
              buildUserSubmissionScrollKey(
                  username = username,
                  route = route,
                  folderUrl = routeInitialFolderUrl,
              )
            }
        val initialSnapshot = remember(scrollKey) { resolveSubmissionSnapshot(scrollKey) }
        val screenModel =
            koinScreenModel<UserSubmissionSectionScreenModel> {
              parametersOf(
                  username,
                  route,
                  routeInitialFolderUrl,
                  initialSnapshot,
              )
            }
        val state by screenModel.state.collectAsState()
        val contextState by contextScreenModel.state(holderTag).collectAsState()
        val rootPageUrl =
            remember(username, route, routeInitialFolderUrl) {
              routeInitialFolderUrl
                  ?: when (route) {
                    UserChildRoute.Gallery -> FaUrls.gallery(username)
                    UserChildRoute.Favorites -> FaUrls.favorites(username)
                    UserChildRoute.Journals -> holderTag
                  }
            }
        LaunchedEffect(
            scrollKey,
            state.submissions,
            state.nextPageUrl,
            state.folderGroups,
        ) {
          updateSubmissionSnapshot(scrollKey, state)
          if (state.submissions.isEmpty()) return@LaunchedEffect
          contextScreenModel.syncRootPage(
              contextId = holderTag,
              sourceKind =
                  when (route) {
                    UserChildRoute.Gallery -> SubmissionContextSourceKind.GALLERY
                    UserChildRoute.Favorites -> SubmissionContextSourceKind.FAVORITES
                    UserChildRoute.Journals -> SubmissionContextSourceKind.GALLERY
                  },
              adapter =
                  UserSubmissionSourceAdapter(
                      route = route,
                      username = username,
                      initialPageUrl = routeInitialFolderUrl,
                      galleryRepository = galleryRepository,
                      favoritesRepository = favoritesRepository,
                  ),
              page =
                  SubmissionLoadedPage(
                      pageId = rootPageUrl,
                      requestKey = rootPageUrl,
                      items = state.submissions,
                      pageNumber = 1,
                      nextRequestKey = state.nextPageUrl,
                      firstRequestKey =
                          when (route) {
                            UserChildRoute.Gallery -> rootPageUrl
                            UserChildRoute.Favorites -> rootPageUrl
                            UserChildRoute.Journals -> routeInitialFolderUrl
                          },
                  ),
              revisionKey = buildUserSubmissionScrollKey(username, route, routeInitialFolderUrl),
          )
        }
        key(scrollKey) {
          val layout =
              remember(headerContent) {
                userSubmissionSectionScrollLayout(hasHeader = headerContent != null)
              }
          val initialScrollPosition =
              remember(scrollKey, headerContent) {
                resolveInitialUserScrollPosition(
                    sharedTopScrollState = resolveSharedTopScrollState(),
                    bodyScrollPosition = resolveBodyScrollPosition(scrollKey),
                    layout = layout,
                )
              }
          val waterfallState =
              rememberLazyStaggeredGridState(
                  initialFirstVisibleItemIndex =
                      contextState?.waterfallViewport?.firstVisibleItemIndex
                          ?: initialScrollPosition.firstVisibleItemIndex,
                  initialFirstVisibleItemScrollOffset =
                      contextState?.waterfallViewport?.firstVisibleItemScrollOffset
                          ?: initialScrollPosition.firstVisibleItemScrollOffset,
              )
          val scope = rememberCoroutineScope()
          LaunchedEffect(waterfallState, updateCurrentRouteScrollToTopAction) {
            updateCurrentRouteScrollToTopAction {
              scope.launch { waterfallState.animateScrollToItem(0) }
            }
          }
          var deferredBodyScrollPosition by
              remember(scrollKey) {
                mutableStateOf(initialScrollPosition.deferredBodyScrollPosition)
              }
          val pageControls = contextState?.toWaterfallPageControls()

          UserSubmissionSectionScreen(
              route = route,
              state =
                  contextState?.let { snapshot ->
                    state.copy(
                        submissions = snapshot.flatItems.ifEmpty { state.submissions },
                        nextPageUrl = if (snapshot.hasNextPage) "context:next" else null,
                        isLoadingMore = snapshot.loading.appendLoading,
                        appendErrorMessage = snapshot.loading.appendErrorMessage,
                    )
                  } ?: state,
              onRetry = { screenModel.load(forceRefresh = true) },
              onRefresh = {
                refreshHeader?.invoke()
                screenModel.load(forceRefresh = true)
              },
              onOpenSubmission = { item ->
                contextScreenModel.selectSubmission(holderTag, item.id)
                rootNavigator.push(
                    SubmissionRouteScreen(initialSid = item.id, contextId = holderTag)
                )
              },
              onOpenFolder = { folderUrl ->
                updateFolderUrl(route, folderUrl)
                screenModel.openFolder(folderUrl)
              },
              onLastVisibleIndexChanged = { lastVisibleIndex ->
                val items = contextState?.flatItems ?: state.submissions
                if (items.isNotEmpty() && lastVisibleIndex > items.lastIndex - 10) {
                  contextScreenModel.loadNextPageIfNeeded(holderTag)
                }
              },
              onRetryLoadMore = {
                contextScreenModel.loadNextPageIfNeeded(holderTag, force = true)
              },
              onSelectRoute = { target ->
                if (target != route) {
                  localNavigator.replaceAll(
                      UserChildRouteScreen(
                          username = username,
                          route = target,
                          initialFolderUrl = resolveFolderUrl(target),
                      )
                  )
                }
              },
              headerContent = headerContent,
              waterfallState = waterfallState,
              deferredBodyScrollPosition = deferredBodyScrollPosition,
              onDeferredBodyScrollPositionConsumed = { deferredBodyScrollPosition = null },
              onSharedTopScrollChanged = updateSharedTopScrollState,
              onBodyScrollPositionChanged = { position ->
                updateBodyScrollPosition(scrollKey, position)
              },
              pageControls = pageControls,
              canLoadPreviousPageAtTop = contextState?.hasPreviousPage == true,
              loadingPreviousPage = contextState?.loading?.prependLoading == true,
              prependErrorMessage = contextState?.loading?.prependErrorMessage,
              onLoadPreviousPageAtTop = {
                contextScreenModel.loadPreviousPageIfNeeded(holderTag, force = true)
              },
              onLoadFirstPage = { contextScreenModel.navigateToFirstPage(holderTag) },
              onLoadPreviousPage = { contextScreenModel.navigateToPreviousPage(holderTag) },
              onJumpToPage = { pageNumber ->
                contextScreenModel.navigateToPage(holderTag, pageNumber)
              },
              onLoadNextPage = { contextScreenModel.navigateToNextPage(holderTag) },
              onLoadLastPage = { contextScreenModel.navigateToLastPage(holderTag) },
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
                    currentPageNumber = contextState?.pageNumberForSid(viewport.anchorSid),
                )
              },
          )
        }
      }
    }
  }
}
