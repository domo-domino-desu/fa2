package me.domino.fa2.ui.screen.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import me.domino.fa2.data.repository.ActivityHistoryRepository
import me.domino.fa2.ui.component.topbar.SearchRouteTopBar
import me.domino.fa2.ui.navigation.SubmissionListHolder
import me.domino.fa2.ui.navigation.goBackHome
import me.domino.fa2.ui.screen.feed.SubmissionRouteScreen
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * 独立搜索路由页面（用于从投稿关键词跳转）。
 */
class SearchRouteScreen(
    private val initialQuery: String,
) : Screen {
    private val holderTag: String = "submission-list-holder:search-route:${initialQuery.hashCode()}"

    override val key: String = "search-route:${initialQuery.trim().lowercase()}"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val historyRepository = koinInject<ActivityHistoryRepository>()
        val submissionListHolder = navigator.rememberNavigatorScreenModel<SubmissionListHolder>(
            tag = holderTag,
        ) {
            SubmissionListHolder()
        }
        val screenModel = koinScreenModel<SearchScreenModel> {
            parametersOf(submissionListHolder)
        }
        val state by screenModel.state.collectAsState()
        val waterfallState = rememberLazyStaggeredGridState()
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(initialQuery) {
            val normalized = initialQuery.trim()
            if (normalized.isNotBlank()) {
                screenModel.updateQuery(normalized)
                screenModel.applySearch()
                historyRepository.recordSearchQuery(normalized)
            }
        }

        Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
            SearchRouteTopBar(
                onBack = { navigator.pop() },
                onGoHome = { navigator.goBackHome() },
            )

            SearchScreen(
                state = state,
                actions = SearchScreenActions(
                    onOpenOverlay = screenModel::openOverlay,
                    onCloseOverlay = screenModel::closeOverlay,
                    onUpdateQuery = screenModel::updateQuery,
                    onToggleGender = screenModel::toggleGender,
                    onUpdateCategory = screenModel::updateCategory,
                    onUpdateType = screenModel::updateType,
                    onUpdateSpecies = screenModel::updateSpecies,
                    onUpdateOrderBy = screenModel::updateOrderBy,
                    onUpdateOrderDirection = screenModel::updateOrderDirection,
                    onUpdateRange = screenModel::updateRange,
                    onUpdateRangeFrom = screenModel::updateRangeFrom,
                    onUpdateRangeTo = screenModel::updateRangeTo,
                    onSetRatingGeneral = screenModel::setRatingGeneral,
                    onSetRatingMature = screenModel::setRatingMature,
                    onSetRatingAdult = screenModel::setRatingAdult,
                    onSetTypeArt = screenModel::setTypeArt,
                    onSetTypeMusic = screenModel::setTypeMusic,
                    onSetTypeFlash = screenModel::setTypeFlash,
                    onSetTypeStory = screenModel::setTypeStory,
                    onSetTypePhoto = screenModel::setTypePhoto,
                    onSetTypePoetry = screenModel::setTypePoetry,
                    onApplySearch = {
                        val query = state.draft.query.trim()
                        screenModel.applySearch()
                        if (query.isNotBlank()) {
                            coroutineScope.launch {
                                historyRepository.recordSearchQuery(query)
                            }
                        }
                    },
                    onRefresh = screenModel::refresh,
                    onRetry = screenModel::refresh,
                    onOpenSubmission = { item ->
                        screenModel.setCurrentSubmission(item.id)
                        navigator.push(
                            SubmissionRouteScreen(
                                initialSid = item.id,
                                holderTag = holderTag,
                            ),
                        )
                    },
                    onLastVisibleIndexChanged = screenModel::onLastVisibleIndexChanged,
                    onRetryLoadMore = screenModel::retryLoadMore,
                ),
                waterfallState = waterfallState,
            )
        }
    }
}
