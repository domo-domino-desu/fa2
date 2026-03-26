package me.domino.fa2.ui.pages.user.route

/** User 子路由。 */
enum class UserChildRoute(
    /** 路由键。 */
    val routeKey: String,
    /** 展示标题。 */
    val title: String,
) {
  /** Gallery。 */
  Gallery(routeKey = "gallery", title = "Gallery"),

  /** Favorites。 */
  Favorites(routeKey = "favorites", title = "Favorites"),

  /** Journals。 */
  Journals(routeKey = "journals", title = "Journals");

  /** 是否为投稿瀑布流子页。 */
  val isSubmissionSection: Boolean
    get() = this == Gallery || this == Favorites

  companion object {
    /** 从字符串恢复子路由。 */
    fun fromRouteKey(routeKey: String?): UserChildRoute =
        entries.firstOrNull { route -> route.routeKey.equals(routeKey?.trim(), ignoreCase = true) }
            ?: Gallery
  }
}
