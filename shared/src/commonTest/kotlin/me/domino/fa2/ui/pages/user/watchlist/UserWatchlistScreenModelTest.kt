package me.domino.fa2.ui.pages.user.watchlist

import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
import me.domino.fa2.data.repository.WatchlistRepository

@OptIn(ExperimentalCoroutinesApi::class)
class UserWatchlistScreenModelTest {
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
  fun shuffleAllWatchingUsersLoadsAllPagesAndCachesShuffledList() =
      runTest(dispatcher.scheduler) {
        val fixture =
            createScreenModelFixture(
                initialPage =
                    WatchlistPage(
                        users =
                            listOf(
                                watchlistUser("alpha", "Alpha"),
                                watchlistUser("beta", "Beta"),
                            ),
                        nextPageUrl = "page-2",
                    ),
                allUsers =
                    PageState.Success(
                        listOf(
                            watchlistUser("alpha", "Alpha"),
                            watchlistUser("beta", "Beta"),
                            watchlistUser("gamma", "Gamma"),
                            watchlistUser("delta", "Delta"),
                        )
                    ),
                random = Random(0),
            )
        val screenModel =
            UserWatchlistScreenModel(
                username = "me",
                category = WatchlistCategory.Watching,
                repository = fixture.repository,
                random = fixture.random,
                loadWatchlistPage = fixture::loadPage,
                loadAllWatchlistUsers = fixture::loadAllUsers,
            )

        screenModel.load()
        runCurrent()
        assertEquals(listOf("alpha", "beta"), screenModel.state.value.users.map { it.username })

        screenModel.shuffleAllWatchingUsers()
        runCurrent()

        val state = screenModel.state.value
        assertFalse(state.isShuffling)
        assertTrue(state.hasShuffledAllUsers)
        assertEquals(4, state.users.size)
        assertEquals(4, state.shuffledAllUsers.size)
        assertEquals(null, state.nextPageUrl)
        assertEquals(1, fixture.loadPageCallCount)
        assertEquals(1, fixture.loadAllCallCount)
      }

  @Test
  fun shuffleAllWatchingUsersUsesLocalReshuffleAfterFirstLoad() =
      runTest(dispatcher.scheduler) {
        val fixture =
            createScreenModelFixture(
                initialPage =
                    WatchlistPage(
                        users =
                            listOf(
                                watchlistUser("alpha", "Alpha"),
                                watchlistUser("beta", "Beta"),
                            ),
                        nextPageUrl = "page-2",
                    ),
                allUsers =
                    PageState.Success(
                        listOf(
                            watchlistUser("alpha", "Alpha"),
                            watchlistUser("beta", "Beta"),
                            watchlistUser("gamma", "Gamma"),
                            watchlistUser("delta", "Delta"),
                        )
                    ),
                random = Random(0),
            )
        val screenModel =
            UserWatchlistScreenModel(
                username = "me",
                category = WatchlistCategory.Watching,
                repository = fixture.repository,
                random = fixture.random,
                loadWatchlistPage = fixture::loadPage,
                loadAllWatchlistUsers = fixture::loadAllUsers,
            )

        screenModel.load()
        runCurrent()
        screenModel.shuffleAllWatchingUsers()
        runCurrent()
        val firstOrder = screenModel.state.value.users.map { it.username }

        screenModel.shuffleAllWatchingUsers()
        runCurrent()

        val secondOrder = screenModel.state.value.users.map { it.username }
        assertEquals(1, fixture.loadPageCallCount)
        assertEquals(1, fixture.loadAllCallCount)
        assertTrue(firstOrder.toSet() == secondOrder.toSet())
        assertFalse(firstOrder == secondOrder)
      }

  @Test
  fun shuffleAllWatchingUsersKeepsExistingListWhenLoadFails() =
      runTest(dispatcher.scheduler) {
        val fixture =
            createScreenModelFixture(
                initialPage =
                    WatchlistPage(
                        users =
                            listOf(
                                watchlistUser("alpha", "Alpha"),
                                watchlistUser("beta", "Beta"),
                            ),
                        nextPageUrl = "page-2",
                    ),
                allUsers = PageState.Error(IllegalStateException("boom")),
                random = Random(0),
            )
        val screenModel =
            UserWatchlistScreenModel(
                username = "me",
                category = WatchlistCategory.Watching,
                repository = fixture.repository,
                random = fixture.random,
                loadWatchlistPage = fixture::loadPage,
                loadAllWatchlistUsers = fixture::loadAllUsers,
            )

        screenModel.load()
        runCurrent()
        val initialUsers = screenModel.state.value.users.map { it.username }

        screenModel.shuffleAllWatchingUsers()
        runCurrent()

        val state = screenModel.state.value
        assertEquals(initialUsers, state.users.map { it.username })
        assertFalse(state.isShuffling)
        assertFalse(state.hasShuffledAllUsers)
        assertTrue(state.errorMessage.orEmpty().contains("boom"))
      }

  @Test
  fun shuffleAllWatchingUsersDoesNothingForWatchedBy() =
      runTest(dispatcher.scheduler) {
        val fixture =
            createScreenModelFixture(
                initialPage =
                    WatchlistPage(
                        users = listOf(watchlistUser("alpha", "Alpha")),
                        nextPageUrl = null,
                    ),
                allUsers = PageState.Success(listOf(watchlistUser("alpha", "Alpha"))),
                random = Random(0),
            )
        val screenModel =
            UserWatchlistScreenModel(
                username = "me",
                category = WatchlistCategory.WatchedBy,
                repository = fixture.repository,
                random = fixture.random,
                loadWatchlistPage = fixture::loadPage,
                loadAllWatchlistUsers = fixture::loadAllUsers,
            )

        screenModel.load()
        runCurrent()
        screenModel.shuffleAllWatchingUsers()
        runCurrent()

        assertEquals(1, fixture.loadPageCallCount)
        assertEquals(0, fixture.loadAllCallCount)
        assertFalse(screenModel.state.value.hasShuffledAllUsers)
      }
}

private data class WatchlistScreenModelFixture(
    val repository: WatchlistRepository,
    val random: Random,
    val initialPage: WatchlistPage,
    val allUsers: PageState<List<WatchlistUser>>,
) {
  var loadPageCallCount: Int = 0
    private set

  var loadAllCallCount: Int = 0
    private set

  suspend fun loadPage(
      username: String,
      category: WatchlistCategory,
      nextPageUrl: String?,
  ): PageState<WatchlistPage> {
    loadPageCallCount += 1
    return PageState.Success(initialPage)
  }

  suspend fun loadAllUsers(
      username: String,
      category: WatchlistCategory,
      useFreshFirstPage: Boolean,
  ): PageState<List<WatchlistUser>> {
    loadAllCallCount += 1
    return allUsers
  }
}

private fun createScreenModelFixture(
    initialPage: WatchlistPage,
    allUsers: PageState<List<WatchlistUser>>,
    random: Random,
): WatchlistScreenModelFixture =
    WatchlistScreenModelFixture(
        repository =
            WatchlistRepository(
                me.domino.fa2.data.store.WatchlistStore(
                    dataSource =
                        me.domino.fa2.data.datasource.WatchlistDataSource(
                            endpoint =
                                me.domino.fa2.data.network.endpoint.WatchlistEndpoint(
                                    dataSource =
                                        object : me.domino.fa2.data.network.FaHtmlDataSource {
                                          override suspend fun get(url: String) =
                                              me.domino.fa2.data.network.HtmlResponseResult.Error(
                                                  statusCode = 500,
                                                  message = "unused",
                                              )
                                        }
                                ),
                            parser = me.domino.fa2.data.parser.WatchlistParser(),
                        ),
                    pageCacheDao = me.domino.fa2.fake.InMemoryPageCacheDao(),
                )
            ),
        random = random,
        initialPage = initialPage,
        allUsers = allUsers,
    )

private fun watchlistUser(username: String, displayName: String): WatchlistUser =
    WatchlistUser(
        username = username,
        displayName = displayName,
        profileUrl = "https://www.furaffinity.net/user/$username/",
    )
