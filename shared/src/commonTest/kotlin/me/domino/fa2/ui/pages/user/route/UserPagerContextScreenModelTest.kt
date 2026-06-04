package me.domino.fa2.ui.pages.user.route

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.model.WatchlistCategory
import me.domino.fa2.data.model.WatchlistPage
import me.domino.fa2.data.model.WatchlistUser

@OptIn(ExperimentalCoroutinesApi::class)
class UserPagerContextScreenModelTest {
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
  fun updateUsersKeepsCurrentUsernameWhenOrderChanges() {
    val model = UserPagerContextScreenModel()
    model.seed(
        source = UserPagerSource.Recommendation,
        users = listOf(user("alpha"), user("beta"), user("gamma")),
        selectedUsername = "beta",
    )

    model.updateUsers(listOf(user("gamma"), user("beta"), user("alpha")))

    assertEquals("beta", model.state.value.currentUsername)
    assertEquals(1, model.state.value.currentIndex)
  }

  @Test
  fun updateUsersFallsBackToAdjacentIndexWhenCurrentUserDisappears() {
    val model = UserPagerContextScreenModel()
    model.seed(
        source = UserPagerSource.SimilarUsers,
        users = listOf(user("alpha"), user("beta"), user("gamma")),
        selectedUsername = "beta",
    )

    model.updateUsers(listOf(user("alpha"), user("gamma")))

    assertEquals("gamma", model.state.value.currentUsername)
    assertEquals(1, model.state.value.currentIndex)
  }

  @Test
  fun requestAppendMergesUsersAndIgnoresConcurrentDuplicate() =
      runTest(dispatcher.scheduler) {
        val model = UserPagerContextScreenModel()
        var loadCount = 0
        model.seed(
            source =
                UserPagerSource.Watchlist(
                    ownerUsername = "owner",
                    category = WatchlistCategory.Watching,
                ),
            users = listOf(user("alpha"), user("beta")),
            selectedUsername = "beta",
            nextPageUrl = "page-2",
        )

        model.requestAppend {
          loadCount++
          PageState.Success(
              WatchlistPage(users = listOf(user("beta"), user("gamma")), nextPageUrl = null)
          )
        }
        model.requestAppend {
          loadCount++
          PageState.Success(WatchlistPage(users = listOf(user("delta")), nextPageUrl = null))
        }
        runCurrent()

        assertEquals(1, loadCount)
        assertEquals(listOf("alpha", "beta", "gamma"), model.state.value.users.map { it.username })
        assertEquals("beta", model.state.value.currentUsername)
        assertEquals(null, model.state.value.nextPageUrl)
      }
}

private fun user(username: String): WatchlistUser =
    WatchlistUser(
        username = username,
        displayName = username.replaceFirstChar { it.uppercase() },
        profileUrl = "https://www.furaffinity.net/user/$username/",
    )
