package me.domino.fa2.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.datasource.FavoritesDataSource
import me.domino.fa2.data.datasource.GalleryDataSource
import me.domino.fa2.data.datasource.JournalDataSource
import me.domino.fa2.data.datasource.JournalsDataSource
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.data.network.endpoint.FavoriteEndpoint
import me.domino.fa2.data.network.endpoint.GalleryEndpoint
import me.domino.fa2.data.network.endpoint.JournalEndpoint
import me.domino.fa2.data.network.endpoint.JournalsEndpoint
import me.domino.fa2.data.parser.GalleryParser
import me.domino.fa2.data.parser.JournalParser
import me.domino.fa2.data.parser.JournalsParser
import me.domino.fa2.data.store.GalleryStore
import me.domino.fa2.data.store.JournalStore
import me.domino.fa2.data.store.JournalsStore
import me.domino.fa2.fake.InMemoryPageCacheDao
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.util.FaUrls

/** User 子分区仓储链路测试。 */
class UserSectionRepositoriesTest {
  @Test
  fun galleryFavoritesRepositoriesLoadFirstPage() = runTest {
    val source = UserSectionScriptedHtmlDataSource()
    val stores = buildStores(source)

    source.enqueue(
        url = FaUrls.gallery("tiaamaito"),
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("www.furaffinity.net:gallery:tiaamaito:.html"),
                url = FaUrls.gallery("tiaamaito"),
            ),
    )
    val galleryState = stores.galleryRepository.loadGalleryPage("tiaamaito")
    assertTrue(galleryState is PageState.Success)
    assertTrue(galleryState.data.submissions.isNotEmpty())

    source.enqueue(
        url = FaUrls.favorites("tiaamaito"),
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("www.furaffinity.net:favorites:tiaamaito:.html"),
                url = FaUrls.favorites("tiaamaito"),
            ),
    )
    val favoritesState = stores.favoritesRepository.loadFavoritesPage("tiaamaito")
    assertTrue(favoritesState is PageState.Success)
    assertTrue(favoritesState.data.submissions.isNotEmpty())
  }

  @Test
  fun journalsAndJournalRepositoriesLoadDetail() = runTest {
    val source = UserSectionScriptedHtmlDataSource()
    val stores = buildStores(source)

    source.enqueue(
        url = FaUrls.journals("tiaamaito"),
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("www.furaffinity.net:journals:tiaamaito:.html"),
                url = FaUrls.journals("tiaamaito"),
            ),
    )
    val journalsState = stores.journalsRepository.loadJournalsPage("tiaamaito")
    assertTrue(journalsState is PageState.Success)
    assertTrue(journalsState.data.journals.isNotEmpty())

    source.enqueue(
        url = FaUrls.journal(10516170),
        response =
            HtmlResponseResult.Success(
                body = TestFixtures.read("www.furaffinity.net:journal:10516170-withcomments.html"),
                url = FaUrls.journal(10516170),
            ),
    )
    val detailState = stores.journalRepository.loadJournalDetail(10516170)
    assertTrue(detailState is PageState.Success)
    assertEquals(10516170, detailState.data.id)
  }

  private fun buildStores(source: FaHtmlDataSource): StoreBundle {
    val pageCacheDao = InMemoryPageCacheDao()
    val galleryStore =
        GalleryStore(
            galleryDataSource =
                GalleryDataSource(endpoint = GalleryEndpoint(source), parser = GalleryParser()),
            favoritesDataSource =
                FavoritesDataSource(
                    endpoint = FavoriteEndpoint(source),
                    parser = GalleryParser(),
                ),
            pageCacheDao = pageCacheDao,
        )
    val journalsStore =
        JournalsStore(
            dataSource =
                JournalsDataSource(
                    endpoint = JournalsEndpoint(source),
                    parser = JournalsParser(),
                ),
            pageCacheDao = pageCacheDao,
        )
    val journalStore =
        JournalStore(
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
