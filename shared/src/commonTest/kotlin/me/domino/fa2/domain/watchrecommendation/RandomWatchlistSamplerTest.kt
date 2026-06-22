package me.domino.fa2.domain.watchrecommendation

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.User
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistPage
import me.domino.fa2.data.model.WatchlistUser
import me.domino.fa2.utils.FaUrls

class RandomWatchlistSamplerTest {
  @Test
  fun artistLoadsUserProfileBeforeRandomPages() = runTest {
    val responses =
        mapOf(
            SamplerLoaderKey("artist", null, WatchlistCategory.WatchedBy) to
                successPage(listOf(watchUser("alpha"))),
            SamplerLoaderKey(
                "artist",
                FaUrls.watchlistTo("artist", 2),
                WatchlistCategory.WatchedBy,
            ) to successPage(listOf(watchUser("bravo"))),
            SamplerLoaderKey(
                "artist",
                FaUrls.watchlistTo("artist", 3),
                WatchlistCategory.WatchedBy,
            ) to successPage(listOf(watchUser("charlie"))),
        )
    val userRequests = mutableListOf<String>()
    val watchlistRequests = mutableListOf<SamplerLoaderKey>()
    val sampler =
        buildSampler(
            responses = responses,
            userResponses =
                mapOf("artist" to PageState.Success(testUser("artist", watchedByCount = 600))),
            userRequests = userRequests,
            watchlistRequests = watchlistRequests,
        )

    val result =
        sampler.sample(
            username = "artist",
            category = WatchlistCategory.WatchedBy,
            targetCount = 1,
            guess = WatchlistUserGuess.Artist,
            skipOnFailure = false,
            onProgress = {},
        )

    assertEquals(1, userRequests.size)
    assertEquals(1, result?.size)
    assertEquals(1, watchlistRequests.size)
  }

  @Test
  fun regularUserSinglePageDoesNotLoadUserProfile() = runTest {
    val responses =
        mapOf(
            SamplerLoaderKey("user", null, WatchlistCategory.Watching) to
                successPage(listOf(watchUser("alpha"), watchUser("bravo")))
        )
    val userRequests = mutableListOf<String>()
    val watchlistRequests = mutableListOf<SamplerLoaderKey>()
    val sampler =
        buildSampler(
            responses = responses,
            userRequests = userRequests,
            watchlistRequests = watchlistRequests,
        )

    val result =
        sampler.sample(
            username = "user",
            category = WatchlistCategory.Watching,
            targetCount = 2,
            guess = WatchlistUserGuess.RegularUser,
            skipOnFailure = false,
            onProgress = {},
        )

    assertEquals(0, userRequests.size)
    assertEquals(1, watchlistRequests.size)
    assertEquals(2, result?.size)
  }

  @Test
  fun regularUserLastUsernameAtOrAfterOLoadsSequentialNextPage() = runTest {
    val page2Url = FaUrls.watchlistBy("user", 2)
    val responses =
        mapOf(
            SamplerLoaderKey("user", null, WatchlistCategory.Watching) to
                successPage(listOf(watchUser("oscar")), nextPageUrl = page2Url),
            SamplerLoaderKey("user", page2Url, WatchlistCategory.Watching) to
                successPage(listOf(watchUser("tango"))),
        )
    val userRequests = mutableListOf<String>()
    val watchlistRequests = mutableListOf<SamplerLoaderKey>()
    val sampler =
        buildSampler(
            responses = responses,
            userRequests = userRequests,
            watchlistRequests = watchlistRequests,
        )

    val result =
        sampler.sample(
            username = "user",
            category = WatchlistCategory.Watching,
            targetCount = 2,
            guess = WatchlistUserGuess.RegularUser,
            skipOnFailure = false,
            onProgress = {},
        )

    assertEquals(0, userRequests.size)
    assertEquals(listOf(null, page2Url), watchlistRequests.map { it.nextPageUrl })
    assertEquals(listOf("oscar", "tango").sorted(), result.orEmpty().map { it.username }.sorted())
  }

  @Test
  fun regularUserLastUsernameBeforeOLoadsProfileAndRandomPages() = runTest {
    val page2Url = FaUrls.watchlistBy("user", 2)
    val page3Url = FaUrls.watchlistBy("user", 3)
    val responses =
        mapOf(
            SamplerLoaderKey("user", null, WatchlistCategory.Watching) to
                successPage(listOf(watchUser("alpha")), nextPageUrl = page2Url),
            SamplerLoaderKey("user", page2Url, WatchlistCategory.Watching) to
                successPage(listOf(watchUser("bravo"))),
            SamplerLoaderKey("user", page3Url, WatchlistCategory.Watching) to
                successPage(listOf(watchUser("charlie"))),
        )
    val userRequests = mutableListOf<String>()
    val watchlistRequests = mutableListOf<SamplerLoaderKey>()
    val sampler =
        buildSampler(
            responses = responses,
            userResponses =
                mapOf("user" to PageState.Success(testUser("user", watchingCount = 600))),
            userRequests = userRequests,
            watchlistRequests = watchlistRequests,
        )

    val result =
        sampler.sample(
            username = "user",
            category = WatchlistCategory.Watching,
            targetCount = 2,
            guess = WatchlistUserGuess.RegularUser,
            skipOnFailure = false,
            onProgress = {},
        )

    assertEquals(1, userRequests.size)
    assertEquals(2, result?.size)
    assertTrue(
        watchlistRequests.any { request ->
          request.nextPageUrl == page2Url || request.nextPageUrl == page3Url
        }
    )
  }

  @Test
  fun missingCountFallsBackToSequentialPagination() = runTest {
    val page2Url = FaUrls.watchlistBy("user", 2)
    val responses =
        mapOf(
            SamplerLoaderKey("user", null, WatchlistCategory.Watching) to
                successPage(listOf(watchUser("alpha")), nextPageUrl = page2Url),
            SamplerLoaderKey("user", page2Url, WatchlistCategory.Watching) to
                successPage(listOf(watchUser("bravo"))),
        )
    val userRequests = mutableListOf<String>()
    val watchlistRequests = mutableListOf<SamplerLoaderKey>()
    val sampler =
        buildSampler(
            responses = responses,
            userResponses = mapOf("user" to PageState.Success(testUser("user"))),
            userRequests = userRequests,
            watchlistRequests = watchlistRequests,
        )

    val result =
        sampler.sample(
            username = "user",
            category = WatchlistCategory.Watching,
            targetCount = 2,
            guess = WatchlistUserGuess.RegularUser,
            skipOnFailure = false,
            onProgress = {},
        )

    assertEquals(1, userRequests.size)
    assertEquals(listOf(null, page2Url), watchlistRequests.map { it.nextPageUrl })
    assertEquals(2, result?.size)
  }

  @Test
  fun sampleRandomPagesLoadsRequestedNumberOfRandomPages() = runTest {
    val responses =
        (1..10).associate { page ->
          SamplerLoaderKey(
              username = "user",
              nextPageUrl = if (page == 1) null else FaUrls.watchlistBy("user", page),
              category = WatchlistCategory.Watching,
          ) to successPage(listOf(watchUser("candidate$page")))
        }
    val userRequests = mutableListOf<String>()
    val watchlistRequests = mutableListOf<SamplerLoaderKey>()
    val sampler =
        buildSampler(
            responses = responses,
            userResponses =
                mapOf("user" to PageState.Success(testUser("user", watchingCount = 2000))),
            userRequests = userRequests,
            watchlistRequests = watchlistRequests,
        )

    val result =
        sampler.sampleRandomPages(
            username = "user",
            category = WatchlistCategory.Watching,
            targetPageCount = 5,
            skipOnFailure = false,
            onProgress = {},
        )

    assertEquals(listOf("user"), userRequests)
    assertEquals(5, watchlistRequests.distinct().size)
    assertEquals(5, result?.size)
  }

  private fun buildSampler(
      responses: Map<SamplerLoaderKey, PageState<WatchlistPage>>,
      userResponses: Map<String, PageState<User>> = emptyMap(),
      userRequests: MutableList<String> = mutableListOf(),
      watchlistRequests: MutableList<SamplerLoaderKey> = mutableListOf(),
  ): RandomWatchlistSampler =
      RandomWatchlistSampler(
          loadWatchlistPage = { username, category, nextPageUrl ->
            val key = SamplerLoaderKey(username.lowercase(), nextPageUrl, category)
            watchlistRequests += key
            responses[key]
                ?: PageState.Error(
                    IllegalStateException("missing response for $username::$category::$nextPageUrl")
                )
          },
          loadUser = { username ->
            userRequests += username.lowercase()
            userResponses[username.lowercase()] ?: PageState.Success(testUser(username))
          },
          random = Random(0),
          requestThrottleMs = 0L,
      )

  private fun successPage(
      users: List<WatchlistUser>,
      nextPageUrl: String? = null,
  ): PageState<WatchlistPage> =
      PageState.Success(WatchlistPage(users = users, nextPageUrl = nextPageUrl))

  private fun watchUser(username: String): WatchlistUser =
      WatchlistUser(
          username = username,
          displayName = username.replaceFirstChar(Char::uppercaseChar),
          profileUrl = "https://www.furaffinity.net/user/$username/",
      )

  private fun testUser(
      username: String,
      watchedByCount: Int? = null,
      watchingCount: Int? = null,
  ): User =
      User(
          username = username,
          displayName = username.replaceFirstChar(Char::uppercaseChar),
          avatarUrl = "",
          userTitle = "",
          registeredAt = "",
          watchedByCount = watchedByCount,
          watchingCount = watchingCount,
      )
}

private data class SamplerLoaderKey(
    val username: String,
    val nextPageUrl: String?,
    val category: WatchlistCategory,
)
