package me.domino.fa2.data.remote.fa.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.remote.fa.core.FaHtmlDataSource
import me.domino.fa2.data.remote.fa.core.HtmlResponseResult
import me.domino.fa2.data.remote.fa.core.PageQueryCache
import me.domino.fa2.data.remote.fa.favorites.FavoritesRepository
import me.domino.fa2.data.remote.fa.gallery.GalleryRepository
import me.domino.fa2.data.remote.fa.journal.JournalParser
import me.domino.fa2.data.remote.fa.journal.JournalRepository
import me.domino.fa2.data.remote.fa.journal.JournalsParser
import me.domino.fa2.data.remote.fa.journal.JournalsRepository
import me.domino.fa2.data.remote.fa.listing.SubmissionListingParser
import me.domino.fa2.fake.InMemoryPageCacheDao
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.utils.FaUrls

/** User 子分区仓储链路测试。 */
class UserSectionRepositoriesTest {
  @Test
  fun galleryFavoritesRepositoriesLoadFirstPage() = runTest {
    val source = UserSectionScriptedHtmlDataSource()
    val stores = buildStores(source)

    source.enqueue(
        url = FaUrls.gallery("artist-alpha"),
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("live/gallery.html"),
                url = FaUrls.gallery("artist-alpha"),
            ),
    )
    val galleryState = stores.galleryRepository.loadGalleryPage("artist-alpha")
    assertTrue(galleryState is ResourceResult.Success)
    assertTrue(galleryState.data.submissions.isNotEmpty())

    source.enqueue(
        url = FaUrls.favorites("artist-alpha"),
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("live/favorites.html"),
                url = FaUrls.favorites("artist-alpha"),
            ),
    )
    val favoritesState = stores.favoritesRepository.loadFavoritesPage("artist-alpha")
    assertTrue(favoritesState is ResourceResult.Success)
    assertTrue(favoritesState.data.submissions.isNotEmpty())
  }

  @Test
  fun journalsAndJournalRepositoriesLoadDetail() = runTest {
    val source = UserSectionScriptedHtmlDataSource()
    val stores = buildStores(source)

    source.enqueue(
        url = FaUrls.journals("artist-alpha"),
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("live/journals.html"),
                url = FaUrls.journals("artist-alpha"),
            ),
    )
    val journalsState = stores.journalsRepository.loadJournalsPage("artist-alpha")
    assertTrue(journalsState is ResourceResult.Success)
    assertTrue(journalsState.data.journals.isNotEmpty())

    source.enqueue(
        url = FaUrls.journal(11377368),
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("live/journal.html"),
                url = FaUrls.journal(11377368),
            ),
    )
    val detailState = stores.journalRepository.loadJournalDetail(11377368)
    assertTrue(detailState is ResourceResult.Success)
    assertEquals(11377368, detailState.data.id)
  }

  private fun buildStores(source: FaHtmlDataSource): StoreBundle {
    val queries = PageQueryCache(InMemoryPageCacheDao())
    val listingParser = SubmissionListingParser()

    return StoreBundle(
        galleryRepository = GalleryRepository(source, listingParser, queries),
        favoritesRepository = FavoritesRepository(source, listingParser, queries),
        journalsRepository = JournalsRepository(source, JournalsParser(), queries),
        journalRepository = JournalRepository(source, JournalParser(), queries),
    )
  }
}

private data class StoreBundle(
    val galleryRepository: GalleryRepository,
    val favoritesRepository: FavoritesRepository,
    val journalsRepository: JournalsRepository,
    val journalRepository: JournalRepository,
)

/** 脚本化 HTML 数据源，用于控制请求返回。 */
private class UserSectionScriptedHtmlDataSource : FaHtmlDataSource {
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
