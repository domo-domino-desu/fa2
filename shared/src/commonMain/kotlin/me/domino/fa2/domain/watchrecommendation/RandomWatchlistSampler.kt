package me.domino.fa2.domain.watchrecommendation

import kotlin.math.ceil
import kotlin.random.Random
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.User
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistPage
import me.domino.fa2.data.model.WatchlistUser
import me.domino.fa2.utils.FaUrls
import me.domino.fa2.utils.concurrency.SequentialRequestThrottle
import me.domino.fa2.utils.logging.FaLog

private const val watchlistPageSize: Int = 200

enum class WatchlistUserGuess {
  Artist,
  RegularUser,
}

class RandomWatchlistSampler(
    private val loadWatchlistPage:
        suspend (username: String, category: WatchlistCategory, nextPageUrl: String?) -> PageState<
                WatchlistPage
            >,
    private val loadUser: suspend (username: String) -> PageState<User>,
    private val random: Random = Random.Default,
    private val requestThrottleMs: Long = watchRecommendationRequestThrottleMs,
) {
  private val log = FaLog.withTag("RandomWatchlistSampler")

  suspend fun sample(
      username: String,
      category: WatchlistCategory,
      targetCount: Int,
      guess: WatchlistUserGuess,
      skipOnFailure: Boolean,
      onProgress: (WatchRecommendationProgress) -> Unit,
  ): List<WatchlistUser>? {
    if (targetCount <= 0) return emptyList()
    val normalizedUsername = username.trim()
    val throttle = SequentialRequestThrottle(requestThrottleMs)
    val loadedPages = linkedMapOf<Int, WatchlistPage>()
    val collected = linkedMapOf<String, WatchlistUser>()

    suspend fun loadPage(pageNumber: Int, totalPages: Int?): WatchlistPage? {
      loadedPages[pageNumber]?.let {
        return it
      }
      throttle.awaitReady()
      onProgress(
          WatchRecommendationProgress.LoadingWatchlist(
              username = normalizedUsername,
              category = category,
              page = pageNumber,
              totalPages = totalPages,
          )
      )
      return when (
          val result =
              loadWatchlistPage(
                  normalizedUsername,
                  category,
                  pageUrl(username = normalizedUsername, category = category, page = pageNumber),
              )
      ) {
        is PageState.Success -> {
          loadedPages[pageNumber] = result.data
          result.data
        }

        PageState.CfChallenge -> {
          if (skipOnFailure) {
            log.w {
              "随机Watchlist采样 -> 跳过用户(Cloudflare,user=$normalizedUsername,category=$category)"
            }
            null
          } else {
            error("Cloudflare verification required")
          }
        }

        is PageState.AuthRequired -> {
          if (skipOnFailure) {
            log.w { "随机Watchlist采样 -> 跳过用户(需要登录,user=$normalizedUsername,category=$category)" }
            null
          } else {
            error(result.message)
          }
        }

        is PageState.MatureBlocked -> {
          if (skipOnFailure) {
            log.w {
              "随机Watchlist采样 -> 跳过用户(受限,user=$normalizedUsername,category=$category,reason=${result.reason})"
            }
            null
          } else {
            error(result.reason)
          }
        }

        is PageState.Error -> {
          if (skipOnFailure) {
            log.w(result.exception) {
              "随机Watchlist采样 -> 跳过用户(失败,user=$normalizedUsername,category=$category)"
            }
            null
          } else {
            throw result.exception
          }
        }

        PageState.Loading -> null
      }
    }

    fun addUsers(page: WatchlistPage) {
      page.users.shuffled(random).forEach { user ->
        collected.putIfAbsent(user.username.lowercase(), user)
      }
    }

    suspend fun loadUserTotalPages(): Int? {
      throttle.awaitReady()
      onProgress(WatchRecommendationProgress.LoadingUserProfile(normalizedUsername))
      return when (val state = loadUser(normalizedUsername)) {
        is PageState.Success -> totalPagesFor(state.data, category)
        PageState.CfChallenge ->
            if (skipOnFailure) null else error("Cloudflare verification required")
        is PageState.AuthRequired -> if (skipOnFailure) null else error(state.message)
        is PageState.MatureBlocked -> if (skipOnFailure) null else error(state.reason)
        is PageState.Error -> if (skipOnFailure) null else throw state.exception
        PageState.Loading -> null
      }
    }

    suspend fun sequentialFrom(startPage: Int, totalPages: Int? = null) {
      var pageNumber = startPage
      while (collected.size < targetCount) {
        val page = loadPage(pageNumber = pageNumber, totalPages = totalPages) ?: return
        addUsers(page)
        if (page.nextPageUrl.isNullOrBlank()) return
        pageNumber += 1
      }
    }

    suspend fun randomPages(
        totalPages: Int,
        alreadyLoadedFirstPage: Boolean,
        minimumPagesToLoad: Int = 0,
    ) {
      val candidatePages =
          (1..totalPages).filterNot { page -> alreadyLoadedFirstPage && page == 1 }.shuffled(random)
      onProgress(WatchRecommendationProgress.RandomPagesSelected(candidatePages.take(8)))
      var loadedCount = 0
      for (pageNumber in candidatePages) {
        if (collected.size >= targetCount && loadedCount >= minimumPagesToLoad) return
        val page = loadPage(pageNumber = pageNumber, totalPages = totalPages) ?: return
        addUsers(page)
        loadedCount += 1
      }
    }

    when (guess) {
      WatchlistUserGuess.Artist -> {
        val totalPages = loadUserTotalPages()
        if (totalPages != null && totalPages > 1) {
          randomPages(totalPages = totalPages, alreadyLoadedFirstPage = false)
        } else {
          sequentialFrom(startPage = 1, totalPages = totalPages)
        }
      }

      WatchlistUserGuess.RegularUser -> {
        val firstPage = loadPage(pageNumber = 1, totalPages = null) ?: return null
        addUsers(firstPage)
        if (!firstPage.nextPageUrl.isNullOrBlank()) {
          if (shouldLoadUserPageForRegularUser(firstPage)) {
            onProgress(WatchRecommendationProgress.RegularUserNeedsCount(normalizedUsername))
            val totalPages = loadUserTotalPages()
            if (totalPages != null && totalPages > 1) {
              randomPages(
                  totalPages = totalPages,
                  alreadyLoadedFirstPage = true,
                  minimumPagesToLoad = 1,
              )
            } else {
              sequentialFrom(startPage = 2, totalPages = totalPages)
            }
          } else if (collected.size < targetCount) {
            onProgress(WatchRecommendationProgress.RegularUserSequential(normalizedUsername))
            sequentialFrom(startPage = 2)
          }
        }
      }
    }

    val result = collected.values.shuffled(random).take(targetCount)
    onProgress(WatchRecommendationProgress.RandomUsersCollected(result.size))
    return result
  }

  suspend fun sampleRandomPages(
      username: String,
      category: WatchlistCategory,
      targetPageCount: Int,
      skipOnFailure: Boolean,
      onProgress: (WatchRecommendationProgress) -> Unit,
  ): List<WatchlistUser>? {
    if (targetPageCount <= 0) return emptyList()
    val normalizedUsername = username.trim()
    val throttle = SequentialRequestThrottle(requestThrottleMs)
    val collected = linkedMapOf<String, WatchlistUser>()

    suspend fun loadPage(pageNumber: Int, totalPages: Int?): WatchlistPage? {
      throttle.awaitReady()
      onProgress(
          WatchRecommendationProgress.LoadingWatchlist(
              username = normalizedUsername,
              category = category,
              page = pageNumber,
              totalPages = totalPages,
          )
      )
      return when (
          val result =
              loadWatchlistPage(
                  normalizedUsername,
                  category,
                  pageUrl(username = normalizedUsername, category = category, page = pageNumber),
              )
      ) {
        is PageState.Success -> result.data
        PageState.CfChallenge -> {
          if (skipOnFailure) null else error("Cloudflare verification required")
        }

        is PageState.AuthRequired -> {
          if (skipOnFailure) null else error(result.message)
        }

        is PageState.MatureBlocked -> {
          if (skipOnFailure) null else error(result.reason)
        }

        is PageState.Error -> {
          if (skipOnFailure) null else throw result.exception
        }

        PageState.Loading -> null
      }
    }

    fun addUsers(page: WatchlistPage) {
      page.users.shuffled(random).forEach { user ->
        collected.putIfAbsent(user.username.lowercase(), user)
      }
    }

    suspend fun loadUserTotalPages(): Int? {
      throttle.awaitReady()
      onProgress(WatchRecommendationProgress.LoadingUserProfile(normalizedUsername))
      return when (val state = loadUser(normalizedUsername)) {
        is PageState.Success -> totalPagesFor(state.data, category)
        PageState.CfChallenge ->
            if (skipOnFailure) null else error("Cloudflare verification required")
        is PageState.AuthRequired -> if (skipOnFailure) null else error(state.message)
        is PageState.MatureBlocked -> if (skipOnFailure) null else error(state.reason)
        is PageState.Error -> if (skipOnFailure) null else throw state.exception
        PageState.Loading -> null
      }
    }

    val totalPages = loadUserTotalPages()
    if (totalPages != null) {
      val pages = (1..totalPages).shuffled(random).take(targetPageCount)
      onProgress(WatchRecommendationProgress.RandomPagesSelected(pages))
      pages.forEach { pageNumber ->
        addUsers(loadPage(pageNumber = pageNumber, totalPages = totalPages) ?: return null)
      }
    } else {
      var pageNumber = 1
      var loadedPages = 0
      while (loadedPages < targetPageCount) {
        val page = loadPage(pageNumber = pageNumber, totalPages = null) ?: return null
        addUsers(page)
        loadedPages += 1
        if (page.nextPageUrl.isNullOrBlank()) break
        pageNumber += 1
      }
    }

    val result = collected.values.shuffled(random)
    onProgress(WatchRecommendationProgress.RandomUsersCollected(result.size))
    return result
  }

  private fun shouldLoadUserPageForRegularUser(firstPage: WatchlistPage): Boolean {
    val firstChar =
        firstPage.users.lastOrNull()?.username?.trim()?.lowercase()?.firstOrNull() ?: return false
    return firstChar in 'a'..'z' && firstChar < 'o'
  }

  private fun pageUrl(username: String, category: WatchlistCategory, page: Int): String? =
      when {
        page <= 1 -> null
        category == WatchlistCategory.WatchedBy -> FaUrls.watchlistTo(username, page)
        else -> FaUrls.watchlistBy(username, page)
      }

  private fun totalPagesFor(user: User, category: WatchlistCategory): Int? {
    val count =
        when (category) {
          WatchlistCategory.WatchedBy -> user.watchedByCount
          WatchlistCategory.Watching -> user.watchingCount
        } ?: return null
    return ceil(count.toDouble() / watchlistPageSize.toDouble()).toInt().coerceAtLeast(1)
  }
}
