package me.domino.fa2.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.*
import me.domino.fa2.ui.icons.FaMaterialSymbols
import org.jetbrains.compose.resources.stringResource

/** 顶层导航目标。 */
enum class TopLevelDestination {
  /** Feed 页面。 */
  FEED,

  /** Browse 页面。 */
  BROWSE,

  /** Search 页面。 */
  SEARCH,

  /** More 页面。 */
  MORE,
}

/** 导航壳布局形态。 */
enum class NavigationShellType {
  /** 窄屏底部栏。 */
  BottomBar,

  /** 宽屏侧边 Rail。 */
  SideRail,
}

/** 触发侧边 Rail 的最小宽度。 */
private val navigationRailMinWidth = 960.dp

/** 根据当前可用宽度选择导航壳形态。 */
fun navigationShellTypeFor(
    /** 当前布局宽度。 */
    width: Dp
): NavigationShellType =
    if (width >= navigationRailMinWidth) NavigationShellType.SideRail
    else NavigationShellType.BottomBar

/** 应用主壳子，视觉与老仓库对齐。 */
@Composable
fun AppScaffold(
    /** 当前选中目标。 */
    currentTopLevelDestination: TopLevelDestination,
    /** 顶层导航点击回调。 */
    onTopLevelDestinationClick: (destination: TopLevelDestination, reselected: Boolean) -> Unit,
    /** 当前页内容。 */
    content: @Composable () -> Unit,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = navigationShellTypeFor(maxWidth) == NavigationShellType.SideRail
    val destinations = topLevelDestinations()

    if (useRail) {
      Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
          destinations.forEach { destination ->
            val selected = currentTopLevelDestination == destination.destination
            NavigationRailItem(
                selected = selected,
                onClick = { onTopLevelDestinationClick(destination.destination, selected) },
                modifier = Modifier.testTag(destination.testTag),
                icon = { TopLevelDestinationIcon(destination, selected) },
                label = { TopLevelDestinationLabel(destination) },
            )
          }
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
        Box(modifier = Modifier.fillMaxSize()) { content() }
      }
    } else {
      Scaffold(
          containerColor = MaterialTheme.colorScheme.surface,
          bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
              destinations.forEach { destination ->
                val selected = currentTopLevelDestination == destination.destination
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTopLevelDestinationClick(destination.destination, selected) },
                    modifier = Modifier.testTag(destination.testTag),
                    icon = { TopLevelDestinationIcon(destination, selected) },
                    label = { TopLevelDestinationLabel(destination) },
                )
              }
            }
          },
      ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) { content() }
      }
    }
  }
}

/** 顶层导航条目定义。 */
private data class TopLevelDestinationItem(
    /** 目标页面。 */
    val destination: TopLevelDestination,
    /** 展示文案。 */
    val label: String,
    /** UI 测试标签。 */
    val testTag: String,
    /** 选中图标。 */
    val selectedIcon: ImageVector,
    /** 未选中图标。 */
    val unselectedIcon: ImageVector,
)

/** 顶层导航条目集合。 */
@Composable
private fun topLevelDestinations(): List<TopLevelDestinationItem> =
    listOf(
        TopLevelDestinationItem(
            destination = TopLevelDestination.FEED,
            label = stringResource(Res.string.feed),
            testTag = "nav-feed",
            selectedIcon = FaMaterialSymbols.Filled.Home,
            unselectedIcon = FaMaterialSymbols.Outlined.Home,
        ),
        TopLevelDestinationItem(
            destination = TopLevelDestination.BROWSE,
            label = stringResource(Res.string.browse),
            testTag = "nav-browse",
            selectedIcon = FaMaterialSymbols.Filled.Explore,
            unselectedIcon = FaMaterialSymbols.Outlined.Explore,
        ),
        TopLevelDestinationItem(
            destination = TopLevelDestination.SEARCH,
            label = stringResource(Res.string.search),
            testTag = "nav-search",
            selectedIcon = FaMaterialSymbols.Filled.Search,
            unselectedIcon = FaMaterialSymbols.Outlined.Search,
        ),
        TopLevelDestinationItem(
            destination = TopLevelDestination.MORE,
            label = stringResource(Res.string.more),
            testTag = "nav-more",
            selectedIcon = FaMaterialSymbols.Filled.Menu,
            unselectedIcon = FaMaterialSymbols.Outlined.Menu,
        ),
    )

/** 导航图标。 */
@Composable
private fun TopLevelDestinationIcon(
    /** 导航条目。 */
    destination: TopLevelDestinationItem,
    selected: Boolean,
) {
  Icon(
      imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
      contentDescription = destination.label,
  )
}

/** 导航文案。 */
@Composable
private fun TopLevelDestinationLabel(
    /** 导航条目。 */
    destination: TopLevelDestinationItem
) {
  Text(destination.label)
}
