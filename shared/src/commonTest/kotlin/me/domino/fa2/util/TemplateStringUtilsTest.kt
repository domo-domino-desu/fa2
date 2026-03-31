package me.domino.fa2.util

import kotlin.test.Test
import kotlin.test.assertEquals

class TemplateStringUtilsTest {
  @Test
  fun replacesKnownVariablesAndKeepsUnknownVariables() {
    val rendered =
        renderBraceTemplate(
            template = "{username}-{unknown}-{title}",
            values =
                mapOf(
                    "username" to "alice",
                    "title" to "sunset",
                ),
        )

    assertEquals("alice-{unknown}-sunset", rendered)
  }

  @Test
  fun supportsEmptyReplacementValues() {
    val renderedAndCleaned =
        renderBraceTemplate(
                template = "{username}-{title}-{submission_id}",
                values =
                    mapOf(
                        "username" to "alice",
                        "title" to "",
                        "submission_id" to "123",
                    ),
            )
            .let(::cleanupTemplateRenderedText)

    assertEquals("alice-123", renderedAndCleaned)
  }

  @Test
  fun cleanupCollapsesConnectorNoise() {
    assertEquals("a-b_c", cleanupTemplateRenderedText(" a -  b__c "))
    assertEquals("alice", cleanupTemplateRenderedText("alice---"))
  }
}
