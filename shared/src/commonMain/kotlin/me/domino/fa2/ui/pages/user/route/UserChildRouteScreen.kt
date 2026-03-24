package me.domino.fa2.ui.pages.user

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import me.domino.fa2.ui.navigation.SubmissionListHolder
import me.domino.fa2.ui.navigation.rootNavigator
import me.domino.fa2.ui.pages.submission.SubmissionRouteScreen
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
        val holderTag = "user-submission-holder:${username.lowercase()}:${route.routeKey}"
        val submissionListHolder =
            rootNavigator.rememberNavigatorScreenModel<SubmissionListHolder>(tag = holderTag) {
              SubmissionListHolder()
            }
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
                  submissionListHolder,
                  routeInitialFolderUrl,
                  initialSnapshot,
              )
            }
        val state by screenModel.state.collectAsState()
        LaunchedEffect(
            scrollKey,
            state.submissions,
            state.nextPageUrl,
            state.folderGroups,
        ) {
          updateSubmissionSnapshot(scrollKey, state)
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
                  initialFirstVisibleItemIndex = initialScrollPosition.firstVisibleItemIndex,
                  initialFirstVisibleItemScrollOffset =
                      initialScrollPosition.firstVisibleItemScrollOffset,
              )
          var deferredBodyScrollPosition by
              remember(scrollKey) {
                mutableStateOf(initialScrollPosition.deferredBodyScrollPosition)
              }

          UserSubmissionSectionScreen(
              route = route,
              state = state,
              onRetry = { screenModel.load(forceRefresh = true) },
              onRefresh = {
                refreshHeader?.invoke()
                screenModel.load(forceRefresh = true)
              },
              onOpenSubmission = { item ->
                screenModel.setCurrentSubmission(item.id)
                rootNavigator.push(
                    SubmissionRouteScreen(initialSid = item.id, holderTag = holderTag)
                )
              },
              onOpenFolder = { folderUrl ->
                updateFolderUrl(route, folderUrl)
                screenModel.openFolder(folderUrl)
              },
              onLastVisibleIndexChanged = screenModel::onLastVisibleIndexChanged,
              onRetryLoadMore = screenModel::retryLoadMore,
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
          )
        }
      }
    }
  }
}
