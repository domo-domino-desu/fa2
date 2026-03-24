package me.domino.fa2.ui.screen.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import me.domino.fa2.ui.component.topbar.BrowseRouteTopBar
import me.domino.fa2.ui.navigation.SubmissionListHolder
import me.domino.fa2.ui.navigation.goBackHome
import me.domino.fa2.ui.screen.feed.SubmissionRouteScreen
import org.koin.core.parameter.parametersOf

/** 独立 Browse 路由页面（用于从投稿详情跳转）。 */
class BrowseRouteScreen(private val initialFilter: BrowseFilterState) : Screen {
  private val holderTag: String = "submission-list-holder:browse-route:${initialFilter.hashCode()}"

  override val key: String = "browse-route:${initialFilter.hashCode()}"

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val submissionListHolder =
      navigator.rememberNavigatorScreenModel<SubmissionListHolder>(tag = holderTag) {
        SubmissionListHolder()
      }
    val screenModel = koinScreenModel<BrowseScreenModel> { parametersOf(submissionListHolder) }
    val state by screenModel.state.collectAsState()
    val waterfallState = rememberLazyStaggeredGridState()

    LaunchedEffect(initialFilter) { screenModel.applyFilter(initialFilter) }

    Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
      BrowseRouteTopBar(onBack = { navigator.pop() }, onGoHome = { navigator.goBackHome() })

      BrowseScreen(
        state = state,
        onUpdateCategory = screenModel::updateCategory,
        onUpdateType = screenModel::updateType,
        onUpdateSpecies = screenModel::updateSpecies,
        onUpdateGender = screenModel::updateGender,
        onSetRatingGeneral = screenModel::setRatingGeneral,
        onSetRatingMature = screenModel::setRatingMature,
        onSetRatingAdult = screenModel::setRatingAdult,
        onApplyFilter = screenModel::applyFilter,
        onRefresh = screenModel::refresh,
        onRetry = screenModel::refresh,
        onOpenSubmission = { item ->
          screenModel.setCurrentSubmission(item.id)
          navigator.push(SubmissionRouteScreen(initialSid = item.id, holderTag = holderTag))
        },
        onLastVisibleIndexChanged = screenModel::onLastVisibleIndexChanged,
        onRetryLoadMore = screenModel::retryLoadMore,
        waterfallState = waterfallState,
      )
    }
  }
}
