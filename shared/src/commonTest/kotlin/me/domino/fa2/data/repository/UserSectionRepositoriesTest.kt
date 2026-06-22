package me.domino.fa2.data.fa.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.fa.core.FaHtmlDataSource
import me.domino.fa2.data.fa.core.HtmlResponseResult
import me.domino.fa2.data.fa.favorites.FavoritesDataSource
import me.domino.fa2.data.fa.favorites.FavoritesEndpoint
import me.domino.fa2.data.fa.favorites.FavoritesRepository
import me.domino.fa2.data.fa.gallery.GalleryDataSource
import me.domino.fa2.data.fa.gallery.GalleryEndpoint
import me.domino.fa2.data.fa.gallery.GalleryPageCache
import me.domino.fa2.data.fa.gallery.GalleryParser
import me.domino.fa2.data.fa.gallery.GalleryRepository
import me.domino.fa2.data.fa.journal.JournalDataSource
import me.domino.fa2.data.fa.journal.JournalEndpoint
import me.domino.fa2.data.fa.journal.JournalPageCache
import me.domino.fa2.data.fa.journal.JournalParser
import me.domino.fa2.data.fa.journal.JournalRepository
import me.domino.fa2.data.fa.journal.JournalsDataSource
import me.domino.fa2.data.fa.journal.JournalsEndpoint
import me.domino.fa2.data.fa.journal.JournalsPageCache
import me.domino.fa2.data.fa.journal.JournalsParser
import me.domino.fa2.data.fa.journal.JournalsRepository
import me.domino.fa2.data.model.PageState
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
                body = TestFixtures.read("www.furaffinity.net:gallery:artist-alpha:.html"),
                url = FaUrls.gallery("artist-alpha"),
            ),
    )
    val galleryState = stores.galleryRepository.loadGalleryPage("artist-alpha")
    assertTrue(galleryState is PageState.Success)
    assertTrue(galleryState.data.submissions.isNotEmpty())

    source.enqueue(
        url = FaUrls.favorites("artist-alpha"),
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("www.furaffinity.net:favorites:artist-alpha:.html"),
                url = FaUrls.favorites("artist-alpha"),
            ),
    )
    val favoritesState = stores.favoritesRepository.loadFavoritesPage("artist-alpha")
    assertTrue(favoritesState is PageState.Success)
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
                body = TestFixtures.read("www.furaffinity.net:journals:artist-alpha:.html"),
                url = FaUrls.journals("artist-alpha"),
            ),
    )
    val journalsState = stores.journalsRepository.loadJournalsPage("artist-alpha")
    assertTrue(journalsState is PageState.Success)
    assertTrue(journalsState.data.journals.isNotEmpty())

    source.enqueue(
        url = FaUrls.journal(20000001),
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("www.furaffinity.net:journal:20000001-withcomments.html"),
                url = FaUrls.journal(20000001),
            ),
    )
    val detailState = stores.journalRepository.loadJournalDetail(20000001)
    assertTrue(detailState is PageState.Success)
    assertEquals(20000001, detailState.data.id)
  }

  private fun buildStores(source: FaHtmlDataSource): StoreBundle {
    val pageCacheDao = InMemoryPageCacheDao()
    val galleryStore =
        GalleryPageCache(
            galleryDataSource =
                GalleryDataSource(endpoint = GalleryEndpoint(source), parser = GalleryParser()),
            favoritesDataSource =
                FavoritesDataSource(
                    endpoint = FavoritesEndpoint(source),
                    parser = GalleryParser(),
                ),
            pageCacheDao = pageCacheDao,
        )
    val journalsStore =
        JournalsPageCache(
            dataSource =
                JournalsDataSource(
                    endpoint = JournalsEndpoint(source),
                    parser = JournalsParser(),
                ),
            pageCacheDao = pageCacheDao,
        )
    val journalStore =
        JournalPageCache(
            dataSource =
                JournalDataSource(endpoint = JournalEndpoint(source), parser = JournalParser()),
            pageCacheDao = pageCacheDao,
        )

    return StoreBundle(
        galleryRepository = GalleryRepository(galleryStore),
        favoritesRepository = FavoritesRepository(galleryStore),
        journalsRepository = JournalsRepository(journalsStore),
        journalRepository = JournalRepository(journalStore),
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
      return HtmlResponseResult.Error(
          statusCode = 500,
          message = "No scripted response for $url",
      )
    }
    return queue.removeFirst()
  }
}
