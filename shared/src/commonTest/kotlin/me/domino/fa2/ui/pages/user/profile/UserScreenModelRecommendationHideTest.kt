package me.domino.fa2.ui.pages.user.profile

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.domino.fa2.data.fa.core.FaHtmlDataSource
import me.domino.fa2.data.fa.core.HtmlResponseResult
import me.domino.fa2.data.fa.social.SocialActionEndpoint
import me.domino.fa2.data.fa.user.UserPageCache
import me.domino.fa2.data.fa.user.UserRepository
import me.domino.fa2.data.local.watchrecommendation.WatchRecommendationBlocklist
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.User
import me.domino.fa2.fake.InMemoryPageCacheDao

@OptIn(ExperimentalCoroutinesApi::class)
class UserScreenModelRecommendationHideTest {
  private val dispatcher = StandardTestDispatcher()

  @BeforeTest
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initializesHiddenStateFromRecommendationBlocklist() =
      runTest(dispatcher.scheduler) {
        val repository = FakeUserRepository(user = testUser(isWatching = false))
        val blocklist = FakeWatchRecommendationBlocklist("artist-alpha")

        val model = buildModel(repository = repository, blocklist = blocklist)
        advanceUntilIdle()

        assertTrue(model.state.value.recommendationHidden, model.state.value.toString())
      }

  @Test
  fun hidesOnlyWhenUserIsNotWatchingAndNotAlreadyHidden() =
      runTest(dispatcher.scheduler) {
        val repository = FakeUserRepository(user = testUser(isWatching = false))
        val blocklist = FakeWatchRecommendationBlocklist()

        val model = buildModel(repository = repository, blocklist = blocklist)
        advanceUntilIdle()

        model.hideFromRecommendations()
        advanceUntilIdle()

        assertTrue(model.state.value.recommendationHidden, model.state.value.toString())
        assertEquals(setOf("artist-alpha"), blocklist.usernames)
        assertEquals(listOf("artist-alpha"), blocklist.addRequests)
      }

  @Test
  fun hideIsNoOpWhenUserIsWatching() =
      runTest(dispatcher.scheduler) {
        val repository = FakeUserRepository(user = testUser(isWatching = true))
        val blocklist = FakeWatchRecommendationBlocklist()

        val model = buildModel(repository = repository, blocklist = blocklist)
        advanceUntilIdle()

        model.hideFromRecommendations()
        advanceUntilIdle()

        assertFalse(model.state.value.recommendationHidden)
        assertTrue(blocklist.addRequests.isEmpty())
      }

  @Test
  fun unhidesHiddenUser() =
      runTest(dispatcher.scheduler) {
        val repository = FakeUserRepository(user = testUser(isWatching = false))
        val blocklist = FakeWatchRecommendationBlocklist("artist-alpha")

        val model = buildModel(repository = repository, blocklist = blocklist)
        advanceUntilIdle()

        model.unhideFromRecommendations()
        advanceUntilIdle()

        assertFalse(model.state.value.recommendationHidden)
        assertTrue(blocklist.usernames.isEmpty())
        assertEquals(listOf("artist-alpha"), blocklist.removeRequests)
      }

  @Test
  fun toggleWatchUsesRepositoryActionFlow() =
      runTest(dispatcher.scheduler) {
        val repository = FakeUserRepository(user = testUser(isWatching = true))
        val blocklist = FakeWatchRecommendationBlocklist()

        val model = buildModel(repository = repository, blocklist = blocklist)
        advanceUntilIdle()
        val actionUrl = model.state.value.header?.watchActionUrl.orEmpty()

        model.toggleWatch()
        advanceUntilIdle()

        assertEquals(listOf("artist-alpha" to actionUrl), repository.toggleRequests)
        assertFalse(model.state.value.watchUpdating)
      }

  private fun buildModel(
      repository: UserRepository,
      blocklist: WatchRecommendationBlocklist,
  ): UserScreenModel =
      UserScreenModel(
          username = "artist-alpha",
          repository = repository,
          blocklistRepository = blocklist,
      )

  private fun testUser(isWatching: Boolean): User =
      User(
          username = "artist-alpha",
          displayName = "Artist Alpha",
          avatarUrl = "",
          userTitle = "",
          registeredAt = "",
          isWatching = isWatching,
          watchActionUrl =
              if (isWatching) {
                "https://www.furaffinity.net/unwatch/artist-alpha/?key=token"
              } else {
                "https://www.furaffinity.net/watch/artist-alpha/?key=token"
              },
      )
}

private class FakeUserRepository(
    private var user: User,
) :
    UserRepository(
        userStore =
            UserPageCache(
                dataSource =
                    me.domino.fa2.data.fa.user.UserDataSource(
                        endpoint = me.domino.fa2.data.fa.user.UserEndpoint(DummyHtmlDataSource),
                        parser = me.domino.fa2.data.fa.user.UserParser(),
                    ),
                pageCacheDao = InMemoryPageCacheDao(),
            ),
        socialActionEndpoint = SocialActionEndpoint(DummyHtmlDataSource),
    ) {
  val toggleRequests: MutableList<Pair<String, String>> = mutableListOf()

  override suspend fun loadUser(username: String): PageState<User> = PageState.Success(user)

  override suspend fun refreshUser(username: String): PageState<User> = PageState.Success(user)

  override suspend fun toggleWatch(username: String, actionUrl: String): PageState<Unit> {
    toggleRequests += username to actionUrl
    user = user.copy(isWatching = !user.isWatching)
    return PageState.Success(Unit)
  }
}

private class FakeWatchRecommendationBlocklist(
    initialUsernames: String = "",
) : WatchRecommendationBlocklist {
  val usernames: MutableSet<String> =
      initialUsernames
          .split(",")
          .map { it.trim().lowercase() }
          .filter { it.isNotBlank() }
          .toMutableSet()
  val addRequests: MutableList<String> = mutableListOf()
  val removeRequests: MutableList<String> = mutableListOf()

  override suspend fun loadBlockedUsernameSet(): Set<String> = usernames.toSet()

  override suspend fun listBlockedUsernames(): List<String> = usernames.toList()

  override suspend fun addBlockedUsername(username: String) {
    val normalized = username.trim().lowercase()
    addRequests += normalized
    usernames += normalized
  }

  override suspend fun removeBlockedUsername(username: String) {
    val normalized = username.trim().lowercase()
    removeRequests += normalized
    usernames -= normalized
  }
}

private object DummyHtmlDataSource : FaHtmlDataSource {
  override suspend fun get(url: String): HtmlResponseResult =
      HtmlResponseResult.Error(statusCode = 500, message = "Unexpected request for $url")
}
