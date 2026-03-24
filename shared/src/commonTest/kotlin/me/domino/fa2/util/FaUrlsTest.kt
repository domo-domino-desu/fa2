package me.domino.fa2.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** FaUrls URL 构建测试。 */
class FaUrlsTest {
  @Test
  fun buildsBrowseUrlWithExplicitParams() {
    val url =
        FaUrls.browse(
            cat = 1,
            atype = 1,
            species = 1,
            gender = "",
            perpage = 72,
            page = 1,
            ratingGeneral = true,
            ratingMature = true,
            ratingAdult = true,
        )
    assertEquals(
        "https://www.furaffinity.net/browse/?cat=1&atype=1&species=1&gender=&perpage=72&page=1&rating_general=1&rating_mature=1&rating_adult=1&go=Apply",
        url,
    )
  }

  @Test
  fun buildsSearchUrlWithDefaultsOmittedAndGenderInQuery() {
    val url = FaUrls.search(FaUrls.SearchParams(q = "wolf @keywords female trans_female"))
    assertTrue(url.contains("/search/?"))
    assertTrue(url.contains("q=wolf%20%40keywords%20female%20trans_female"))
    assertTrue(url.contains("order-by=relevancy"))
    assertTrue(url.contains("order-direction=desc"))
    assertTrue(url.contains("rating-general=1"))
    assertTrue(url.contains("type-art=1"))
    assertTrue(!url.contains("category="))
    assertTrue(!url.contains("arttype="))
    assertTrue(!url.contains("species="))
    assertTrue(!url.contains("mode="))
  }

  @Test
  fun buildsSearchUrlWithManualRange() {
    val url =
        FaUrls.search(
            FaUrls.SearchParams(
                q = "wolf",
                range = "manual",
                rangeFrom = "2026-01-01",
                rangeTo = "2026-02-01",
            )
        )
    assertTrue(url.contains("range=manual"))
    assertTrue(url.contains("range_from=2026-01-01"))
    assertTrue(url.contains("range_to=2026-02-01"))
  }
}
