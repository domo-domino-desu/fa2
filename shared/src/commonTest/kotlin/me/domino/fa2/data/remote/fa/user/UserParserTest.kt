package me.domino.fa2.data.remote.fa.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.utils.FaUrls

/** UserParser 解析测试。 */
class UserParserTest {
  @Test
  fun parsesUserHeaderFromHomePage() {
    val header = UserParser().parse(TestFixtures.read("live/user.html"), FaUrls.user("fender"))
    assertEquals("fender", header.username.lowercase())
    assertTrue(header.displayName.isNotBlank())
    assertTrue(header.profileHtml.isNotBlank())
    assertTrue((header.watchedByCount ?: 0) >= 0)
    assertTrue((header.watchingCount ?: 0) >= 0)
    assertTrue(header.watchedByListUrl.contains("/watchlist/to/"))
    assertTrue(header.watchingListUrl.contains("/watchlist/by/"))
    assertTrue(header.galleryPreviews.isNotEmpty())
    assertTrue(header.galleryPreviews.all { it.id > 0 && it.thumbnailUrl.startsWith("https://") })
    assertTrue(header.favoritesPreviews.all { it.id > 0 && it.author.isNotBlank() })
  }

  @Test
  fun mapsSystemErrorToReadableException() {
    val html = "<html><title>System Error</title><body>This user cannot be found</body></html>"
    val parser = UserParser()

    val error =
        assertFailsWith<IllegalStateException> {
          parser.parse(html = html, url = FaUrls.user("unknown"))
        }
    assertTrue(error.message.orEmpty().contains("cannot be found", ignoreCase = true))
  }

  @Test
  fun mapsDisabledUserToReadableException() {
    val html = "<body>Access has been disabled to the account and contents of user</body>"
    val parser = UserParser()

    val error =
        assertFailsWith<IllegalStateException> {
          parser.parse(html = html, url = FaUrls.user("disabled"))
        }
    assertTrue(error.message.orEmpty().contains("disabled", ignoreCase = true))
  }

  @Test
  fun mapsPendingDeletionToReadableException() {
    val html = "<body>This account is currently pending deletion</body>"
    val parser = UserParser()

    val error =
        assertFailsWith<IllegalStateException> {
          parser.parse(html = html, url = FaUrls.user("pending"))
        }
    assertTrue(error.message.orEmpty().contains("pending deletion", ignoreCase = true))
  }

  @Test
  fun parsesProfileBannerFromBannerImageNode() {
    val html = TestFixtures.read("live/journal.html")
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
