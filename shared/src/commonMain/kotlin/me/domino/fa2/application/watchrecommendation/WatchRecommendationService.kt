package me.domino.fa2.application.watchrecommendation

import kotlin.random.Random
import me.domino.fa2.application.request.SequentialRequestThrottle
import me.domino.fa2.application.request.defaultSequentialRequestThrottleMs
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistPage
import me.domino.fa2.data.model.WatchlistUser
import me.domino.fa2.data.repository.WatchRecommendationBlocklistRepository
import me.domino.fa2.data.repository.WatchlistRepository
import me.domino.fa2.util.logging.FaLog

/** 第一轮推荐的初始采样用户数。 */
private const val initialSampleSize: Int = 10

/** 每轮增加的采样用户数步长。 */
private const val sampleSizeStep: Int = 5

/** 第一轮所需最少共同关注数阈值。 */
private const val initialMinimumSharedFollowers: Int = 4

/** 最大推荐计算轮次。 */
private const val maxRounds: Int = 4

/** 基于共同关注分析，为当前用户推荐可关注的新用户。 */
class WatchRecommendationService(
    private val loadWatchlistPage:
        suspend (username: String, category: WatchlistCategory, nextPageUrl: String?) -> PageState<
                WatchlistPage
            >,
    private val blocklistRepository: WatchRecommendationBlocklistRepository,
    private val random: Random = Random.Default,
    private val requestThrottleMs: Long = defaultSequentialRequestThrottleMs,
) {
  constructor(
      repository: WatchlistRepository,
      blocklistRepository: WatchRecommendationBlocklistRepository,
      random: Random = Random.Default,
      requestThrottleMs: Long = defaultSequentialRequestThrottleMs,
  ) : this(
      loadWatchlistPage = repository::loadWatchlistPage,
      blocklistRepository = blocklistRepository,
      random = random,
      requestThrottleMs = requestThrottleMs,
  )

  /** 日志标签。 */
  private val log = FaLog.withTag("WatchRecommendationService")

  /** 为指定用户计算并返回推荐关注列表。 */
  suspend fun recommend(
      username: String,
      recommendationCount: Int,
  ): List<RecommendedWatchUser> =
      recommendFromSources(
          username = username,
          recommendationCount = recommendationCount,
          sourceCategory = WatchlistCategory.Watching,
          excludeSourceUsers = true,
          logLabel = "关注推荐",
      )

  /** 基于指定用户的关注者，为该用户发现相似用户。 */
  suspend fun recommendFromFollowers(
      username: String,
      recommendationCount: Int,
  ): List<RecommendedWatchUser> =
      recommendFromSources(
          username = username,
          recommendationCount = recommendationCount,
          sourceCategory = WatchlistCategory.WatchedBy,
          excludeSourceUsers = false,
          logLabel = "相似用户",
      )

  private suspend fun recommendFromSources(
      username: String,
      recommendationCount: Int,
      sourceCategory: WatchlistCategory,
      excludeSourceUsers: Boolean,
      logLabel: String,
  ): List<RecommendedWatchUser> {
    if (recommendationCount <= 0) return emptyList()

    val normalizedUsername = username.trim()
    val throttle = SequentialRequestThrottle(requestThrottleMs)
    val sourceUsers =
        loadCompleteWatchlist(
            username = normalizedUsername,
            category = sourceCategory,
            throttle = throttle,
            skipOnFailure = false,
        ) ?: return emptyList()

    if (sourceUsers.isEmpty()) {
      log.i { "$logLabel -> 没有可抽样用户(user=$normalizedUsername,category=$sourceCategory)" }
      return emptyList()
    }

    val sourcesByKey = linkedMapOf<String, WatchlistUser>()
    sourceUsers.forEach { user -> sourcesByKey.putIfAbsent(user.username.lowercase(), user) }
    val blockedUsernames = blocklistRepository.loadBlockedUsernameSet()
    val excludedUsernames =
        mutableSetOf<String>().apply {
          add(normalizedUsername.lowercase())
          if (excludeSourceUsers) addAll(sourcesByKey.keys)
          addAll(blockedUsernames)
        }
    val sampledUsers = sourcesByKey.values.shuffled(random)

    var bestCandidates: List<RecommendedWatchUser> = emptyList()
    repeat(maxRounds) { round ->
      val sampleSize = initialSampleSize + (round * sampleSizeStep)
      val minimumSharedFollowers = (initialMinimumSharedFollowers - round).coerceAtLeast(1)
      val sources = sampledUsers.take(sampleSize)
      if (sources.isEmpty()) return bestCandidates.take(recommendationCount)

      val counts = linkedMapOf<String, MutableRecommendedWatchUser>()
      sources.forEach { source ->
        val watchedUsers =
            loadCompleteWatchlist(
                username = source.username,
                category = WatchlistCategory.Watching,
                throttle = throttle,
                skipOnFailure = true,
            ) ?: return@forEach

        watchedUsers.forEach { candidate ->
          val key = candidate.username.lowercase()
          if (key in excludedUsernames) return@forEach
          val accumulator =
              counts.getOrPut(key) {
                MutableRecommendedWatchUser(
                    user = candidate,
                    sharedFollowCount = 0,
                )
              }
          accumulator.sharedFollowCount += 1
        }
      }

      val roundCandidates =
          counts.values
              .asSequence()
              .filter { candidate -> candidate.sharedFollowCount >= minimumSharedFollowers }
              .map { candidate ->
                RecommendedWatchUser(
                    user = candidate.user,
                    sharedFollowCount = candidate.sharedFollowCount,
                )
              }
              .sortedWith(
                  compareByDescending<RecommendedWatchUser> { candidate ->
                        candidate.sharedFollowCount
                      }
                      .thenBy { candidate -> candidate.user.username.lowercase() }
              )
              .toList()

      bestCandidates = roundCandidates
      log.i {
        "$logLabel -> 第${round + 1}轮(user=$normalizedUsername,sample=${sources.size},threshold=$minimumSharedFollowers,candidates=${roundCandidates.size},blocked=${blockedUsernames.size})"
      }
      if (roundCandidates.size >= recommendationCount || minimumSharedFollowers <= 1) {
        return roundCandidates.take(recommendationCount)
      }
    }

    return bestCandidates.take(recommendationCount)
  }

  /** 遍历分页加载指定用户的完整 watchlist。 */
  private suspend fun loadCompleteWatchlist(
      username: String,
      category: WatchlistCategory,
      throttle: SequentialRequestThrottle,
      skipOnFailure: Boolean,
  ): List<WatchlistUser>? {
    val uniqueUsers = linkedMapOf<String, WatchlistUser>()
    var nextPageUrl: String? = null
    while (true) {
      throttle.awaitReady()
      when (
          val result =
              loadWatchlistPage(
                  username,
                  category,
                  nextPageUrl,
              )
      ) {
        is PageState.Success -> {
          result.data.users.forEach { user ->
            uniqueUsers.putIfAbsent(user.username.lowercase(), user)
          }
          nextPageUrl = result.data.nextPageUrl
          if (nextPageUrl.isNullOrBlank()) {
            return uniqueUsers.values.toList()
          }
        }

        PageState.CfChallenge -> {
          if (skipOnFailure) {
            log.w { "推荐计算 -> 跳过用户(Cloudflare,user=$username,category=$category)" }
            return null
          }
          error("Cloudflare verification required")
        }

        is PageState.AuthRequired -> {
          if (skipOnFailure) {
            log.w { "推荐计算 -> 跳过用户(需要登录,user=$username,category=$category)" }
            return null
          }
          error(result.message)
        }

        is PageState.MatureBlocked -> {
          if (skipOnFailure) {
            log.w { "推荐计算 -> 跳过用户(受限,user=$username,category=$category,reason=${result.reason})" }
            return null
          }
          error(result.reason)
        }

        is PageState.Error -> {
          if (skipOnFailure) {
            log.w(result.exception) { "推荐计算 -> 跳过用户(失败,user=$username,category=$category)" }
            return null
          }
          throw result.exception
        }

        PageState.Loading -> Unit
      }
    }
  }
}

/** 推荐关注的用户及其共同关注数。 */
data class RecommendedWatchUser(
    /** 被推荐的用户信息。 */
    val user: WatchlistUser,
    /** 与当前用户共同关注的数量。 */
    val sharedFollowCount: Int,
)

/** 推荐计算过程中用于累计共同关注数的可变中间对象。 */
private data class MutableRecommendedWatchUser(
    /** 候选用户信息。 */
    val user: WatchlistUser,
    /** 累计共同关注数（可变）。 */
    var sharedFollowCount: Int,
)
