package me.domino.fa2.ui.screen.user

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

internal object UserSectionTopDefaults {
  val tabsHorizontalPaddingInList: Dp = 12.dp
  val tabsHorizontalPaddingInGrid: Dp = 0.dp
  val stickyTabsHorizontalPadding: Dp = 12.dp
}

internal data class UserScrollLayout(val hasHeader: Boolean, val bodyStartIndex: Int) {
  val tabsInlineIndex: Int
    get() = if (hasHeader) 1 else 0
}

internal sealed interface UserSharedTopScrollState {
  data class Inline(val firstVisibleItemIndex: Int = 0, val firstVisibleItemScrollOffset: Int = 0) :
    UserSharedTopScrollState

  data object Sticky : UserSharedTopScrollState
}

internal data class UserBodyScrollPosition(
  val firstVisibleItemIndex: Int = 0,
  val firstVisibleItemScrollOffset: Int = 0,
) {
  val isAtStart: Boolean
    get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
}

internal data class UserResolvedScrollPosition(
  val firstVisibleItemIndex: Int,
  val firstVisibleItemScrollOffset: Int,
  val deferredBodyScrollPosition: UserBodyScrollPosition? = null,
)

internal fun userJournalsScrollLayout(hasHeader: Boolean): UserScrollLayout =
  UserScrollLayout(hasHeader = hasHeader, bodyStartIndex = if (hasHeader) 2 else 1)

internal fun userSubmissionSectionScrollLayout(hasHeader: Boolean): UserScrollLayout =
  UserScrollLayout(hasHeader = hasHeader, bodyStartIndex = if (hasHeader) 2 else 1)

internal fun shouldStickUserSectionTabs(
  firstVisibleItemIndex: Int,
  firstVisibleItemScrollOffset: Int,
  hasHeader: Boolean,
): Boolean {
  val tabsInlineIndex = if (hasHeader) 1 else 0
  return firstVisibleItemIndex > tabsInlineIndex ||
    (firstVisibleItemIndex == tabsInlineIndex && firstVisibleItemScrollOffset > 0)
}

internal fun resolveUserSharedTopScrollState(
  firstVisibleItemIndex: Int,
  firstVisibleItemScrollOffset: Int,
  layout: UserScrollLayout,
): UserSharedTopScrollState =
  if (
    shouldStickUserSectionTabs(
      firstVisibleItemIndex = firstVisibleItemIndex,
      firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
      hasHeader = layout.hasHeader,
    )
  ) {
    UserSharedTopScrollState.Sticky
  } else {
    UserSharedTopScrollState.Inline(
      firstVisibleItemIndex = firstVisibleItemIndex,
      firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
    )
  }

internal fun resolveUserBodyScrollPosition(
  firstVisibleItemIndex: Int,
  firstVisibleItemScrollOffset: Int,
  layout: UserScrollLayout,
): UserBodyScrollPosition {
  val relativeIndex = max(firstVisibleItemIndex - layout.bodyStartIndex, 0)
  val relativeOffset =
    if (firstVisibleItemIndex < layout.bodyStartIndex) 0 else firstVisibleItemScrollOffset
  return UserBodyScrollPosition(
    firstVisibleItemIndex = relativeIndex,
    firstVisibleItemScrollOffset = relativeOffset,
  )
}

internal fun resolveInitialUserScrollPosition(
  sharedTopScrollState: UserSharedTopScrollState,
  bodyScrollPosition: UserBodyScrollPosition?,
  layout: UserScrollLayout,
): UserResolvedScrollPosition =
  when (sharedTopScrollState) {
    is UserSharedTopScrollState.Inline ->
      UserResolvedScrollPosition(
        firstVisibleItemIndex = sharedTopScrollState.firstVisibleItemIndex,
        firstVisibleItemScrollOffset = sharedTopScrollState.firstVisibleItemScrollOffset,
        deferredBodyScrollPosition = bodyScrollPosition?.takeUnless { it.isAtStart },
      )

    UserSharedTopScrollState.Sticky -> {
      // Keep sticky tabs snapped with a tiny top inset so we don't jump under the row.
      val offsetPixels = 3
      val targetBodyScrollPosition = bodyScrollPosition ?: UserBodyScrollPosition()
      if (targetBodyScrollPosition.isAtStart) {
        return UserResolvedScrollPosition(
          firstVisibleItemIndex = layout.tabsInlineIndex,
          firstVisibleItemScrollOffset = offsetPixels,
        )
      }
      UserResolvedScrollPosition(
        firstVisibleItemIndex =
          layout.bodyStartIndex + targetBodyScrollPosition.firstVisibleItemIndex,
        firstVisibleItemScrollOffset = targetBodyScrollPosition.firstVisibleItemScrollOffset,
      )
    }
  }

internal enum class UserSectionTabSelectionAction {
  SelectRoute,
  RefreshCurrentRoute,
  ScrollCurrentRouteToTop,
}

internal fun resolveUserSectionTabSelectionAction(
  targetRoute: UserChildRoute,
  currentRoute: UserChildRoute,
  isAtTop: Boolean,
): UserSectionTabSelectionAction =
  when {
    targetRoute != currentRoute -> UserSectionTabSelectionAction.SelectRoute
    isAtTop -> UserSectionTabSelectionAction.RefreshCurrentRoute
    else -> UserSectionTabSelectionAction.ScrollCurrentRouteToTop
  }

internal fun handleUserSectionTabSelection(
  targetRoute: UserChildRoute,
  currentRoute: UserChildRoute,
  isAtTop: Boolean,
  onSelectRoute: (UserChildRoute) -> Unit,
  onRefreshCurrentRoute: () -> Unit,
  onScrollCurrentRouteToTop: () -> Unit,
) {
  when (
    resolveUserSectionTabSelectionAction(
      targetRoute = targetRoute,
      currentRoute = currentRoute,
      isAtTop = isAtTop,
    )
  ) {
    UserSectionTabSelectionAction.SelectRoute -> onSelectRoute(targetRoute)
    UserSectionTabSelectionAction.RefreshCurrentRoute -> onRefreshCurrentRoute()
    UserSectionTabSelectionAction.ScrollCurrentRouteToTop -> onScrollCurrentRouteToTop()
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserChildRouteTabs(
  currentRoute: UserChildRoute,
  onSelectRoute: (UserChildRoute) -> Unit,
  horizontalPadding: Dp = UserSectionTopDefaults.tabsHorizontalPaddingInList,
  modifier: Modifier = Modifier,
) {
  Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
    SingleChoiceSegmentedButtonRow(
      modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 6.dp)
    ) {
      UserChildRoute.entries.forEachIndexed { index, route ->
        SegmentedButton(
          selected = route == currentRoute,
          onClick = { onSelectRoute(route) },
          shape =
            SegmentedButtonDefaults.itemShape(index = index, count = UserChildRoute.entries.size),
        ) {
          Text(route.title)
        }
      }
    }
  }
}
