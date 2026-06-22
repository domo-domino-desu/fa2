package me.domino.fa2.data.fa.gallery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import me.domino.fa2.data.model.GalleryFolderGroup
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.utils.FaUrls

/** GalleryParser 解析测试。 */
class GalleryParserTest {
  @Test
  fun parsesGalleryAndNextPage() {
    val html = TestFixtures.read("www.furaffinity.net:gallery:artist-alpha:.html")
    val parser = GalleryParser()

    val page =
        parser.parse(
            html = html,
            baseUrl = FaUrls.gallery("artist-alpha"),
            defaultAuthor = "artist-alpha",
        )

    assertTrue(page.submissions.isNotEmpty())
    assertNotNull(page.nextPageUrl)
    assertTrue(page.nextPageUrl.contains("/gallery/artist-alpha/2/"))
    assertTrue(page.submissions.first().id > 0)
    assertTrue(page.submissions.first().author.isNotBlank())
    assertTrue(page.folderGroups.isNotEmpty())
    assertTrue(page.folderGroups.any { group -> group.folders.isNotEmpty() })
    assertTrue(page.folderGroups.flattenFolders().any { folder -> folder.isActive })
    assertTrue(page.folderGroups.flattenFolders().any { folder -> folder.url.contains("/folder/") })
    assertTrue(
        page.submissions.any { item ->
          item.authorAvatarUrl.startsWith("https://a.furaffinity.net/")
        }
    )
  }

  @Test
  fun parsesFavoritesAndNextPage() {
    val html = TestFixtures.read("www.furaffinity.net:favorites:artist-alpha:.html")
    val parser = GalleryParser()

    val page =
        parser.parse(
            html = html,
            baseUrl = FaUrls.favorites("artist-alpha"),
            defaultAuthor = "artist-alpha",
        )

    assertTrue(page.submissions.isNotEmpty())
    assertNotNull(page.nextPageUrl)
    assertTrue(page.nextPageUrl.contains("/favorites/artist-alpha/"))
  }

  @Test
  fun parsesThumbnailFromSrcsetWhenSrcMissing() {
    val html =
        """
        <html>
            <body>
                <section class="gallery-section">
                    <figure id="sid-998877">
                        <a href="/view/998877/">
                            <img src="" srcset="//t.furaffinity.net/998877@300-1700000000.jpg 1x, //t.furaffinity.net/998877@600-1700000000.jpg 2x" data-width="300" data-height="200" />
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

    val page = parser.parse(html = html, baseUrl = FaUrls.gallery("demo"), defaultAuthor = "demo")

    assertEquals(1, page.submissions.size)
    assertEquals(
        "https://t.furaffinity.net/998877@300-1700000000.jpg",
        page.submissions.first().thumbnailUrl,
    )
  }
}

private fun List<GalleryFolderGroup>.flattenFolders() = flatMap { group -> group.folders }
