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
    val html = TestFixtures.read("www.furaffinity.net:user:terriniss.html")
    val parser = UserParser()

    val header = parser.parse(html = html, url = FaUrls.user("terriniss"))

    assertEquals("terriniss", header.username.lowercase())
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
    assertTrue(header.watchedByListUrl.contains("/watchlist/to/terriniss"))
    assertTrue(header.watchingListUrl.contains("/watchlist/by/terriniss"))
    assertEquals("mengshi", header.shouts.first().author.lowercase())
    assertTrue(header.shouts.first().bodyHtml.contains("Love your gallery", ignoreCase = true))
    assertTrue(header.contacts.any { it.label.equals("Twitter", ignoreCase = true) })
    assertTrue(
        header.contacts.any { contact ->
          contact.label.equals("Twitter", ignoreCase = true) &&
              contact.url.contains("twitter.com", ignoreCase = true)
        }
    )
    assertEquals(20, header.galleryPreviews.size)
    assertEquals(20, header.favoritesPreviews.size)
    assertEquals(60087990, header.galleryPreviews.first().id)
    assertEquals("[OPEN] Adopts FixPrice - FluffyBeans", header.galleryPreviews.first().title)
    assertEquals("Terriniss", header.galleryPreviews.first().author)
    assertTrue(header.galleryPreviews.first().thumbnailUrl.contains("60087990@300-1741039738"))
    assertTrue(header.galleryPreviews.first().authorAvatarUrl.isNotBlank())
    assertEquals(57803262, header.favoritesPreviews.first().id)
    assertEquals(
        "Auction. CLOSED. Adopt Alkior from Closed Species Alivante",
        header.favoritesPreviews.first().title,
    )
    assertEquals("inn_art", header.favoritesPreviews.first().author)
    assertTrue(header.favoritesPreviews.first().thumbnailUrl.contains("57803262@300-1724011901"))
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
    val html = TestFixtures.read("www.furaffinity.net:journal:10882268-disabled-comments.html")
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
                    <a href="mailto:test@example.com">test@example.com</a>
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
    assertEquals("mailto:test@example.com", header.contacts[1].url)
    assertEquals("test@example.com", header.contacts[1].value)
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
                    <a href="javascript:void(0)">discord.gg/testInvite</a>
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
    assertEquals("discord.gg/testInvite", header.contacts[0].value)
    assertEquals("https://discord.gg/testInvite", header.contacts[0].url)
  }
}
