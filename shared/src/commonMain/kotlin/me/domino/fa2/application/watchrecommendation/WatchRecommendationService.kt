package me.domino.fa2.application.watchrecommendation

import kotlin.random.Random
import me.domino.fa2.application.request.SequentialRequestThrottle
import me.domino.fa2.application.request.defaultSequentialRequestThrottleMs
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistPage
import me.domino.fa2.data.model.WatchlistUser
import me.domino.fa2.data.repository.WatchRecommendationCooldownRepository
import me.domino.fa2.data.repository.WatchlistRepository
import me.domino.fa2.util.logging.FaLog

private const val initialSampleSize: Int = 10
private const val sampleSizeStep: Int = 5
private const val initialMinimumSharedFollowers: Int = 4
private const val maxRounds: Int = 4

class WatchRecommendationService(
    private val loadWatchlistPage:
        suspend (username: String, category: WatchlistCategory, nextPageUrl: String?) -> PageState<
                WatchlistPage
            >,
    private val cooldownRepository: WatchRecommendationCooldownRepository,
    private val random: Random = Random.Default,
    private val requestThrottleMs: Long = defaultSequentialRequestThrottleMs,
) {
  constructor(
      repository: WatchlistRepository,
      cooldownRepository: WatchRecommendationCooldownRepository,
      random: Random = Random.Default,
      requestThrottleMs: Long = defaultSequentialRequestThrottleMs,
  ) : this(
      loadWatchlistPage = repository::loadWatchlistPage,
      cooldownRepository = cooldownRepository,
      random = random,
      requestThrottleMs = requestThrottleMs,
  )

  private val log = FaLog.withTag("WatchRecommendationService")

  suspend fun recommend(
      username: String,
      recommendationCount: Int,
  ): List<RecommendedWatchUser> {
    if (recommendationCount <= 0) return emptyList()

    val normalizedUsername = username.trim()
    val throttle = SequentialRequestThrottle(requestThrottleMs)
    val following =
        loadCompleteWatching(
            username = normalizedUsername,
            throttle = throttle,
            skipOnFailure = false,
        ) ?: return emptyList()

    if (following.isEmpty()) {
      log.i { "关注推荐 -> 当前账号未关注任何人(user=$normalizedUsername)" }
      return emptyList()
    }

    val followingByKey = linkedMapOf<String, WatchlistUser>()
    following.forEach { user -> followingByKey.putIfAbsent(user.username.lowercase(), user) }
    val cooldownExcludedUsernames = cooldownRepository.loadExcludedUsernames()
    val excludedUsernames =
        followingByKey.keys.toMutableSet().apply {
          add(normalizedUsername.lowercase())
          addAll(cooldownExcludedUsernames)
        }
    val sampledUsers = followingByKey.values.shuffled(random)

    var bestCandidates: List<RecommendedWatchUser> = emptyList()
    repeat(maxRounds) { round ->
      val sampleSize = initialSampleSize + (round * sampleSizeStep)
      val minimumSharedFollowers = (initialMinimumSharedFollowers - round).coerceAtLeast(1)
      val sources = sampledUsers.take(sampleSize)
      if (sources.isEmpty()) return bestCandidates.take(recommendationCount)

      val counts = linkedMapOf<String, MutableRecommendedWatchUser>()
      sources.forEach { source ->
        val watchedUsers =
            loadCompleteWatching(
                username = source.username,
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
        "关注推荐 -> 第${round + 1}轮(user=$normalizedUsername,sample=${sources.size},threshold=$minimumSharedFollowers,candidates=${roundCandidates.size},cooldown=${cooldownExcludedUsernames.size})"
      }
      if (roundCandidates.size >= recommendationCount || minimumSharedFollowers <= 1) {
        val displayedResults = roundCandidates.take(recommendationCount)
        recordDisplayedRecommendations(displayedResults)
        return displayedResults
      }
    }

    val displayedResults = bestCandidates.take(recommendationCount)
    recordDisplayedRecommendations(displayedResults)
    return displayedResults
  }

  private suspend fun loadCompleteWatching(
      username: String,
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
                  WatchlistCategory.Watching,
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
            log.w { "关注推荐 -> 跳过用户(Cloudflare,user=$username)" }
            return null
          }
          error("Cloudflare verification required")
        }

        is PageState.MatureBlocked -> {
          if (skipOnFailure) {
            log.w { "关注推荐 -> 跳过用户(受限,user=$username,reason=${result.reason})" }
            return null
          }
          error(result.reason)
        }

        is PageState.Error -> {
          if (skipOnFailure) {
            log.w(result.exception) { "关注推荐 -> 跳过用户(失败,user=$username)" }
            return null
          }
          throw result.exception
        }

        PageState.Loading -> Unit
      }
    }
  }

  private suspend fun recordDisplayedRecommendations(results: List<RecommendedWatchUser>) {
    if (results.isEmpty()) return
    cooldownRepository.recordDisplayedRecommendations(
        results.map { result -> result.user.username }
    )
  }
}

data class RecommendedWatchUser(
    val user: WatchlistUser,
    val sharedFollowCount: Int,
)

private data class MutableRecommendedWatchUser(
    val user: WatchlistUser,
    var sharedFollowCount: Int,
)
