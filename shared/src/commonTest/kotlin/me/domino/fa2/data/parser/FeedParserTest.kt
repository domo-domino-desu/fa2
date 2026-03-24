package me.domino.fa2.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.util.FaUrls

/** FeedParser 解析测试。 */
class FeedParserTest {
  /** 首页面应解析出投稿并包含下一页链接。 */
  @Test
  fun parsesFirstPage() {
    val html = TestFixtures.read("www.furaffinity.net:msg:submissions-firstpage.html")
    val parser = FeedParser()

    val page = parser.parse(html, FaUrls.submissions())

    assertTrue(page.submissions.isNotEmpty())
    assertNotNull(page.nextPageUrl)
    assertTrue(page.submissions.first().id > 0)
    assertTrue(
        page.submissions.first().submissionUrl.startsWith("https://www.furaffinity.net/view/")
    )
    assertTrue(page.submissions.first().authorAvatarUrl.startsWith("https://a.furaffinity.net/"))
  }

  /** 末页面应解析出投稿，且通常没有下一页链接。 */
  @Test
  fun parsesLastPage() {
    val html = TestFixtures.read("www.furaffinity.net:msg:submissions-lastpage.html")
    val parser = FeedParser()

    val page = parser.parse(html, FaUrls.submissions())

    assertTrue(page.submissions.isNotEmpty())
    assertEquals(
        "https://www.furaffinity.net/view/${page.submissions.first().id}/",
        page.submissions.first().submissionUrl,
    )
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
                            <img class="blocked-content" src="//t.furaffinity.net/123456@300-1700000000.jpg" data-width="300" data-height="200" />
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
}
