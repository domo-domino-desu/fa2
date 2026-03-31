package me.domino.fa2.ui.pages.user.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UserContactActionsTest {
  @Test
  fun displayUserContactLabelUsesPlatformName() {
    assertEquals("Twitter", displayUserContactLabel("Twitter"))
    assertEquals("AO3", displayUserContactLabel("Archive of Our Own"))
    assertEquals("Ko-fi", displayUserContactLabel("ko-fi"))
    assertEquals("Picarto", displayUserContactLabel("Picarto"))
  }

  @Test
  fun displayUserContactLabelFallsBackToOriginalLabel() {
    assertEquals("Toyhouse", displayUserContactLabel("Toyhouse"))
    assertEquals("Link", displayUserContactLabel("   "))
  }

  @Test
  fun resolveUserContactIconSupportsPicarto() {
    assertNotNull(resolveUserContactIcon("Picarto"))
  }
}
