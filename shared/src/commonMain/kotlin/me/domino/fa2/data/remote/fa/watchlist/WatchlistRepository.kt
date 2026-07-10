package me.domino.fa2.data.remote.fa.watchlist

import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistPage
import me.domino.fa2.data.model.WatchlistUser
import me.domino.fa2.data.model.summarizeResourceResult
import me.domino.fa2.utils.concurrency.SequentialRequestThrottle
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl

/** watchlist 仓储。 */
class WatchlistRepository(private val store: WatchlistPageCache) :
    WatchRecommendationWatchlistSource {
  private val log = FaLog.withTag("WatchlistRepository")

  private companion object {
    const val loadAllWatchlistUsersThrottleMs: Long = 600L
  }

  /** 加载 watchlist 分页。 */
  override suspend fun loadWatchlistPage(
      username: String,
      category: WatchlistCategory,
      nextPageUrl: String?,
  ): ResourceResult<WatchlistPage> {
    log.d {
      "加载Watchlist -> user=$username,category=$category,cursor=${nextPageUrl?.let(::summarizeUrl) ?: "first"}"
    }
    val state =
        store.loadPageOnce(username = username, category = category, nextPageUrl = nextPageUrl)
    log.d { "加载Watchlist -> ${summarizeResourceResult(state)}" }
    return state
  }

  /** 强制刷新 watchlist 首页。 */
  suspend fun refreshWatchlistFirstPage(
      username: String,
      category: WatchlistCategory,
  ): ResourceResult<WatchlistPage> {
    log.i { "刷新Watchlist -> user=$username,category=$category" }
    store.invalidateUserCategory(username = username, category = category)
    val state = loadWatchlistPage(username = username, category = category, nextPageUrl = null)
    log.i { "刷新Watchlist -> ${summarizeResourceResult(state)}" }
    return state
  }

  /** 拉取指定 watchlist 的全部用户。 */
  suspend fun loadAllWatchlistUsers(
      username: String,
      category: WatchlistCategory,
      useFreshFirstPage: Boolean = false,
  ): ResourceResult<List<WatchlistUser>> {
    log.i {
      "加载全部Watchlist -> 开始(user=$username,category=$category,useFreshFirstPage=$useFreshFirstPage)"
    }
    val throttle = SequentialRequestThrottle(loadAllWatchlistUsersThrottleMs)
    val uniqueUsers = linkedMapOf<String, WatchlistUser>()
    var nextPageUrl: String? = null
    var pageCount = 0

    while (true) {
      throttle.awaitReady()
      val state =
          if (pageCount == 0 && useFreshFirstPage) {
            refreshWatchlistFirstPage(username = username, category = category)
          } else {
            loadWatchlistPage(username = username, category = category, nextPageUrl = nextPageUrl)
          }
      when (state) {
        is ResourceResult.Success -> {
          pageCount += 1
          state.data.users.forEach { user ->
            uniqueUsers.putIfAbsent(user.username.lowercase(), user)
          }
          nextPageUrl = state.data.nextPageUrl
          log.d {
            "加载全部Watchlist -> 第${pageCount}页成功(accumulated=${uniqueUsers.size},next=${nextPageUrl?.let(::summarizeUrl) ?: "-"})"
          }
          if (nextPageUrl.isNullOrBlank()) {
            val result = uniqueUsers.values.toList()
            log.i { "加载全部Watchlist -> 成功(user=$username,category=$category,count=${result.size})" }
            return ResourceResult.Success(result)
          }
        }

        is ResourceResult.AuthRequired -> {
          log.w { "加载全部Watchlist -> 需要重新登录(user=$username,category=$category)" }
          return state
        }

        ResourceResult.CfChallenge -> {
          log.w { "加载全部Watchlist -> Cloudflare验证(user=$username,category=$category)" }
          return ResourceResult.CfChallenge
        }

        is ResourceResult.MatureBlocked -> {
          log.w { "加载全部Watchlist -> 受限(user=$username,category=$category,reason=${state.reason})" }
          return state
        }

        is ResourceResult.Error -> {
          log.e(state.exception) { "加载全部Watchlist -> 失败(user=$username,category=$category)" }
          return state
        }
      }
    }
  }
}
