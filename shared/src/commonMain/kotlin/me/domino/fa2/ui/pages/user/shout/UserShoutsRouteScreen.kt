package me.domino.fa2.ui.pages.user.shout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.load_failed
import fa2.shared.generated.resources.no_displayable_shouts
import fa2.shared.generated.resources.shouts
import fa2.shared.generated.resources.shouts_count
import kotlinx.coroutines.launch
import me.domino.fa2.ui.components.SkeletonBlock
import me.domino.fa2.ui.components.StatusSurface
import me.domino.fa2.ui.components.StatusSurfaceVariant
import me.domino.fa2.ui.components.submission.SubmissionCommentsCard
import me.domino.fa2.ui.layouts.UserRouteTopBar
import me.domino.fa2.ui.navigation.goBackHome
import me.domino.fa2.ui.pages.user.route.UserChildRoute
import me.domino.fa2.ui.pages.user.route.UserRouteScreen
import me.domino.fa2.util.FaUrls
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf

class UserShoutsRouteScreen(private val username: String) : Screen {
  override val key: String = "user-shouts:${username.lowercase()}"

  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = koinScreenModel<UserShoutsScreenModel> { parametersOf(username) }
    val state by screenModel.state.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
      UserRouteTopBar(
          title = stringResource(Res.string.shouts),
          onBack = { navigator.pop() },
          onGoHome = { navigator.goBackHome() },
          shareUrl = FaUrls.user(username),
          onTitleClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
      )

      when (val snapshot = state) {
        UserShoutsUiState.Loading -> UserShoutsSkeleton()
        is UserShoutsUiState.Error ->
            UserShoutsError(message = snapshot.message, onRetry = { screenModel.load(true) })
        is UserShoutsUiState.Success -> {
          LazyColumn(
              state = listState,
              modifier = Modifier.fillMaxSize(),
              verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            item {
              SubmissionCommentsCard(
                  commentCount = snapshot.user.shoutCount,
                  comments = snapshot.user.shouts,
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
                  title =
                      stringResource(Res.string.shouts_count, snapshot.user.shoutCount.toString()),
                  emptyText = stringResource(Res.string.no_displayable_shouts),
                  modifier = Modifier.padding(top = 10.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun UserShoutsSkeleton() {
  Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    SkeletonBlock(modifier = Modifier.fillMaxWidth().height(220.dp))
  }
}

@Composable
private fun UserShoutsError(message: String, onRetry: () -> Unit) {
  StatusSurface(
      title = stringResource(Res.string.load_failed),
      body = message,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      onAction = onRetry,
      variant = StatusSurfaceVariant.Section,
  )
}
