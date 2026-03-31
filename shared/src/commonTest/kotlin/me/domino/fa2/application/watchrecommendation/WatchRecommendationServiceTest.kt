package me.domino.fa2.application.watchrecommendation

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistPage
import me.domino.fa2.data.model.WatchlistUser
import me.domino.fa2.data.repository.WatchRecommendationBlocklistRepository

class WatchRecommendationServiceTest {
  @Test
  fun firstRoundReturnsTopCandidatesWhenEnoughResultsExist() = runTest {
    val followingUsers = (1..10).map(::sourceUser)
    val responses = mutableMapOf<LoaderKey, PageState<WatchlistPage>>()
    responses[LoaderKey("me", null)] = successPage(followingUsers)
    followingUsers.forEach { source ->
      responses[LoaderKey(source.username, null)] =
          successPage(
              listOf(
                  candidateUser("artist-a"),
                  candidateUser("artist-b"),
                  candidateUser("artist-c"),
              )
          )
    }

    val blocklistRepository = FakeWatchRecommendationBlocklistRepository()
    val service = buildService(responses, blocklistRepository)

    val result = service.recommend(username = "me", recommendationCount = 2)

    assertEquals(listOf("artist-a", "artist-b"), result.map { it.user.username })
    assertEquals(listOf(10, 10), result.map { it.sharedFollowCount })
  }

  @Test
  fun expandsSampleAndLowersThresholdAcrossRounds() = runTest {
    val followingUsers = (1..15).map(::sourceUser)
    val responses = mutableMapOf<LoaderKey, PageState<WatchlistPage>>()
    responses[LoaderKey("me", null)] = successPage(followingUsers)
    followingUsers.forEach { source ->
      val extra =
          if (source.username in setOf("source13", "source14", "source15")) {
            listOf(candidateUser("artist-b"))
          } else {
            emptyList()
          }
      responses[LoaderKey(source.username, null)] =
          successPage(listOf(candidateUser("artist-a")) + extra)
    }

    val service = buildService(responses)

    val result = service.recommend(username = "me", recommendationCount = 2)

    assertEquals(listOf("artist-a", "artist-b"), result.map { it.user.username })
    assertEquals(listOf(15, 3), result.map { it.sharedFollowCount })
  }

  @Test
  fun fourthRoundFallsBackToSingleSharedFollowerBaseline() = runTest {
    val followingUsers = (1..25).map(::sourceUser)
    val responses = mutableMapOf<LoaderKey, PageState<WatchlistPage>>()
    responses[LoaderKey("me", null)] = successPage(followingUsers)
    followingUsers.forEachIndexed { index, source ->
      responses[LoaderKey(source.username, null)] =
          successPage(listOf(candidateUser("artist-${index + 1}")))
    }

    val service = buildService(responses)

    val result = service.recommend(username = "me", recommendationCount = 2)

    assertEquals(listOf("artist-1", "artist-10"), result.map { it.user.username })
    assertEquals(listOf(1, 1), result.map { it.sharedFollowCount })
  }

  @Test
  fun excludesSelfAndExistingFollowing() = runTest {
    val followingUsers = (1..10).map(::sourceUser)
    val responses = mutableMapOf<LoaderKey, PageState<WatchlistPage>>()
    responses[LoaderKey("me", null)] = successPage(followingUsers)
    followingUsers.forEach { source ->
      responses[LoaderKey(source.username, null)] =
          successPage(
              listOf(
                  watchUser("me"),
                  watchUser("source1"),
                  candidateUser("artist-a"),
              )
          )
    }

    val service = buildService(responses)

    val result = service.recommend(username = "me", recommendationCount = 5)

    assertEquals(listOf("artist-a"), result.map { it.user.username })
  }

  @Test
  fun sortsBySharedFollowCountThenUsername() = runTest {
    val followingUsers = (1..10).map(::sourceUser)
    val responses = mutableMapOf<LoaderKey, PageState<WatchlistPage>>()
    responses[LoaderKey("me", null)] = successPage(followingUsers)
    followingUsers.forEachIndexed { index, source ->
      val users = mutableListOf<WatchlistUser>()
      if (index < 5) users += candidateUser("omega")
      if (index < 4) users += candidateUser("zeta")
      if (index in 2..5) users += candidateUser("alpha")
      responses[LoaderKey(source.username, null)] = successPage(users)
    }

    val service = buildService(responses)

    val result = service.recommend(username = "me", recommendationCount = 3)

    assertEquals(listOf("omega", "alpha", "zeta"), result.map { it.user.username })
    assertEquals(listOf(5, 4, 4), result.map { it.sharedFollowCount })
  }

  @Test
  fun returnsAllCandidatesWhenFewerThanRequested() = runTest {
    val followingUsers = (1..10).map(::sourceUser)
    val responses = mutableMapOf<LoaderKey, PageState<WatchlistPage>>()
    responses[LoaderKey("me", null)] = successPage(followingUsers)
    followingUsers.forEach { source ->
      responses[LoaderKey(source.username, null)] = successPage(listOf(candidateUser("artist-a")))
    }

    val service = buildService(responses)

    val result = service.recommend(username = "me", recommendationCount = 5)

    assertEquals(listOf("artist-a"), result.map { it.user.username })
  }

  @Test
  fun skipsSampledUsersThatFailToLoad() = runTest {
    val followingUsers = (1..10).map(::sourceUser)
    val responses = mutableMapOf<LoaderKey, PageState<WatchlistPage>>()
    responses[LoaderKey("me", null)] = successPage(followingUsers)
    followingUsers.forEachIndexed { index, source ->
      responses[LoaderKey(source.username, null)] =
          if (index < 2) {
            PageState.Error(IllegalStateException("boom-${source.username}"))
          } else {
            successPage(
                buildList {
                  add(candidateUser("artist-a"))
                  if (index < 6) add(candidateUser("artist-b"))
                }
            )
          }
    }

    val service = buildService(responses)

    val result = service.recommend(username = "me", recommendationCount = 2)

    assertEquals(listOf("artist-a", "artist-b"), result.map { it.user.username })
    assertEquals(listOf(8, 4), result.map { it.sharedFollowCount })
  }

  @Test
  fun failsWhenCurrentUserFollowingCannotBeLoaded() = runTest {
    val responses =
        mutableMapOf(
            LoaderKey("me", null) to PageState.Error(IllegalStateException("missing following"))
        )
    val service = buildService(responses)

    val error =
        assertFailsWith<IllegalStateException> {
          service.recommend(username = "me", recommendationCount = 10)
        }

    assertEquals("missing following", error.message)
  }

  @Test
  fun excludesManuallyBlockedRecommendations() = runTest {
    val followingUsers = (1..10).map(::sourceUser)
    val responses = mutableMapOf<LoaderKey, PageState<WatchlistPage>>()
    responses[LoaderKey("me", null)] = successPage(followingUsers)
    followingUsers.forEachIndexed { index, source ->
      responses[LoaderKey(source.username, null)] =
          successPage(
              buildList {
                add(candidateUser("artist-a"))
                add(candidateUser("artist-b"))
                if (index < 4) add(candidateUser("artist-c"))
              }
          )
    }
    val blocklistRepository =
        FakeWatchRecommendationBlocklistRepository(
            initialUsernames = listOf("artist-a", "artist-b")
        )
    val service = buildService(responses, blocklistRepository)

    val result = service.recommend(username = "me", recommendationCount = 3)

    assertEquals(listOf("artist-c"), result.map { it.user.username })
  }

  @Test
  fun manualBlocklistDoesNotChangeAfterRecommend() = runTest {
    val followingUsers = (1..10).map(::sourceUser)
    val responses = mutableMapOf<LoaderKey, PageState<WatchlistPage>>()
    responses[LoaderKey("me", null)] = successPage(followingUsers)
    followingUsers.forEachIndexed { index, source ->
      responses[LoaderKey(source.username, null)] =
          successPage(
              buildList {
                add(candidateUser("artist-a"))
                if (index < 9) add(candidateUser("artist-b"))
                if (index < 8) add(candidateUser("artist-c"))
                if (index < 7) add(candidateUser("artist-d"))
                if (index < 6) add(candidateUser("artist-e"))
              }
          )
    }
    val blocklistRepository =
        FakeWatchRecommendationBlocklistRepository(initialUsernames = listOf("artist-a"))
    val service = buildService(responses, blocklistRepository)

    val first = service.recommend(username = "me", recommendationCount = 1)
    val second = service.recommend(username = "me", recommendationCount = 1)

    assertEquals(listOf("artist-b"), first.map { it.user.username })
    assertEquals(listOf("artist-b"), second.map { it.user.username })
    assertEquals(listOf("artist-a"), blocklistRepository.listBlockedUsernames())
  }

  private fun buildService(
      responses: Map<LoaderKey, PageState<WatchlistPage>>,
      blocklistRepository: WatchRecommendationBlocklistRepository =
          FakeWatchRecommendationBlocklistRepository(),
  ): WatchRecommendationService =
      WatchRecommendationService(
          loadWatchlistPage = { username, category, nextPageUrl ->
            require(category == WatchlistCategory.Watching)
            responses[LoaderKey(username.lowercase(), nextPageUrl)]
                ?: PageState.Error(
                    IllegalStateException("missing response for $username::$nextPageUrl")
                )
          },
          blocklistRepository = blocklistRepository,
          random = Random(0),
          requestThrottleMs = 0L,
      )

  private fun successPage(
      users: List<WatchlistUser>,
      nextPageUrl: String? = null,
  ): PageState<WatchlistPage> =
      PageState.Success(WatchlistPage(users = users, nextPageUrl = nextPageUrl))

  private fun sourceUser(index: Int): WatchlistUser = watchUser("source$index")

  private fun candidateUser(username: String): WatchlistUser = watchUser(username)

  private fun watchUser(username: String): WatchlistUser =
      WatchlistUser(
          username = username,
          displayName = username.replaceFirstChar(Char::uppercaseChar),
          profileUrl = "https://www.furaffinity.net/user/$username/",
      )
}

private data class LoaderKey(
    val username: String,
    val nextPageUrl: String?,
)

private class FakeWatchRecommendationBlocklistRepository(
    initialUsernames: List<String> = emptyList(),
) : WatchRecommendationBlocklistRepository {
  private val blockedUsernames: MutableList<String> =
      initialUsernames
          .map(String::trim)
          .map(String::lowercase)
          .filter(String::isNotBlank)
          .distinct()
          .toMutableList()

  override suspend fun loadBlockedUsernameSet(): Set<String> = blockedUsernames.toSet()

  override suspend fun listBlockedUsernames(): List<String> = blockedUsernames.toList()

  override suspend fun addBlockedUsername(username: String) {
    val normalized = username.trim().lowercase()
    if (normalized.isBlank() || normalized in blockedUsernames) return
    blockedUsernames += normalized
  }

  override suspend fun removeBlockedUsername(username: String) {
    val normalized = username.trim().lowercase()
    blockedUsernames.removeAll { it == normalized }
  }
}
