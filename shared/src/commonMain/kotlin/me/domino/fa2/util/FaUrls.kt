package me.domino.fa2.util

import io.ktor.http.encodeURLParameter

/** FA URL 构建工具。 */
object FaUrls {
  /** 首页地址。 */
  const val home: String = "https://www.furaffinity.net/"

  /** submissions 根路径。 */
  private const val submissionsRoot: String = "https://www.furaffinity.net/msg/submissions/"

  /** submission 根路径。 */
  private const val submissionRoot: String = "https://www.furaffinity.net/view/"

  /** 头像根路径。 */
  private const val avatarRoot: String = "https://a.furaffinity.net/"

  /** submissions 固定页大小。 */
  private const val pageSize: Int = 72

  /** 用户主页根路径。 */
  private const val userRoot: String = "https://www.furaffinity.net/user/"

  /** 关注列表根路径。 */
  private const val watchlistRoot: String = "https://www.furaffinity.net/watchlist/"

  /** 画廊根路径。 */
  private const val galleryRoot: String = "https://www.furaffinity.net/gallery/"

  /** Browse 根路径。 */
  private const val browseRoot: String = "https://www.furaffinity.net/browse/"

  /** Search 根路径。 */
  private const val searchRoot: String = "https://www.furaffinity.net/search/"

  /** 收藏根路径。 */
  private const val favoritesRoot: String = "https://www.furaffinity.net/favorites/"

  /** 日志列表根路径。 */
  private const val journalsRoot: String = "https://www.furaffinity.net/journals/"

  /** 单篇日志根路径。 */
  private const val journalRoot: String = "https://www.furaffinity.net/journal/"

  /**
   * 构建 submissions 页地址。
   *
   * @param fromSid 分页游标。
   */
  fun submissions(fromSid: Int? = null): String =
      if (fromSid == null) {
        "${submissionsRoot}new@$pageSize/"
      } else {
        "${submissionsRoot}new~$fromSid@$pageSize/"
      }

  /**
   * 构建 submission 详情页地址。
   *
   * @param sid 投稿 ID。
   */
  fun submission(sid: Int): String = "$submissionRoot$sid/"

  /**
   * 构建用户头像地址。
   *
   * @param usernameLower 用户名（建议小写）。
   * @param avatarMtime 头像时间戳目录。
   */
  fun avatar(usernameLower: String, avatarMtime: String): String {
    val normalizedUser = normalizeUsername(usernameLower).lowercase()
    val normalizedMtime = avatarMtime.trim().trim('/')
    if (normalizedUser.isBlank() || normalizedMtime.isBlank()) return ""
    return "$avatarRoot$normalizedMtime/$normalizedUser.gif"
  }

  /** 构建用户主页地址。 */
  fun user(username: String): String = "$userRoot${normalizeUsername(username)}/"

  /** 构建“关注该用户的人”列表地址。 */
  fun watchlistTo(username: String, page: Int? = null): String {
    val base = "$watchlistRoot" + "to/${normalizeUsername(username)}/"
    return when {
      page == null || page <= 1 -> base
      else -> "$base?page=$page"
    }
  }

  /** 构建“该用户已关注的人”列表地址。 */
  fun watchlistBy(username: String, page: Int? = null): String {
    val base = "$watchlistRoot" + "by/${normalizeUsername(username)}/"
    return when {
      page == null || page <= 1 -> base
      else -> "$base?page=$page"
    }
  }

  /** 构建用户 Gallery 地址。 */
  fun gallery(username: String): String = "$galleryRoot${normalizeUsername(username)}/"

  /** 构建用户 Favorites 地址。 */
  fun favorites(username: String): String = "$favoritesRoot${normalizeUsername(username)}/"

  /** 构建用户 Journals 地址。 */
  fun journals(username: String): String = "$journalsRoot${normalizeUsername(username)}/"

  /** 构建单篇 Journal 地址。 */
  fun journal(journalId: Int): String = "$journalRoot$journalId/"

  /** 构建 Browse 地址（显式参数形态）。 */
  fun browse(
      cat: Int = 1,
      atype: Int = 1,
      species: Int = 1,
      gender: String = "",
      perpage: Int = pageSize,
      page: Int = 1,
      ratingGeneral: Boolean = true,
      ratingMature: Boolean = true,
      ratingAdult: Boolean = true,
  ): String {
    val query =
        buildList {
              add("cat=$cat")
              add("atype=$atype")
              add("species=$species")
              add("gender=${gender.encodeURLParameter()}")
              add("perpage=$perpage")
              add("page=$page")
              if (ratingGeneral) add("rating_general=1")
              if (ratingMature) add("rating_mature=1")
              if (ratingAdult) add("rating_adult=1")
              add("go=Apply")
            }
            .joinToString("&")
    return "$browseRoot?$query"
  }

  /** Search URL 参数模型。 */
  data class SearchParams(
      val q: String,
      val page: Int = 1,
      val perpage: Int = pageSize,
      val orderBy: String = "relevancy",
      val orderDirection: String = "desc",
      val range: String = "all",
      val category: Int = 1,
      val arttype: Int = 1,
      val species: Int = 1,
      val ratingGeneral: Boolean = true,
      val ratingMature: Boolean = true,
      val ratingAdult: Boolean = true,
      val typeArt: Boolean = true,
      val typeMusic: Boolean = true,
      val typeFlash: Boolean = true,
      val typeStory: Boolean = true,
      val typePhoto: Boolean = true,
      val typePoetry: Boolean = true,
      val rangeFrom: String = "",
      val rangeTo: String = "",
      val mode: String = "extended",
  )

  /** 构建 Search 地址（对齐站点默认省略规则）。 */
  fun search(params: SearchParams): String {
    val query =
        buildList {
              add("q=${params.q.encodeURLParameter()}")
              add("page=${params.page}")
              add("perpage=${params.perpage}")
              add("order-by=${params.orderBy.encodeURLParameter()}")
              add("order-direction=${params.orderDirection.encodeURLParameter()}")
              add("range=${params.range.encodeURLParameter()}")

              if (params.category != 1) add("category=${params.category}")
              if (params.arttype != 1) add("arttype=${params.arttype}")
              if (params.species != 1) add("species=${params.species}")
              if (params.mode != "extended") add("mode=${params.mode.encodeURLParameter()}")

              if (params.range == "manual") {
                if (params.rangeFrom.isNotBlank())
                    add("range_from=${params.rangeFrom.encodeURLParameter()}")
                if (params.rangeTo.isNotBlank())
                    add("range_to=${params.rangeTo.encodeURLParameter()}")
              }

              if (params.ratingGeneral) add("rating-general=1")
              if (params.ratingMature) add("rating-mature=1")
              if (params.ratingAdult) add("rating-adult=1")

              if (params.typeArt) add("type-art=1")
              if (params.typeMusic) add("type-music=1")
              if (params.typeFlash) add("type-flash=1")
              if (params.typeStory) add("type-story=1")
              if (params.typePhoto) add("type-photo=1")
              if (params.typePoetry) add("type-poetry=1")
            }
            .joinToString("&")
    return "$searchRoot?$query"
  }

  /** 规范化用户名段。 */
  private fun normalizeUsername(username: String): String = username.trim().trim('/')
}
