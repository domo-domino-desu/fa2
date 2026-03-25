package me.domino.fa2.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
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
    val parser = GalleryParser()

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
    val parser = GalleryParser()

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
    val parser = GalleryParser()

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
    val parser = GalleryParser()

    val page = parser.parseListing(html = html, baseUrl = FaUrls.browse())

    assertEquals(1, page.submissions.size)
    assertTrue(page.submissions.first().isBlockedByTag)
  }
}
