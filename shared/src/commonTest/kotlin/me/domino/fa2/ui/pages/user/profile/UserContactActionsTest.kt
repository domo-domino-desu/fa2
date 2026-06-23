package me.domino.fa2.ui.pages.user.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import me.domino.fa2.ui.pages.overlays.journaldetail.*
import me.domino.fa2.ui.pages.overlays.journalpager.*
import me.domino.fa2.ui.pages.overlays.userpager.*
import me.domino.fa2.ui.pages.overlays.usershouts.*
import me.domino.fa2.ui.pages.overlays.userwatchlist.*
import me.domino.fa2.ui.pages.overlays.watchrecommendation.*
import me.domino.fa2.ui.pages.user.*

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
