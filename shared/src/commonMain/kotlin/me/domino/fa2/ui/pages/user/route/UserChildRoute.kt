package me.domino.fa2.ui.pages.user.route

/** User 子路由。 */
enum class UserChildRoute(
    /** 路由键。 */
    val routeKey: String,
) {
  /** Gallery。 */
  Gallery(routeKey = "gallery"),

  /** Favorites。 */
  Favorites(routeKey = "favorites"),

  /** Journals。 */
  Journals(routeKey = "journals");

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
