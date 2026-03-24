package me.domino.fa2.ui.pages.user

import kotlin.test.Test
import kotlin.test.assertNotEquals

class UserChildRouteScreenKeyTest {
  @Test
  fun keyDiffersByRoute() {
    val gallery = UserChildRouteScreen(username = "tiaamaito", route = UserChildRoute.Gallery)
    val favorites = UserChildRouteScreen(username = "tiaamaito", route = UserChildRoute.Favorites)

    assertNotEquals(gallery.key, favorites.key)
  }

  @Test
  fun keyDiffersByUser() {
    val first = UserChildRouteScreen(username = "alpha", route = UserChildRoute.Gallery)
    val second = UserChildRouteScreen(username = "beta", route = UserChildRoute.Gallery)

    assertNotEquals(first.key, second.key)
  }

  @Test
  fun userRouteKeyDiffersByUser() {
    val first = UserRouteScreen(username = "alpha", initialChildRoute = UserChildRoute.Gallery)
    val second = UserRouteScreen(username = "beta", initialChildRoute = UserChildRoute.Gallery)

    assertNotEquals(first.key, second.key)
  }

  @Test
  fun userRouteKeyDiffersByInitialFolder() {
    val first =
      UserRouteScreen(
        username = "alpha",
        initialChildRoute = UserChildRoute.Gallery,
        initialFolderUrl = "https://www.furaffinity.net/gallery/alpha/folder/1/demo",
      )
    val second =
      UserRouteScreen(
        username = "alpha",
        initialChildRoute = UserChildRoute.Gallery,
        initialFolderUrl = "https://www.furaffinity.net/gallery/alpha/folder/2/demo",
      )

    assertNotEquals(first.key, second.key)
  }
}
