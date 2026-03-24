package me.domino.fa2.data.parser

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.util.FaUrls

/** Browse / Search 解析测试。 */
class BrowseSearchParserTest {
  @Test
  fun parsesBrowseListingAndFormBasedNextPage() {
    val html = TestFixtures.read("www.furaffinity.net:browse:default-page1.html")
    val parser = GalleryParser()

    val page = parser.parseListing(html = html, baseUrl = FaUrls.browse())

    assertTrue(page.submissions.isNotEmpty())
    assertNotNull(page.nextPageUrl)
    assertTrue(page.nextPageUrl.contains("page=2"))
    assertTrue(page.nextPageUrl.contains("per_page=72"))
  }

  @Test
  fun parsesSearchListingAndAnchorNextPage() {
    val html =
      TestFixtures.read("www.furaffinity.net:search:wolf-keywords-female-trans_female.html")
    val parser = GalleryParser()
    val searchUrl = FaUrls.search(FaUrls.SearchParams(q = "wolf @keywords female trans_female"))

    val page = parser.parseListing(html = html, baseUrl = searchUrl)

    assertTrue(page.submissions.isNotEmpty())
    assertNotNull(page.nextPageUrl)
    assertTrue(page.nextPageUrl.contains("/search/"))
    assertTrue(page.nextPageUrl.contains("page=2"))
  }

  @Test
  fun parsesBlockedItemsFromBrowseWithTagBlocklist() {
    val html = TestFixtures.read("www.furaffinity.net:browse:blocked-canine.html")
    val parser = GalleryParser()

    val page = parser.parseListing(html = html, baseUrl = FaUrls.browse())

    assertTrue(page.submissions.isNotEmpty())
    assertTrue(page.submissions.any { item -> item.isBlockedByTag })
    assertTrue(page.submissions.any { item -> !item.isBlockedByTag })
  }
}
