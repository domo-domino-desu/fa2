package me.domino.fa2.application.challenge

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChallengeCookiePolicyTest {
  private val policy = CloudflareChallengeCookiePolicy()

  @Test
  fun containsRequiredCookieRecognizesCloudflareCookies() {
    assertTrue(policy.containsRequiredCookie("cf_clearance=token"))
    assertTrue(policy.containsRequiredCookie("__cf_bm=token"))
    assertFalse(policy.containsRequiredCookie("a=1; session=2"))
  }

  @Test
  fun shouldMergeCookieOnlyAcceptsCloudflareCookies() {
    assertTrue(policy.shouldMergeCookie("cf_clearance"))
    assertTrue(policy.shouldMergeCookie("__cf_bm"))
    assertFalse(policy.shouldMergeCookie("a"))
  }
}
