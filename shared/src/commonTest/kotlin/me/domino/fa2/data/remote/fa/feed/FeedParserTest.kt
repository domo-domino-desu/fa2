package me.domino.fa2.data.remote.fa.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.utils.FaUrls

/** FeedParser 解析测试。 */
class FeedParserTest {
  /** 共用投稿卡片应保留当前公开页面的数据，不臆造缺失的头像元数据。 */
  @Test
  fun parsesCurrentSubmissionCards() {
    val html = TestFixtures.read("live/gallery.html")
    val parser = FeedParser()

    val page = parser.parse(html, FaUrls.submissions())

    assertTrue(page.submissions.isNotEmpty())
    page.nextPageUrl?.let { assertTrue(it.startsWith("https://")) }
    assertTrue(page.submissions.first().id > 0)
    assertTrue(
        page.submissions.first().submissionUrl.startsWith("https://www.furaffinity.net/view/")
    )
    assertTrue(page.submissions.all { it.authorAvatarUrl.isBlank() })
    assertTrue(page.submissions.none { item -> item.isBlockedByTag })
  }

  /** 末页面应解析出投稿，且通常没有下一页链接。 */
  @Test
  fun parsesEmptyResponseWithoutInventingNavigation() {
    val page = FeedParser().parse("<html><body></body></html>", FaUrls.submissions())
    assertTrue(page.submissions.isEmpty())
    assertEquals(null, page.nextPageUrl)
  }

  @Test
  fun parsesThumbnailFromDataSrcWhenSrcMissing() {
    val html =
        """
        <html>
            <body>
                <section class="gallery-section">
                    <figure id="sid-123456">
                        <a href="/view/123456/">
                            <img src="" data-src="//t.furaffinity.net/123456@300-1700000000.jpg" data-width="300" data-height="200" />
                        </a>
                        <figcaption>
                            <p><a href="/view/123456/" title="Sample">Sample</a></p>
                            <p><i>by</i> <a href="/user/demo/">demo</a></p>
                        </figcaption>
                    </figure>
                </section>
            </body>
        </html>
        """
            .trimIndent()
    val parser = FeedParser()

    val page = parser.parse(html, FaUrls.submissions())

    assertEquals(1, page.submissions.size)
    assertEquals(
        "https://t.furaffinity.net/123456@300-1700000000.jpg",
        page.submissions.first().thumbnailUrl,
    )
  }

  @Test
  fun marksBlockedItemWhenTagBlocklistPresent() {
    val html =
        """
        <html>
            <body data-tag-blocklist="canine">
                <section class="gallery-section">
                    <figure id="sid-123456">
                        <a href="/view/123456/">
                            <img class="blocked-content" data-reason='["canine"]' data-tags="wolf canine anthro" src="//t.furaffinity.net/123456@300-1700000000.jpg" data-width="300" data-height="200" />
                        </a>
                        <figcaption>
                            <p><a href="/view/123456/" title="Sample">Sample</a></p>
                            <p><i>by</i> <a href="/user/demo/">demo</a></p>
                        </figcaption>
                    </figure>
                </section>
            </body>
        </html>
        """
            .trimIndent()
    val parser = FeedParser()

    val page = parser.parse(html, FaUrls.submissions())

    assertEquals(1, page.submissions.size)
    assertTrue(page.submissions.first().isBlockedByTag)
  }

  @Test
  fun marksBlockedWhenImageTagsOverlapBlocklist() {
    val html =
        """
        <html>
            <body data-tag-blocklist="weight">
                <section class="gallery-section">
                    <figure id="sid-123456">
                        <a href="/view/123456/">
                            <img class="" data-tags="male weight dragon" src="//t.furaffinity.net/123456@300-1700000000.jpg" data-width="300" data-height="200" />
                        </a>
                        <figcaption>
                            <p><a href="/view/123456/" title="Sample">Sample</a></p>
                            <p><i>by</i> <a href="/user/demo/">demo</a></p>
                        </figcaption>
                    </figure>
                </section>
            </body>
        </html>
        """
            .trimIndent()
    val parser = FeedParser()

    val page = parser.parse(html, FaUrls.submissions())

    assertEquals(1, page.submissions.size)
    assertTrue(page.submissions.first().isBlockedByTag)
  }

  @Test
  fun doesNotMarkBlockedWhenBlocklistEmptyEvenIfClassMarked() {
    val html =
        """
        <html>
            <body data-tag-blocklist="">
                <section class="gallery-section">
                    <figure id="sid-123456">
                        <a href="/view/123456/">
                            <img class="blocked-content" data-tags="canine wolf" src="//t.furaffinity.net/123456@300-1700000000.jpg" data-width="300" data-height="200" />
                        </a>
                        <figcaption>
                            <p><a href="/view/123456/" title="Sample">Sample</a></p>
                            <p><i>by</i> <a href="/user/demo/">demo</a></p>
                        </figcaption>
                    </figure>
                </section>
            </body>
        </html>
        """
            .trimIndent()
    val parser = FeedParser()

    val page = parser.parse(html, FaUrls.submissions())

    assertEquals(1, page.submissions.size)
    assertTrue(!page.submissions.first().isBlockedByTag)
  }

  @Test
  fun doesNotMarkBlockedWhenNoTagOverlap() {
    val html =
        """
        <html>
            <body data-tag-blocklist="weight">
                <section class="gallery-section">
                    <figure id="sid-123456">
                        <a href="/view/123456/">
                            <img data-reason="[]" data-tags="wolf dragon" src="//t.furaffinity.net/123456@300-1700000000.jpg" data-width="300" data-height="200" />
                        </a>
                        <figcaption>
                            <p><a href="/view/123456/" title="Sample">Sample</a></p>
                            <p><i>by</i> <a href="/user/demo/">demo</a></p>
                        </figcaption>
                    </figure>
                </section>
            </body>
        </html>
        """
            .trimIndent()
    val parser = FeedParser()

    val page = parser.parse(html, FaUrls.submissions())

    assertEquals(1, page.submissions.size)
    assertTrue(!page.submissions.first().isBlockedByTag)
  }
}
