package me.domino.fa2.ui.pages.user.route

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.no_content
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.domino.fa2.data.fa.watchlist.WatchlistRepository
import me.domino.fa2.ui.app.navigation.goBackHome
import me.domino.fa2.ui.app.scaffold.UserRouteTopBar
import me.domino.fa2.ui.pages.watchrecommendation.SimilarUsersRouteScreen
import me.domino.fa2.utils.FaUrls
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val userPagerAppendThreshold = 3

internal class UserPagerRouteScreen(
    private val initialUsername: String,
    private val contextId: String,
) : Screen {
  override val key: String = "user-pager:$contextId:${initialUsername.lowercase()}"

  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val contextScreenModel =
        navigator.rememberNavigatorScreenModel<UserPagerContextScreenModel>(
            tag = "user-pager-context:$contextId"
        ) {
          UserPagerContextScreenModel()
        }
    val watchlistRepository = koinInject<WatchlistRepository>()
    val state by contextScreenModel.state.collectAsState()
    val users = state.users
    val currentUsername =
        users.getOrNull(state.currentIndex)?.username
            ?: state.currentUsername.ifBlank { initialUsername }
    val safeInitialIndex = state.currentIndex.coerceIn(0, (users.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeInitialIndex, pageCount = { users.size })
    val scrollVersions = remember { mutableMapOf<String, MutableLongState>() }
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    fun requestWatchlistAppendIfPossible() {
      val source = state.source
      if (source is UserPagerSource.Watchlist) {
        contextScreenModel.requestAppend { nextPageUrl ->
          watchlistRepository.loadWatchlistPage(
              username = source.ownerUsername,
              category = source.category,
              nextPageUrl = nextPageUrl,
          )
        }
      }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(state.currentIndex, users.size) {
      if (users.isNotEmpty() && pagerState.currentPage != state.currentIndex) {
        pagerState.scrollToPage(state.currentIndex.coerceIn(0, users.lastIndex))
      }
    }

    LaunchedEffect(pagerState, users.size, state.source, state.nextPageUrl) {
      snapshotFlow { pagerState.currentPage }
          .distinctUntilChanged()
          .collect { page ->
            contextScreenModel.setCurrentIndex(page)
            val source = state.source
            if (
                source is UserPagerSource.Watchlist &&
                    users.isNotEmpty() &&
                    page >= users.lastIndex - userPagerAppendThreshold
            ) {
              requestWatchlistAppendIfPossible()
            }
          }
    }

    Column(
        modifier =
            Modifier.fillMaxSize().focusRequester(focusRequester).focusable().onPreviewKeyEvent {
                event ->
              if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
              when (event.key) {
                Key.DirectionLeft -> {
                  if (pagerState.currentPage > 0) {
                    coroutineScope.launch {
                      pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                    true
                  } else {
                    false
                  }
                }

                Key.DirectionRight -> {
                  when {
                    pagerState.currentPage < pagerState.pageCount - 1 -> {
                      coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                      }
                      true
                    }

                    state.hasMore -> {
                      requestWatchlistAppendIfPossible()
                      true
                    }

                    else -> false
                  }
                }

                else -> false
              }
            }
    ) {
      UserRouteTopBar(
          title = "~$currentUsername",
          onBack = { navigator.pop() },
          onGoHome = { navigator.goBackHome() },
          shareUrl = FaUrls.user(currentUsername),
          onExploreSimilarUsers = { navigator.push(SimilarUsersRouteScreen(currentUsername)) },
          onTitleClick = { scrollVersions[currentUsername.lowercase()]?.longValue++ },
      )

      if (users.isEmpty()) {
        Text(
            text = stringResource(Res.string.no_content),
            modifier = Modifier.padding(16.dp),
        )
        return@Column
      }

      Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
          val user = users[page]
          val userKey = user.username.lowercase()
          key(userKey) {
            val scrollToTopVersion = remember(userKey) { mutableLongStateOf(0L) }
            LaunchedEffect(userKey, scrollToTopVersion) {
              scrollVersions[userKey] = scrollToTopVersion
            }
            UserRouteBody(
                username = user.username,
                initialChildRoute = UserChildRoute.Gallery,
                initialFolderUrl = null,
                scrollToTopVersion = scrollToTopVersion,
            )
          }
        }
      }
    }
  }
}
