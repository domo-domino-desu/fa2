package me.domino.fa2.ui.pages.submission

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SubmissionRouteScreenKeyTest {
  @Test
  fun repeatedRouteHasDistinctEntryKeys() {
    val first = SubmissionRouteScreen(initialSid = 1, contextId = "same")
    val second = SubmissionRouteScreen(initialSid = 1, contextId = "same")

    assertNotEquals(first.key, second.key)
  }

  @Test
  fun serializationKeepsEntryKey() {
    val screen = SubmissionRouteScreen(initialSid = 1, contextId = "holder")
    val restored = Json.decodeFromString<SubmissionRouteScreen>(Json.encodeToString(screen))

    assertEquals(screen.key, restored.key)
  }
}
