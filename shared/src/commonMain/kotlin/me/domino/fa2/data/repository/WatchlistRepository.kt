package me.domino.fa2.data.repository

import me.domino.fa2.application.request.SequentialRequestThrottle
import me.domino.fa2.application.request.defaultSequentialRequestThrottleMs
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistPage
import me.domino.fa2.data.model.WatchlistUser
import me.domino.fa2.data.store.WatchlistStore
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizePageState
import me.domino.fa2.util.logging.summarizeUrl

/** watchlist 仓储。 */
class WatchlistRepository(private val store: WatchlistStore) {
  private val log = FaLog.withTag("WatchlistRepository")

  /** 加载 watchlist 分页。 */
  suspend fun loadWatchlistPage(
      username: String,
      category: WatchlistCategory,
      nextPageUrl: String? = null,
  ): PageState<WatchlistPage> {
    log.d {
      "加载Watchlist -> user=$username,category=$category,cursor=${nextPageUrl?.let(::summarizeUrl) ?: "first"}"
    }
    val state =
        store.loadPageOnce(username = username, category = category, nextPageUrl = nextPageUrl)
    log.d { "加载Watchlist -> ${summarizePageState(state)}" }
    return state
  }

  /** 强制刷新 watchlist 首页。 */
  suspend fun refreshWatchlistFirstPage(
      username: String,
      category: WatchlistCategory,
  ): PageState<WatchlistPage> {
    log.i { "刷新Watchlist -> user=$username,category=$category" }
    store.invalidateUserCategory(username = username, category = category)
    val state = loadWatchlistPage(username = username, category = category, nextPageUrl = null)
    log.i { "刷新Watchlist -> ${summarizePageState(state)}" }
    return state
  }

  /** 拉取指定 watchlist 的全部用户。 */
  suspend fun loadAllWatchlistUsers(
      username: String,
      category: WatchlistCategory,
      useFreshFirstPage: Boolean = false,
  ): PageState<List<WatchlistUser>> {
    log.i {
      "加载全部Watchlist -> 开始(user=$username,category=$category,useFreshFirstPage=$useFreshFirstPage)"
    }
    val throttle = SequentialRequestThrottle(defaultSequentialRequestThrottleMs)
    val uniqueUsers = linkedMapOf<String, WatchlistUser>()
    var nextPageUrl: String? = null
    var pageCount = 0

    while (true) {
      throttle.awaitReady()
      val state =
          if (pageCount == 0 && useFreshFirstPage) {
            refreshWatchlistFirstPage(username = username, category = category)
          } else {
            loadWatchlistPage(
                username = username,
                category = category,
                nextPageUrl = nextPageUrl,
            )
          }
      when (state) {
        is PageState.Success -> {
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
            return PageState.Success(result)
          }
        }

        is PageState.AuthRequired -> {
          log.w { "加载全部Watchlist -> 需要重新登录(user=$username,category=$category)" }
          return state
        }

        PageState.CfChallenge -> {
          log.w { "加载全部Watchlist -> Cloudflare验证(user=$username,category=$category)" }
          return PageState.CfChallenge
        }

        is PageState.MatureBlocked -> {
          log.w { "加载全部Watchlist -> 受限(user=$username,category=$category,reason=${state.reason})" }
          return state
        }

        is PageState.Error -> {
          log.e(state.exception) { "加载全部Watchlist -> 失败(user=$username,category=$category)" }
          return state
        }

        PageState.Loading -> Unit
      }
    }
  }
}
