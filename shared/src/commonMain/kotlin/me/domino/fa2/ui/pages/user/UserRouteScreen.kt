package me.domino.fa2.ui.pages.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.ui.layouts.UserRouteTopBar
import me.domino.fa2.ui.navigation.goBackHome
import me.domino.fa2.util.FaUrls
import org.koin.core.parameter.parametersOf

/** User 父路由页面。 */
class UserRouteScreen(
    /** 目标用户名。 */
    private val username: String,
    /** 初始子路由。 */
    private val initialChildRoute: UserChildRoute = UserChildRoute.Gallery,
    /** 初始文件夹 URL（可选）。 */
    private val initialFolderUrl: String? = null,
) : Screen {
  override val key: String =
      "user-route:${username.lowercase()}:${initialChildRoute.routeKey}:${initialFolderUrl.orEmpty()}"

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val parentNavigator = LocalNavigator.currentOrThrow
    val userScreenModel =
        koinScreenModel<UserScreenModel> {
          parametersOf(username, initialChildRoute, initialFolderUrl)
        }
    val userState by userScreenModel.state.collectAsState()
    var onCurrentRouteScrollToTop by remember { mutableStateOf<(() -> Unit)?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
      UserRouteTopBar(
          title = userState.header?.displayName?.ifBlank { username } ?: username,
          onBack = { parentNavigator.pop() },
          onGoHome = { parentNavigator.goBackHome() },
          shareUrl = FaUrls.user(username),
          onTitleClick = { onCurrentRouteScrollToTop?.invoke() },
      )

      Navigator(
          screen =
              UserChildRouteScreen(
                  username = username,
                  route = initialChildRoute,
                  initialFolderUrl = initialFolderUrl,
              )
      ) {
        CompositionLocalProvider(
            LocalUserHeaderContent provides
                {
                  UserHeaderCard(
                      state = userState,
                      onRetry = { userScreenModel.load() },
                      onToggleProfileExpanded = userScreenModel::toggleProfileExpanded,
                      onToggleWatch = userScreenModel::toggleWatch,
                      onOpenWatchedBy = {
                        val initialUrl =
                            userState.header?.watchedByListUrl?.trim()?.takeIf { value ->
                              value.isNotBlank()
                            } ?: FaUrls.watchlistTo(username)
                        parentNavigator.push(
                            UserWatchlistRouteScreen(
                                username = username,
                                category = WatchlistCategory.WatchedBy,
                                initialUrl = initialUrl,
                            )
                        )
                      },
                      onOpenWatching = {
                        val initialUrl =
                            userState.header?.watchingListUrl?.trim()?.takeIf { value ->
                              value.isNotBlank()
                            } ?: FaUrls.watchlistBy(username)
                        parentNavigator.push(
                            UserWatchlistRouteScreen(
                                username = username,
                                category = WatchlistCategory.Watching,
                                initialUrl = initialUrl,
                            )
                        )
                      },
                  )
                },
            LocalUserHeaderRefreshAction provides { userScreenModel.load(forceRefresh = true) },
            LocalUserSubmissionFolderResolver provides
                { route ->
                  userScreenModel.getSubmissionRouteFolderUrl(route)
                },
            LocalUserSubmissionFolderUpdater provides
                { route, folderUrl ->
                  userScreenModel.setSubmissionRouteFolderUrl(route, folderUrl)
                },
            LocalUserSharedTopScrollStateResolver provides
                {
                  userScreenModel.getSharedTopScrollState()
                },
            LocalUserSharedTopScrollStateUpdater provides
                { position ->
                  userScreenModel.setSharedTopScrollState(position)
                },
            LocalUserBodyScrollPositionResolver provides
                { scrollKey ->
                  userScreenModel.getBodyScrollPosition(scrollKey)
                },
            LocalUserBodyScrollPositionUpdater provides
                { scrollKey, position ->
                  userScreenModel.setBodyScrollPosition(scrollKey, position)
                },
            LocalUserSubmissionSnapshotResolver provides
                { cacheKey ->
                  userScreenModel.getSubmissionSectionSnapshot(cacheKey)
                },
            LocalUserSubmissionSnapshotUpdater provides
                { cacheKey, snapshot ->
                  userScreenModel.setSubmissionSectionSnapshot(cacheKey, snapshot)
                },
            LocalUserCurrentRouteScrollToTopActionUpdater provides
                { action ->
                  onCurrentRouteScrollToTop = action
                },
        ) {
          Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            CurrentScreen()
          }
        }
      }
    }
  }
}
