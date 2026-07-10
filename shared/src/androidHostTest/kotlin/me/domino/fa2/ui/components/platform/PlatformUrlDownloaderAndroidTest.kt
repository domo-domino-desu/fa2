package me.domino.fa2.ui.components.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PlatformUrlDownloaderAndroidTest {
  @Test
  fun temporaryDownloadNameIsHiddenAndCannotCollideAcrossTimestamps() {
    val first = buildAndroidTemporaryDownloadName(submissionId = 42, timestampMs = 1_000)
    val second = buildAndroidTemporaryDownloadName(submissionId = 42, timestampMs = 1_001)

    assertEquals(".fa2-42-1000.part", first)
    assertNotEquals(first, second)
  }
}
