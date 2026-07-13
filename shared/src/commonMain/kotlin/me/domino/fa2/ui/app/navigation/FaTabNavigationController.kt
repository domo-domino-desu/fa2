package me.domino.fa2.ui.app.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.navigator.Navigator

internal class FaTabNavigationController {
  var selectedTab by mutableStateOf(TopLevelDestination.FEED)
    private set

  private val navigators = mutableMapOf<TopLevelDestination, Navigator>()
  private val pending = mutableMapOf<TopLevelDestination, MutableList<AppScreen>>()
  private val depths = mutableStateMapOf<TopLevelDestination, Int>()

  val currentDepth: Int
    get() = depths[selectedTab] ?: 1

  fun select(tab: TopLevelDestination) {
    selectedTab = tab
  }

  fun register(tab: TopLevelDestination, navigator: Navigator) {
    navigators[tab] = navigator
    depths[tab] = navigator.size
    pending.remove(tab)?.forEach(navigator::push)
  }

  fun updateDepth(tab: TopLevelDestination, depth: Int) {
    depths[tab] = depth
  }

  fun pushCurrent(screen: AppScreen) {
    navigators[selectedTab]?.push(screen)
        ?: pending.getOrPut(selectedTab, ::mutableListOf).add(screen)
  }

  fun openExternal(tab: TopLevelDestination, screen: AppScreen) {
    selectedTab = tab
    navigators[tab]?.push(screen) ?: pending.getOrPut(tab, ::mutableListOf).add(screen)
  }
}

internal val LocalFaTabNavigationController =
    compositionLocalOf<FaTabNavigationController> { error("FaTabNavigationController missing") }
