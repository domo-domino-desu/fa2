package me.domino.fa2.ui.screen.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import me.domino.fa2.ui.navigation.SubmissionListHolder
import org.koin.core.parameter.parametersOf

/**
 * Feed 路由页面。
 */
class FeedRouteScreen : Screen {
    /**
     * 路由页面内容。
     */
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val submissionListHolder = navigator.rememberNavigatorScreenModel<SubmissionListHolder>(
            tag = "submission-list-holder",
        ) {
            SubmissionListHolder()
        }
        val screenModel = koinScreenModel<FeedScreenModel> {
            parametersOf(submissionListHolder)
        }
        val state by screenModel.state.collectAsState()
        val waterfallState = rememberLazyStaggeredGridState()

        LaunchedEffect(Unit) {
            screenModel.load()
        }

        FeedScreen(
            state = state,
            onRetry = { screenModel.load(forceRefresh = true) },
            onRefresh = screenModel::refresh,
            onOpenSubmission = { item ->
                screenModel.setCurrentSubmission(item.id)
                navigator.push(SubmissionRouteScreen(initialSid = item.id))
            },
            onLastVisibleIndexChanged = screenModel::onLastVisibleIndexChanged,
            onRetryLoadMore = screenModel::retryLoadMore,
            waterfallState = waterfallState,
        )
    }
}
