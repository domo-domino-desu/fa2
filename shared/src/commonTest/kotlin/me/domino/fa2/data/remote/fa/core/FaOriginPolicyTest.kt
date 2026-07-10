package me.domino.fa2.data.remote.fa.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FaOriginPolicyTest {
  @Test
  fun pageOriginRequiresHttpsCanonicalWebHost() {
    assertTrue(FaOriginPolicy.isTrustedPageOrigin("https://www.furaffinity.net/view/1/"))
    assertTrue(FaOriginPolicy.isTrustedPageOrigin("https://furaffinity.net/user/test/"))
    assertFalse(FaOriginPolicy.isTrustedPageOrigin("http://www.furaffinity.net/view/1/"))
    assertFalse(FaOriginPolicy.isTrustedPageOrigin("https://www.furaffinity.net.evil.test/view/1/"))
    assertFalse(FaOriginPolicy.isTrustedPageOrigin("https://d.furaffinity.net/art/file.png"))
  }

  @Test
  fun cookieOriginAllowsOnlyHttpsFaOwnedHosts() {
    assertTrue(FaOriginPolicy.isTrustedCookieOrigin("https://d.furaffinity.net/art/file.png"))
    assertFalse(FaOriginPolicy.isTrustedCookieOrigin("http://d.furaffinity.net/art/file.png"))
    assertFalse(FaOriginPolicy.isTrustedCookieOrigin("https://furaffinity.net.evil.test/"))
  }

  @Test
  fun actionUrlRequiresExpectedPath() {
    assertEquals(
        "https://www.furaffinity.net/fav/1/?key=x",
        FaOriginPolicy.trustedActionUrlOrNull(
            "https://www.furaffinity.net/fav/1/?key=x",
            setOf("/fav/", "/unfav/"),
        ),
    )
    assertNull(
        FaOriginPolicy.trustedActionUrlOrNull(
            "https://www.furaffinity.net/user/test/",
            setOf("/fav/", "/unfav/"),
        )
    )
    assertNull(
        FaOriginPolicy.trustedActionUrlOrNull(
            "https://evil.test/fav/1/?key=x",
            setOf("/fav/", "/unfav/"),
        )
    )
  }
}
