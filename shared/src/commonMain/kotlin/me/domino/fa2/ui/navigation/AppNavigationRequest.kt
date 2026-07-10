package me.domino.fa2.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf

sealed interface AppNavigationRequest {
  data class UserGallery(val username: String) : AppNavigationRequest

  data class Search(val query: String) : AppNavigationRequest

  data class BrowseFilter(val category: Int, val type: Int, val species: Int) : AppNavigationRequest
}

val LocalAppNavigation = staticCompositionLocalOf<(AppNavigationRequest) -> Unit> { {} }
