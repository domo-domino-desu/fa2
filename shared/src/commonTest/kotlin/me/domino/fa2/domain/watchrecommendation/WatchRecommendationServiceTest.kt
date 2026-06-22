package me.domino.fa2.domain.watchrecommendation

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.local.watchrecommendation.WatchRecommendationBlocklist
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.User
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistPage
import me.domino.fa2.data.model.WatchlistUser
import me.domino.fa2.utils.FaUrls

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

    val blocklistRepository = FakeWatchRecommendationBlocklist()
    val service = buildService(responses, blocklistRepository)

    val result = service.recommend(username = "me", recommendationCount = 2)

    assertEquals(listOf("artist-a", "artist-b", "artist-c"), result.map { it.user.username })
    assertEquals(listOf(10, 10, 10), result.map { it.sharedFollowCount })
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

    assertEquals((1..25).map { index -> "artist-$index" }.sorted(), result.map { it.user.username })
    assertEquals(List(25) { 1 }, result.map { it.sharedFollowCount })
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
  fun middleUserFollowingCandidatesAreLoadedFromFiveRandomPages() = runTest {
    val followingUsers = (1..10).map(::sourceUser)
    val responses = mutableMapOf<LoaderKey, PageState<WatchlistPage>>()
    val userResponses = mutableMapOf<String, PageState<User>>()
    responses[LoaderKey("me", null)] = successPage(followingUsers)
    followingUsers.forEach { source ->
      userResponses[source.username] =
          PageState.Success(testUser(source.username, watchingCount = 1000))
      (1..5).forEach { page ->
        responses[
            LoaderKey(
                username = source.username,
                nextPageUrl = if (page == 1) null else watchlistByUrl(source.username, page),
            )] =
            successPage(
                users =
                    if (page == 5) {
                      listOf(candidateUser("deep-candidate"))
                    } else {
                      listOf(candidateUser("${source.username}-page$page"))
                    }
            )
      }
    }
    val service = buildService(responses = responses, userResponses = userResponses)

    val result = service.recommend(username = "me", recommendationCount = 1)

    assertEquals(listOf("deep-candidate"), result.map { it.user.username })
    assertEquals(listOf(10), result.map { it.sharedFollowCount })
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
        FakeWatchRecommendationBlocklist(initialUsernames = listOf("artist-a", "artist-b"))
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
        FakeWatchRecommendationBlocklist(initialUsernames = listOf("artist-a"))
    val service = buildService(responses, blocklistRepository)

    val first = service.recommend(username = "me", recommendationCount = 1)
    val second = service.recommend(username = "me", recommendationCount = 1)

    assertEquals(
        listOf("artist-b", "artist-c", "artist-d", "artist-e"),
        first.map { it.user.username },
    )
    assertEquals(
        listOf("artist-b", "artist-c", "artist-d", "artist-e"),
        second.map { it.user.username },
    )
    assertEquals(listOf("artist-a"), blocklistRepository.listBlockedUsernames())
  }

  @Test
  fun similarUsersStartFromFollowersAndRankCommonFollowing() = runTest {
    val followers = (1..10).map(::sourceUser)
    val responses = mutableMapOf<LoaderKey, PageState<WatchlistPage>>()
    responses[LoaderKey("artist", null, WatchlistCategory.WatchedBy)] = successPage(followers)
    followers.forEachIndexed { index, follower ->
      responses[LoaderKey(follower.username, null)] =
          successPage(
              buildList {
                add(candidateUser("common-a"))
                if (index < 8) add(candidateUser("common-b"))
                if (index < 6) add(candidateUser("common-c"))
              }
          )
    }

    val service = buildService(responses)

    val result = service.recommendFromFollowers(username = "artist", recommendationCount = 3)

    assertEquals(listOf("common-a", "common-b", "common-c"), result.map { it.user.username })
    assertEquals(listOf(10, 8, 6), result.map { it.sharedFollowCount })
  }

  @Test
  fun similarUsersExcludeSelfAndBlockedButNotFollowers() = runTest {
    val followers = (1..10).map(::sourceUser)
    val responses = mutableMapOf<LoaderKey, PageState<WatchlistPage>>()
    responses[LoaderKey("artist", null, WatchlistCategory.WatchedBy)] = successPage(followers)
    followers.forEach { follower ->
      responses[LoaderKey(follower.username, null)] =
          successPage(
              listOf(
                  watchUser("artist"),
                  watchUser("source1"),
                  candidateUser("blocked-user"),
                  candidateUser("visible-user"),
              )
          )
    }
    val blocklistRepository =
        FakeWatchRecommendationBlocklist(initialUsernames = listOf("blocked-user"))
    val service = buildService(responses, blocklistRepository)

    val result = service.recommendFromFollowers(username = "artist", recommendationCount = 5)

    assertEquals(listOf("source1", "visible-user"), result.map { it.user.username })
  }

  @Test
  fun similarUsersExcludeCurrentLoggedInFollowing() = runTest {
    val followers = (1..10).map(::sourceUser)
    val responses = mutableMapOf<LoaderKey, PageState<WatchlistPage>>()
    responses[LoaderKey("artist", null, WatchlistCategory.WatchedBy)] = successPage(followers)
    responses[LoaderKey("me", null)] = successPage(listOf(candidateUser("already-followed")))
    followers.forEach { follower ->
      responses[LoaderKey(follower.username, null)] =
          successPage(
              listOf(
                  candidateUser("already-followed"),
                  candidateUser("visible-user"),
              )
          )
    }
    val service = buildService(responses)

    val result =
        service.recommendFromFollowers(
            username = "artist",
            recommendationCount = 5,
            excludeFollowingUsername = "me",
        )

    assertEquals(listOf("visible-user"), result.map { it.user.username })
  }

  @Test
  fun recommendationResultsDoNotLoadResultUserProfiles() = runTest {
    val followingUsers = (1..10).map(::sourceUser)
    val responses = mutableMapOf<LoaderKey, PageState<WatchlistPage>>()
    val loadedUsers = mutableListOf<String>()
    responses[LoaderKey("me", null)] = successPage(followingUsers)
    followingUsers.forEach { source ->
      responses[LoaderKey(source.username, null)] = successPage(listOf(candidateUser("artist-a")))
    }
    val service =
        buildService(
            responses = responses,
            onLoadUser = { username -> loadedUsers += username.lowercase() },
        )

    val result = service.recommend(username = "me", recommendationCount = 1)

    assertEquals(listOf("artist-a"), result.map { it.user.username })
    assertFalse("artist-a" in loadedUsers)
  }

  @Test
  fun similarUsersFailWhenFollowersCannotBeLoaded() = runTest {
    val responses =
        mutableMapOf(
            LoaderKey("artist", null, WatchlistCategory.WatchedBy) to
                PageState.Error(IllegalStateException("missing followers"))
        )
    val service = buildService(responses)

    val error =
        assertFailsWith<IllegalStateException> {
          service.recommendFromFollowers(username = "artist", recommendationCount = 10)
        }

    assertEquals("missing followers", error.message)
  }

  @Test
  fun similarUsersSkipFollowersThatFailToLoadFollowing() = runTest {
    val followers = (1..10).map(::sourceUser)
    val responses = mutableMapOf<LoaderKey, PageState<WatchlistPage>>()
    responses[LoaderKey("artist", null, WatchlistCategory.WatchedBy)] = successPage(followers)
    followers.forEachIndexed { index, follower ->
      responses[LoaderKey(follower.username, null)] =
          if (index < 2) {
            PageState.Error(IllegalStateException("boom-${follower.username}"))
          } else {
            successPage(
                buildList {
                  add(candidateUser("common-a"))
                  if (index < 6) add(candidateUser("common-b"))
                }
            )
          }
    }

    val service = buildService(responses)

    val result = service.recommendFromFollowers(username = "artist", recommendationCount = 2)

    assertEquals(listOf("common-a", "common-b"), result.map { it.user.username })
    assertEquals(listOf(8, 4), result.map { it.sharedFollowCount })
  }

  private fun buildService(
      responses: Map<LoaderKey, PageState<WatchlistPage>>,
      blocklistRepository: WatchRecommendationBlocklist = FakeWatchRecommendationBlocklist(),
      userResponses: Map<String, PageState<User>> = emptyMap(),
      onLoadUser: (String) -> Unit = {},
  ): WatchRecommendationService =
      WatchRecommendationService(
          loadWatchlistPage = { username, category, nextPageUrl ->
            responses[LoaderKey(username.lowercase(), nextPageUrl, category)]
                ?: PageState.Error(
                    IllegalStateException("missing response for $username::$category::$nextPageUrl")
                )
          },
          loadUser = { username ->
            onLoadUser(username)
            userResponses[username.lowercase()] ?: PageState.Success(testUser(username))
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

  private fun watchlistByUrl(username: String, page: Int): String =
      FaUrls.watchlistBy(username, page)

  private fun testUser(
      username: String,
      avatarUrl: String = "",
      watchedByCount: Int? = null,
      watchingCount: Int? = null,
  ): User =
      User(
          username = username,
          displayName = username.replaceFirstChar(Char::uppercaseChar),
          avatarUrl = avatarUrl,
          userTitle = "",
          registeredAt = "",
          watchedByCount = watchedByCount,
          watchingCount = watchingCount,
      )

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
    val category: WatchlistCategory = WatchlistCategory.Watching,
)

private class FakeWatchRecommendationBlocklist(
    initialUsernames: List<String> = emptyList(),
) : WatchRecommendationBlocklist {
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
