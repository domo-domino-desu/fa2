package me.domino.fa2.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.util.FaUrls

/** UserParser 解析测试。 */
class UserParserTest {
  @Test
  fun parsesUserHeaderFromHomePage() {
    val html = TestFixtures.read("www.furaffinity.net:user:artist-alpha.html")
    val parser = UserParser()

    val header = parser.parse(html = html, url = FaUrls.user("artist-alpha"))

    assertEquals("artist-alpha", header.username.lowercase())
    assertTrue(header.displayName.isNotBlank())
    assertTrue(header.registeredAt.isNotBlank())
    assertTrue(header.profileHtml.contains("bbcode"))
    assertTrue(header.isWatching)
    assertTrue(
        header.watchActionUrl.contains("/watch/") || header.watchActionUrl.contains("/unwatch/")
    )
    assertEquals(2257, header.watchedByCount)
    assertEquals(120, header.watchingCount)
    assertEquals(12, header.shoutCount)
    assertEquals(12, header.shouts.size)
    assertTrue(header.watchedByListUrl.contains("/watchlist/to/artist-alpha"))
    assertTrue(header.watchingListUrl.contains("/watchlist/by/artist-alpha"))
    assertEquals("user-commenter-001", header.shouts.first().author.lowercase())
    assertTrue(header.shouts.first().bodyHtml.contains("Sanitized", ignoreCase = true))
    assertTrue(header.contacts.any { it.label.equals("Twitter", ignoreCase = true) })
    assertTrue(
        header.contacts.any { contact ->
          contact.label.equals("Twitter", ignoreCase = true) &&
              contact.url.contains("social.example.invalid", ignoreCase = true)
        }
    )
    assertEquals(20, header.galleryPreviews.size)
    assertEquals(20, header.favoritesPreviews.size)
    assertEquals(60087990, header.galleryPreviews.first().id)
    assertEquals("Sanitized Gallery Preview", header.galleryPreviews.first().title)
    assertEquals("Artist Alpha", header.galleryPreviews.first().author)
    assertTrue(header.galleryPreviews.first().thumbnailUrl.startsWith("https://t.furaffinity.net/"))
    assertEquals(57803262, header.favoritesPreviews.first().id)
    assertEquals(
        "Sanitized Favorite Preview",
        header.favoritesPreviews.first().title,
    )
    assertEquals("artist-favorite-001", header.favoritesPreviews.first().author)
    assertTrue(
        header.favoritesPreviews.first().thumbnailUrl.startsWith("https://t.furaffinity.net/")
    )
  }

  @Test
  fun mapsSystemErrorToReadableException() {
    val html = TestFixtures.read("www.furaffinity.net:user:username-system-error.html")
    val parser = UserParser()

    val error =
        assertFailsWith<IllegalStateException> {
          parser.parse(html = html, url = FaUrls.user("unknown"))
        }
    assertTrue(error.message.orEmpty().contains("cannot be found", ignoreCase = true))
  }

  @Test
  fun mapsDisabledUserToReadableException() {
    val html = TestFixtures.read("www.furaffinity.net:user:disabled-user-message.html")
    val parser = UserParser()

    val error =
        assertFailsWith<IllegalStateException> {
          parser.parse(html = html, url = FaUrls.user("disabled"))
        }
    assertTrue(error.message.orEmpty().contains("disabled", ignoreCase = true))
  }

  @Test
  fun mapsPendingDeletionToReadableException() {
    val html = TestFixtures.read("www.furaffinity.net:user:pending-deletion-message.html")
    val parser = UserParser()

    val error =
        assertFailsWith<IllegalStateException> {
          parser.parse(html = html, url = FaUrls.user("pending"))
        }
    assertTrue(error.message.orEmpty().contains("pending deletion", ignoreCase = true))
  }

  @Test
  fun parsesProfileBannerFromBannerImageNode() {
    val html = TestFixtures.read("www.furaffinity.net:journal:20000002-disabled-comments.html")
    val parser = UserParser()

    val header = parser.parse(html = html, url = FaUrls.user("fender"))

    assertTrue(header.profileBannerUrl.startsWith("https://d.furaffinity.net/"))
    assertTrue(header.profileBannerUrl.contains("/profile_banner.jpg"))
  }

  @Test
  fun parsesWebsiteAndEmailContacts() {
    val html =
        """
        <html>
          <body>
            <userpage-nav-avatar>
              <a href="/user/testuser/"><img src="/avatar.gif" /></a>
            </userpage-nav-avatar>
            <div class="c-usernameBlock__displayName">Test User</div>
            <div class="c-usernameBlock__userName">~testuser</div>
            <div class="user-title">Tester <span class="popup_date" title="Jan 1, 2024">2 years ago</span></div>
            <div id="userpage-contact">
              <div class="user-contact">
                <div class="user-contact-item">
                  <div class="user-contact-user-info">
                    <span class="font-small"><strong class="highlight">Website</strong></span><br>
                    <a href="https://example.com/profile">https://example.com/profile</a>
                  </div>
                </div>
                <div class="user-contact-item">
                  <div class="user-contact-user-info">
                    <span class="font-small"><strong class="highlight">Email</strong></span><br>
                    <a href="https://example.invalid/email">tester@example.invalid</a>
                  </div>
                </div>
                <div class="user-contact-item">
                  <div class="user-contact-user-info">
                    <span class="font-small"><strong class="highlight">Steam</strong></span><br>
                    test-steam-id
                  </div>
                </div>
              </div>
            </div>
            <section class="userpage-layout-profile">
              <div class="section-body userpage-profile"><p>Hello</p></div>
            </section>
          </body>
        </html>
        """
            .trimIndent()
    val parser = UserParser()

    val header = parser.parse(html = html, url = FaUrls.user("testuser"))

    assertEquals(3, header.contacts.size)
    assertEquals("Website", header.contacts[0].label)
    assertEquals("https://example.com/profile", header.contacts[0].url)
    assertEquals("Email", header.contacts[1].label)
    assertEquals("https://example.invalid/email", header.contacts[1].url)
    assertEquals("tester@example.invalid", header.contacts[1].value)
    assertEquals("Steam", header.contacts[2].label)
    assertEquals("", header.contacts[2].url)
    assertEquals("test-steam-id", header.contacts[2].value)
  }

  @Test
  fun normalizesDomainLikeContactDisplayTextToHttpsUrl() {
    val html =
        """
        <html>
          <body>
            <userpage-nav-avatar>
              <a href="/user/testuser/"><img src="/avatar.gif" /></a>
            </userpage-nav-avatar>
            <div class="c-usernameBlock__displayName">Test User</div>
            <div class="c-usernameBlock__userName">~testuser</div>
            <div class="user-title">Tester <span class="popup_date" title="Jan 1, 2024">2 years ago</span></div>
            <div id="userpage-contact">
              <div class="user-contact">
                <div class="user-contact-item">
                  <div class="user-contact-user-info">
                    <span class="font-small"><strong class="highlight">Discord</strong></span><br>
                    <a href="javascript:void(0)">social.example.invalid/invite</a>
                  </div>
                </div>
              </div>
            </div>
            <section class="userpage-layout-profile">
              <div class="section-body userpage-profile"><p>Hello</p></div>
            </section>
          </body>
        </html>
        """
            .trimIndent()
    val parser = UserParser()

    val header = parser.parse(html = html, url = FaUrls.user("testuser"))

    assertEquals(1, header.contacts.size)
    assertEquals("Discord", header.contacts[0].label)
    assertEquals("social.example.invalid/invite", header.contacts[0].value)
    assertEquals("https://social.example.invalid/invite", header.contacts[0].url)
  }
}
