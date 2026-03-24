package me.domino.fa2.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 顶层导航目标。
 */
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

/**
 * 导航壳布局形态。
 */
enum class NavigationShellType {
    /** 窄屏底部栏。 */
    BottomBar,

    /** 宽屏侧边 Rail。 */
    SideRail,
}

/** 触发侧边 Rail 的最小宽度。 */
private val navigationRailMinWidth = 960.dp

/**
 * 根据当前可用宽度选择导航壳形态。
 */
fun navigationShellTypeFor(
    /** 当前布局宽度。 */
    width: Dp,
): NavigationShellType =
    if (width >= navigationRailMinWidth) NavigationShellType.SideRail else NavigationShellType.BottomBar

/**
 * 应用主壳子，视觉与老仓库对齐。
 */
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

        if (useRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    TOP_LEVEL_DESTINATIONS.forEach { destination ->
                        val selected = currentTopLevelDestination == destination.destination
                        NavigationRailItem(
                            selected = selected,
                            onClick = { onTopLevelDestinationClick(destination.destination, selected) },
                            icon = { TopLevelDestinationIcon(destination) },
                            label = { TopLevelDestinationLabel(destination) },
                        )
                    }
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
                Box(modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        TOP_LEVEL_DESTINATIONS.forEach { destination ->
                            val selected = currentTopLevelDestination == destination.destination
                            NavigationBarItem(
                                selected = selected,
                                onClick = { onTopLevelDestinationClick(destination.destination, selected) },
                                icon = { TopLevelDestinationIcon(destination) },
                                label = { TopLevelDestinationLabel(destination) },
                            )
                        }
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * 顶层导航条目定义。
 */
private data class TopLevelDestinationItem(
    /** 目标页面。 */
    val destination: TopLevelDestination,
    /** 展示文案。 */
    val label: String,
    /** 图标。 */
    val icon: ImageVector,
)

/** 顶层导航条目集合。 */
private val TOP_LEVEL_DESTINATIONS: List<TopLevelDestinationItem> = listOf(
    TopLevelDestinationItem(
        destination = TopLevelDestination.FEED,
        label = "动态",
        icon = Icons.Filled.Home,
    ),
    TopLevelDestinationItem(
        destination = TopLevelDestination.BROWSE,
        label = "Browse",
        icon = Icons.Filled.Explore,
    ),
    TopLevelDestinationItem(
        destination = TopLevelDestination.SEARCH,
        label = "Search",
        icon = Icons.Filled.Search,
    ),
    TopLevelDestinationItem(
        destination = TopLevelDestination.MORE,
        label = "更多",
        icon = Icons.Filled.Menu,
    ),
)

/**
 * 导航图标。
 */
@Composable
private fun TopLevelDestinationIcon(
    /** 导航条目。 */
    destination: TopLevelDestinationItem,
) {
    Icon(
        imageVector = destination.icon,
        contentDescription = destination.label,
    )
}

/**
 * 导航文案。
 */
@Composable
private fun TopLevelDestinationLabel(
    /** 导航条目。 */
    destination: TopLevelDestinationItem,
) {
    Text(destination.label)
}
