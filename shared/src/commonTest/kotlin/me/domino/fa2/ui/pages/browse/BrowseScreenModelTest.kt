package me.domino.fa2.ui.pages.browse

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.domino.fa2.data.fa.browse.BrowseDataSource
import me.domino.fa2.data.fa.browse.BrowseEndpoint
import me.domino.fa2.data.fa.browse.BrowsePageCache
import me.domino.fa2.data.fa.browse.BrowseRepository
import me.domino.fa2.data.fa.core.FaHtmlDataSource
import me.domino.fa2.data.fa.core.HtmlResponseResult
import me.domino.fa2.data.fa.listing.SubmissionListingParser
import me.domino.fa2.fake.InMemoryPageCacheDao
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.utils.FaUrls

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseScreenModelTest {
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
  fun initialFilterSeedsAppliedFilterUsedByFirstLoadUrl() =
      runTest(dispatcher.scheduler) {
        val htmlDataSource = RecordingBrowseHtmlDataSource()
        val repository = buildRepository(htmlDataSource)
        val initialFilter = BrowseFilterState(category = 2, type = 3, species = 4)

        val screenModel = BrowseScreenModel(repository = repository, initialFilter = initialFilter)

        val expectedUrl =
            FaUrls.browse(
                cat = initialFilter.category,
                atype = initialFilter.type,
                species = initialFilter.species,
                gender = initialFilter.gender,
                page = 1,
                ratingGeneral = initialFilter.ratingGeneral,
                ratingMature = initialFilter.ratingMature,
                ratingAdult = initialFilter.ratingAdult,
            )
        assertEquals(initialFilter, screenModel.state.value.draftFilter)
        assertEquals(initialFilter, screenModel.state.value.appliedFilter)
        assertEquals(expectedUrl, buildBrowseUrl(screenModel.state.value.appliedFilter))
      }

  private fun buildRepository(source: FaHtmlDataSource): BrowseRepository {
    val cache =
        BrowsePageCache(
            dataSource =
                BrowseDataSource(
                    endpoint = BrowseEndpoint(source),
                    parser = SubmissionListingParser(),
                ),
            pageCacheDao = InMemoryPageCacheDao(),
        )
    return BrowseRepository(cache)
  }
}

private class RecordingBrowseHtmlDataSource : FaHtmlDataSource {
  override suspend fun get(url: String): HtmlResponseResult {
    return HtmlResponseResult.Success(
        body = TestFixtures.read("www.furaffinity.net:browse:default-page1.html"),
        url = url,
    )
  }
}
