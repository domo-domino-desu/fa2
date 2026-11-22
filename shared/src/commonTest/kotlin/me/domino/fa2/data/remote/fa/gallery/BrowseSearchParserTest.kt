package me.domino.fa2.data.remote.fa.gallery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import me.domino.fa2.data.remote.fa.listing.SubmissionListingParser
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.utils.FaUrls

/** Browse / Search 解析测试。 */
class BrowseSearchParserTest {
  @Test
  fun ignoresDisabledNextSubmitButtonAtEndOfSearch() {
    val html =
        """
        <html><body>
          <form class="c-paginationForm" method="GET" action="/search/">
            <input type="hidden" name="q" value="wolf" />
            <select name="page"><option value="2" selected>2</option></select>
            <button type="submit" name="page" value="3" disabled>Next</button>
          </form>
        </body></html>
        """
    val page =
        SubmissionListingParser()
            .parseListing(html, "https://www.furaffinity.net/search/?q=wolf&page=2")
    assertEquals(null, page.nextPageUrl)
    assertEquals(2, page.currentPageNumber)
  }

  @Test
  fun parsesBrowseListingAndFormBasedNextPage() {
    val html = TestFixtures.read("live/browse.html")
    val parser = SubmissionListingParser()

    val page = parser.parseListing(html = html, baseUrl = FaUrls.browse())

    assertTrue(page.submissions.isNotEmpty())
    assertNotNull(page.nextPageUrl)
    assertTrue(page.nextPageUrl.contains("page=2"))
    assertTrue(page.nextPageUrl.contains("per_page=72"))
  }

  @Test
  fun parsesSearchListingAndAnchorNextPage() {
    val html = TestFixtures.read("live/search.html")
    val parser = SubmissionListingParser()
    val searchUrl = FaUrls.search(FaUrls.SearchParams(q = "dragon @keywords female trans_female"))

    val page = parser.parseListing(html = html, baseUrl = searchUrl)

    assertTrue(page.submissions.isNotEmpty())
    assertNotNull(page.nextPageUrl)
    assertTrue(page.nextPageUrl.contains("/search/"))
    assertTrue(page.nextPageUrl.contains("page=2"))
  }

  @Test
  fun marksBlockedWhenImageTagsOverlapBlocklistInListing() {
    val html =
        """
        <html>
            <body data-tag-blocklist="weight">
                <section class="gallery-section">
                    <figure id="sid-998877">
                        <a href="/view/998877/">
                            <img class="" data-tags="female weight bunny" src="//t.furaffinity.net/998877@300-1700000000.jpg" data-width="300" data-height="200" />
                        </a>
                        <figcaption>
                            <p><a href="/view/998877/" title="Demo">Demo</a></p>
                            <p><i>by</i> <a href="/user/demo/">demo</a></p>
                        </figcaption>
                    </figure>
                </section>
            </body>
        </html>
        """
            .trimIndent()
    val parser = SubmissionListingParser()

    val page = parser.parseListing(html = html, baseUrl = FaUrls.browse())

    assertEquals(1, page.submissions.size)
    assertTrue(page.submissions.first().isBlockedByTag)
  }

  @Test
  fun doesNotMarkBlockedWhenOnlyNonPreviewImageHasBlockedClass() {
    val html =
        """
        <html>
            <body data-tag-blocklist="weight">
                <section class="gallery-section">
                    <figure id="sid-998877">
                        <a href="/view/998877/">
                            <img class="" src="//t.furaffinity.net/998877@300-1700000000.jpg" data-width="300" data-height="200" />
                        </a>
                        <div class="extra">
                            <img class="blocked-content" data-reason='["weight"]' src="//t.furaffinity.net/meta.jpg" />
                        </div>
                        <figcaption>
                            <p><a href="/view/998877/" title="Demo">Demo</a></p>
                            <p><i>by</i> <a href="/user/demo/">demo</a></p>
                        </figcaption>
                    </figure>
                </section>
            </body>
        </html>
        """
            .trimIndent()
    val parser = SubmissionListingParser()

    val page = parser.parseListing(html = html, baseUrl = FaUrls.browse())

    assertEquals(1, page.submissions.size)
    assertTrue(!page.submissions.first().isBlockedByTag)
  }

  @Test
  fun doesNotMarkBlockedWhenReasonIsEmptyArrayWithoutClass() {
    val html =
        """
        <html>
            <body data-tag-blocklist="weight">
                <section class="gallery-section">
                    <figure id="sid-998877">
                        <a href="/view/998877/">
                            <img data-reason="[]" data-tags="wolf dragon" src="//t.furaffinity.net/998877@300-1700000000.jpg" data-width="300" data-height="200" />
                        </a>
                        <figcaption>
                            <p><a href="/view/998877/" title="Demo">Demo</a></p>
                            <p><i>by</i> <a href="/user/demo/">demo</a></p>
                        </figcaption>
                    </figure>
                </section>
            </body>
        </html>
        """
            .trimIndent()
    val parser = SubmissionListingParser()

    val page = parser.parseListing(html = html, baseUrl = FaUrls.browse())

    assertEquals(1, page.submissions.size)
    assertTrue(!page.submissions.first().isBlockedByTag)
  }

  @Test
  fun marksBlockedWhenHideTaglessEnabledAndImageHasNoTags() {
    val html =
        """
        <html>
            <body data-tag-blocklist="" data-tag-blocklist-hide-tagless="1">
                <section class="gallery-section">
                    <figure id="sid-998877">
                        <a href="/view/998877/">
                            <img class="" data-tags="" src="//t.furaffinity.net/998877@300-1700000000.jpg" data-width="300" data-height="200" />
                        </a>
                        <figcaption>
                            <p><a href="/view/998877/" title="Demo">Demo</a></p>
                            <p><i>by</i> <a href="/user/demo/">demo</a></p>
                        </figcaption>
                    </figure>
                </section>
            </body>
        </html>
        """
            .trimIndent()
    val parser = SubmissionListingParser()

    val page = parser.parseListing(html = html, baseUrl = FaUrls.browse())

    assertEquals(1, page.submissions.size)
    assertTrue(page.submissions.first().isBlockedByTag)
  }
}
