package me.domino.fa2.application.watchrecommendation

import kotlin.random.Random
import me.domino.fa2.application.request.SequentialRequestThrottle
import me.domino.fa2.application.request.defaultSequentialRequestThrottleMs
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.User
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistPage
import me.domino.fa2.data.model.WatchlistUser
import me.domino.fa2.data.repository.UserRepository
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

/** 推荐算法最多需要的来源用户数。 */
private const val maxSourceSampleSize: Int = initialSampleSize + (sampleSizeStep * (maxRounds - 1))

/** 每个中间用户随机抽取的关注页数。 */
private const val candidateRandomPageCount: Int = 5

/** 基于共同关注分析，为当前用户推荐可关注的新用户。 */
class WatchRecommendationService(
    private val watchlistSampler: RandomWatchlistSampler,
    private val loadWatchlistPage:
        suspend (username: String, category: WatchlistCategory, nextPageUrl: String?) -> PageState<
                WatchlistPage
            >,
    private val blocklistRepository: WatchRecommendationBlocklistRepository,
    private val random: Random = Random.Default,
    private val requestThrottleMs: Long = defaultSequentialRequestThrottleMs,
) {
  constructor(
      loadWatchlistPage:
          suspend (
              username: String,
              category: WatchlistCategory,
              nextPageUrl: String?,
          ) -> PageState<WatchlistPage>,
      loadUser: suspend (username: String) -> PageState<User>,
      blocklistRepository: WatchRecommendationBlocklistRepository,
      random: Random = Random.Default,
      requestThrottleMs: Long = defaultSequentialRequestThrottleMs,
  ) : this(
      watchlistSampler =
          RandomWatchlistSampler(
              loadWatchlistPage = loadWatchlistPage,
              loadUser = loadUser,
              random = random,
              requestThrottleMs = requestThrottleMs,
          ),
      blocklistRepository = blocklistRepository,
      random = random,
      loadWatchlistPage = loadWatchlistPage,
      requestThrottleMs = requestThrottleMs,
  )

  constructor(
      repository: WatchlistRepository,
      userRepository: UserRepository,
      blocklistRepository: WatchRecommendationBlocklistRepository,
      random: Random = Random.Default,
      requestThrottleMs: Long = defaultSequentialRequestThrottleMs,
  ) : this(
      loadWatchlistPage = repository::loadWatchlistPage,
      loadUser = userRepository::loadUser,
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
      excludeFollowingUsername: String? = username,
      onProgress: (WatchRecommendationProgress) -> Unit = {},
  ): List<RecommendedWatchUser> =
      recommendFromSources(
          username = username,
          recommendationCount = recommendationCount,
          sourceCategory = WatchlistCategory.Watching,
          sourceGuess = WatchlistUserGuess.RegularUser,
          excludeSourceUsers = true,
          excludeFollowingUsername = excludeFollowingUsername,
          logLabel = "关注推荐",
          onProgress = onProgress,
      )

  /** 基于指定用户的关注者，为该用户发现相似用户。 */
  suspend fun recommendFromFollowers(
      username: String,
      recommendationCount: Int,
      excludeFollowingUsername: String? = null,
      onProgress: (WatchRecommendationProgress) -> Unit = {},
  ): List<RecommendedWatchUser> =
      recommendFromSources(
          username = username,
          recommendationCount = recommendationCount,
          sourceCategory = WatchlistCategory.WatchedBy,
          sourceGuess = WatchlistUserGuess.Artist,
          excludeSourceUsers = false,
          excludeFollowingUsername = excludeFollowingUsername,
          logLabel = "相似用户",
          onProgress = onProgress,
      )

  private suspend fun recommendFromSources(
      username: String,
      recommendationCount: Int,
      sourceCategory: WatchlistCategory,
      sourceGuess: WatchlistUserGuess,
      excludeSourceUsers: Boolean,
      excludeFollowingUsername: String?,
      logLabel: String,
      onProgress: (WatchRecommendationProgress) -> Unit,
  ): List<RecommendedWatchUser> {
    if (recommendationCount <= 0) return emptyList()

    val normalizedUsername = username.trim()
    val excludedFollowingUsers =
        excludeFollowingUsername
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { followingUsername ->
              loadCompleteWatchingForExclusion(
                  username = followingUsername,
                  onProgress = onProgress,
              )
            }
            .orEmpty()
    val sourceUsers =
        watchlistSampler.sample(
            username = normalizedUsername,
            category = sourceCategory,
            targetCount = maxSourceSampleSize,
            guess = sourceGuess,
            skipOnFailure = false,
            onProgress = onProgress,
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
          addAll(excludedFollowingUsers.map { user -> user.username.lowercase() })
          addAll(blockedUsernames)
        }
    val sampledUsers = sourcesByKey.values.shuffled(random)
    onProgress(
        WatchRecommendationProgress.SamplePrepared(
            sourceCount = sourcesByKey.size,
            maxRounds = maxRounds,
        )
    )

    var bestCandidates: List<RecommendedWatchUser> = emptyList()
    repeat(maxRounds) { round ->
      val sampleSize = initialSampleSize + (round * sampleSizeStep)
      val minimumSharedFollowers = (initialMinimumSharedFollowers - round).coerceAtLeast(1)
      val sources = sampledUsers.take(sampleSize)
      if (sources.isEmpty()) return bestCandidates.take(recommendationCount)
      onProgress(
          WatchRecommendationProgress.RoundStarted(
              round = round + 1,
              sampleSize = sources.size,
              minimumSharedFollowCount = minimumSharedFollowers,
          )
      )

      val counts = linkedMapOf<String, MutableRecommendedWatchUser>()
      sources.forEach { source ->
        val watchedUsers =
            watchlistSampler.sampleRandomPages(
                username = source.username,
                category = WatchlistCategory.Watching,
                targetPageCount = candidateRandomPageCount,
                skipOnFailure = true,
                onProgress = onProgress,
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
      onProgress(
          WatchRecommendationProgress.RoundCompleted(
              round = round + 1,
              candidateCount = roundCandidates.size,
              recommendationCount = recommendationCount,
          )
      )
      log.i {
        "$logLabel -> 第${round + 1}轮(user=$normalizedUsername,sample=${sources.size},threshold=$minimumSharedFollowers,candidates=${roundCandidates.size},blocked=${blockedUsernames.size})"
      }
      if (roundCandidates.size >= recommendationCount || minimumSharedFollowers <= 1) {
        val result = roundCandidates.take(recommendationCount)
        onProgress(WatchRecommendationProgress.Completed(resultCount = result.size))
        return result
      }
    }

    val result = bestCandidates.take(recommendationCount)
    onProgress(WatchRecommendationProgress.Completed(resultCount = result.size))
    return result
  }

  /** 完整顺序加载当前用户已关注列表，仅用于排除“已关注”候选。 */
  private suspend fun loadCompleteWatchingForExclusion(
      username: String,
      onProgress: (WatchRecommendationProgress) -> Unit,
  ): List<WatchlistUser> {
    val normalizedUsername = username.trim()
    val throttle = SequentialRequestThrottle(requestThrottleMs)
    val uniqueUsers = linkedMapOf<String, WatchlistUser>()
    var nextPageUrl: String? = null
    var page = 1
    while (true) {
      throttle.awaitReady()
      onProgress(
          WatchRecommendationProgress.LoadingWatchlist(
              username = normalizedUsername,
              category = WatchlistCategory.Watching,
              page = page,
              totalPages = null,
          )
      )
      when (
          val state =
              loadWatchlistPage(
                  normalizedUsername,
                  WatchlistCategory.Watching,
                  nextPageUrl,
              )
      ) {
        is PageState.Success -> {
          state.data.users.forEach { user ->
            uniqueUsers.putIfAbsent(user.username.lowercase(), user)
          }
          nextPageUrl = state.data.nextPageUrl
          if (nextPageUrl.isNullOrBlank()) return uniqueUsers.values.toList()
          page += 1
        }

        PageState.CfChallenge -> error("Cloudflare verification required")
        is PageState.AuthRequired -> error(state.message)
        is PageState.MatureBlocked -> error(state.reason)
        is PageState.Error -> throw state.exception
        PageState.Loading -> Unit
      }
    }
  }
}

/** 推荐计算进度事件。 */
sealed interface WatchRecommendationProgress {
  data class LoadingWatchlist(
      val username: String,
      val category: WatchlistCategory,
      val page: Int,
      val totalPages: Int?,
  ) : WatchRecommendationProgress

  data class LoadingUserProfile(
      val username: String,
  ) : WatchRecommendationProgress

  data class RegularUserNeedsCount(
      val username: String,
  ) : WatchRecommendationProgress

  data class RegularUserSequential(
      val username: String,
  ) : WatchRecommendationProgress

  data class RandomPagesSelected(
      val pages: List<Int>,
  ) : WatchRecommendationProgress

  data class RandomUsersCollected(
      val count: Int,
  ) : WatchRecommendationProgress

  data class SamplePrepared(
      val sourceCount: Int,
      val maxRounds: Int,
  ) : WatchRecommendationProgress

  data class RoundStarted(
      val round: Int,
      val sampleSize: Int,
      val minimumSharedFollowCount: Int,
  ) : WatchRecommendationProgress

  data class RoundCompleted(
      val round: Int,
      val candidateCount: Int,
      val recommendationCount: Int,
  ) : WatchRecommendationProgress

  data class Completed(
      val resultCount: Int,
  ) : WatchRecommendationProgress
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
