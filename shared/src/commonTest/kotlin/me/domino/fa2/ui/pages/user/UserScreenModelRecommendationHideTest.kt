package me.domino.fa2.ui.pages.user

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
import me.domino.fa2.data.local.watchrecommendation.WatchRecommendationBlocklist
import me.domino.fa2.data.remote.fa.core.FaHtmlDataSource
import me.domino.fa2.data.remote.fa.core.HtmlResponseResult
import me.domino.fa2.data.remote.fa.core.PageQueryCache
import me.domino.fa2.data.remote.fa.social.SocialActionResult
import me.domino.fa2.data.remote.fa.social.SocialActionSource
import me.domino.fa2.data.remote.fa.user.UserParser
import me.domino.fa2.data.remote.fa.user.UserRepository
import me.domino.fa2.fake.InMemoryPageCacheDao
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.fake.UnusedSocialActionSource
import me.domino.fa2.utils.FaUrls

/**
 * Coverage for UserScreenModel's watch-recommendation hide/unhide/toggle-watch behavior, which had
 * no test at all after the pre-refactor equivalent (built on a now-deleted UserResourceRepository
 * fake) was dropped by the QueryState migration.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserScreenModelRecommendationHideTest {
  private val dispatcher = StandardTestDispatcher()
  private val username = "fender"

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
        val source = scriptedSource(watching = false)
        val blocklist = FakeWatchRecommendationBlocklist(username)

        val model = buildModel(source = source, blocklist = blocklist)
        advanceUntilIdle()

        assertTrue(model.state.value.recommendationHidden, model.state.value.toString())
      }

  @Test
  fun hidesOnlyWhenUserIsNotWatchingAndNotAlreadyHidden() =
      runTest(dispatcher.scheduler) {
        val source = scriptedSource(watching = false)
        val blocklist = FakeWatchRecommendationBlocklist()

        val model = buildModel(source = source, blocklist = blocklist)
        advanceUntilIdle()

        model.hideFromRecommendations()
        advanceUntilIdle()

        assertTrue(model.state.value.recommendationHidden, model.state.value.toString())
        assertEquals(setOf(username), blocklist.usernames)
        assertEquals(listOf(username), blocklist.addRequests)
      }

  @Test
  fun hideIsNoOpWhenUserIsWatching() =
      runTest(dispatcher.scheduler) {
        val source = scriptedSource(watching = true)
        val blocklist = FakeWatchRecommendationBlocklist()

        val model = buildModel(source = source, blocklist = blocklist)
        advanceUntilIdle()

        model.hideFromRecommendations()
        advanceUntilIdle()

        assertFalse(model.state.value.recommendationHidden)
        assertTrue(blocklist.addRequests.isEmpty())
      }

  @Test
  fun unhidesHiddenUser() =
      runTest(dispatcher.scheduler) {
        val source = scriptedSource(watching = false)
        val blocklist = FakeWatchRecommendationBlocklist(username)

        val model = buildModel(source = source, blocklist = blocklist)
        advanceUntilIdle()

        model.unhideFromRecommendations()
        advanceUntilIdle()

        assertFalse(model.state.value.recommendationHidden)
        assertTrue(blocklist.usernames.isEmpty())
        assertEquals(listOf(username), blocklist.removeRequests)
      }

  @Test
  fun toggleWatchUsesRepositoryActionFlowAndReloadsHeader() =
      runTest(dispatcher.scheduler) {
        // The scripted source serves the same body for both the initial load and the
        // post-toggle reload, since toggling invalidates the repository cache.
        val source = scriptedSource(watching = true, responseCount = 2)
        val actions = SpySocialActionSource()

        val model =
            buildModel(
                source = source,
                blocklist = FakeWatchRecommendationBlocklist(),
                actions = actions,
            )
        advanceUntilIdle()
        val actionUrl = model.state.value.header?.watchActionUrl.orEmpty()
        assertTrue(actionUrl.isNotBlank())

        model.toggleWatch()
        advanceUntilIdle()

        assertEquals(listOf(actionUrl), actions.executedUrls)
        assertFalse(model.state.value.watchUpdating)
      }

  private fun buildModel(
      source: FaHtmlDataSource,
      blocklist: WatchRecommendationBlocklist,
      actions: SocialActionSource = UnusedSocialActionSource,
  ): UserScreenModel =
      UserScreenModel(
          username = username,
          repository =
              UserRepository(source, UserParser(), PageQueryCache(InMemoryPageCacheDao()), actions),
          blocklistRepository = blocklist,
      )

  private fun scriptedSource(watching: Boolean, responseCount: Int = 1): FaHtmlDataSource {
    val rawBody = TestFixtures.read("live/user.html")
    val body =
        if (watching) rawBody.replace("/watch/fender/", "/unwatch/fender/")
        else rawBody.replace("/unwatch/fender/", "/watch/fender/").replace(">-Watch<", ">+Watch<")
    val source = ScriptedHtmlDataSource()
    repeat(responseCount) {
      source.enqueue(
          url = FaUrls.user(username),
          response = HtmlResponseResult.Success(body = body, url = FaUrls.user(username)),
      )
    }
    return source
  }
}

private class SpySocialActionSource : SocialActionSource {
  val executedUrls: MutableList<String> = mutableListOf()

  override suspend fun execute(actionUrl: String): SocialActionResult {
    executedUrls += actionUrl
    return SocialActionResult.Completed(redirected = false)
  }

  override suspend fun updateTagBlocklist(
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): SocialActionResult = error("Tag blocking is outside this test's scope")
}

private class FakeWatchRecommendationBlocklist(initialUsernames: String = "") :
    WatchRecommendationBlocklist {
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

private class ScriptedHtmlDataSource : FaHtmlDataSource {
  private val queueByUrl: MutableMap<String, ArrayDeque<HtmlResponseResult>> = mutableMapOf()

  fun enqueue(url: String, response: HtmlResponseResult) {
    queueByUrl.getOrPut(url) { ArrayDeque() }.addLast(response)
  }

  override suspend fun get(url: String): HtmlResponseResult {
    val queue = queueByUrl[url]
    if (queue == null || queue.isEmpty()) {
      return HtmlResponseResult.Error(statusCode = 500, message = "No scripted response for $url")
    }
    return queue.removeFirst()
  }
}
