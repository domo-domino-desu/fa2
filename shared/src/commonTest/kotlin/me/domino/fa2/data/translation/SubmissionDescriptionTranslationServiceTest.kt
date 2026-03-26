package me.domino.fa2.data.translation

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.local.KeyValueStorage
import me.domino.fa2.data.parser.SubmissionParser
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.data.settings.AppSettingsStorage
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.util.FaUrls
import okio.FileSystem
import okio.Path.Companion.toPath

class SubmissionDescriptionTranslationServiceTest {
  @Test
  fun extractsParagraphsFromPlainDescription() {
    val service = createService()
    val parser = SubmissionParser()
    val html = TestFixtures.read("www.furaffinity.net:view:49338772-nocomment.html")
    val detail = parser.parse(html = html, url = FaUrls.submission(49338772))

    val blocks = service.extractBlocks(detail.descriptionHtml)

    assertTrue(blocks.size >= 3, "Expected split paragraphs, actual=${blocks.size}")
    assertTrue(blocks.any { block -> block.sourceText.contains("YCH", ignoreCase = true) })
    assertTrue(blocks.any { block -> block.sourceText.contains("Cody", ignoreCase = true) })
    assertTrue(
        blocks.any { block -> block.sourceText.contains("Feed me with coffee", ignoreCase = true) }
    )
    assertTrue(
        blocks.none { block ->
          block.sourceText.startsWith('\n') || block.sourceText.endsWith('\n')
        }
    )
  }

  @Test
  fun extractsParagraphsInsideCodeWrapperAndKeepsWrapperTag() {
    val service = createService()
    val parser = SubmissionParser()
    val html = TestFixtures.read("www.furaffinity.net:view:49917619-comment-hidden.html")
    val detail = parser.parse(html = html, url = FaUrls.submission(49917619))

    val blocks = service.extractBlocks(detail.descriptionHtml)

    assertTrue(
        blocks.size >= 4,
        "Expected multiple paragraphs in code wrapper, actual=${blocks.size}",
    )
    assertTrue(
        blocks.any { block -> block.sourceText.contains("Since I thought", ignoreCase = true) }
    )
    assertTrue(
        blocks.any { block ->
          block.sourceText.contains("Payment is made through PayPal", ignoreCase = true)
        }
    )
    assertTrue(
        blocks.all { block -> block.originalHtml.contains("class=\"bbcode bbcode_center\"") },
        "Each split block should keep code wrapper attributes.",
    )
    assertTrue(
        blocks.none { block ->
          block.sourceText.startsWith('\n') || block.sourceText.endsWith('\n')
        }
    )
  }

  @Test
  fun extractsIndependentBlocksForDivCodeWithBreakBoundaries() {
    val service = createService()
    val html =
        """
        <div class="submission-description user-submitted-links">
            <code class="bbcode bbcode_center">
                First paragraph<br><br>
                Second paragraph<br><br>
                <b class="bbcode bbcode_b">Third paragraph</b><br><br>
                Fourth paragraph
            </code>
        </div>
        """
            .trimIndent()

    val blocks = service.extractBlocks(html)

    assertEquals(
        expected =
            listOf(
                "First paragraph",
                "Second paragraph",
                "Third paragraph",
                "Fourth paragraph",
            ),
        actual = blocks.map { it.sourceText },
    )
    assertTrue(
        blocks.all { block ->
          block.originalHtml.startsWith(
              "<div class=\"submission-description user-submitted-links\"><code class=\"bbcode bbcode_center\">"
          ) && block.originalHtml.endsWith("</code></div>")
        },
        "Each block should preserve div/code wrappers without leaking sibling content.",
    )
    assertTrue(
        blocks.none { block ->
          block.sourceText == "First paragraph" && block.originalHtml.contains("Fourth paragraph")
        },
        "Block HTML should not contain unrelated paragraph content.",
    )
  }

  @Test
  fun doesNotLeakFullCodeContentIntoEachSplitBlock() {
    val service = createService()
    val html =
        """
        <div class="submission-description user-submitted-links">
            <code class="bbcode bbcode_center">
                HEADER LINE<br><br>
                Link paragraph <a href="https://example.com/a" class="auto_link">https://example.com/a</a><br><br>
                <b class="bbcode bbcode_b">Bold paragraph</b><br><br>
                <i class="bbcode bbcode_i">Italic paragraph</i><br><br>
                FOOTER LINE
            </code>
        </div>
        """
            .trimIndent()

    val blocks = service.extractBlocks(html)

    assertTrue(blocks.size >= 5, "Expected at least five blocks, actual=${blocks.size}")
    assertTrue(blocks.any { it.sourceText.contains("HEADER LINE") })
    assertTrue(blocks.any { it.sourceText.contains("FOOTER LINE") })
    assertTrue(
        blocks.none { block ->
          block.originalHtml.contains("HEADER LINE") && block.originalHtml.contains("FOOTER LINE")
        },
        "A single block should not contain both first and last paragraph content.",
    )
    assertTrue(
        blocks.all { block -> "<code".toRegex().findAll(block.originalHtml).count() == 1 },
        "Each split block should have one code wrapper only.",
    )
  }

  @Test
  fun stripsLeadingAndTrailingSeparatorMarkersFromTranslatedBlock() = runTest {
    val service = createService(translationOutput = "%%\n译文内容\n%%")
    val results = mutableListOf<SubmissionDescriptionBlockResult>()

    service.translateBlocks(
        blocks = listOf(SubmissionDescriptionBlock("<p>Original</p>", "Original")),
        onBlockResult = { _, result -> results += result },
    )

    assertEquals(1, results.size)
    assertEquals(
        "译文内容",
        (results.single() as SubmissionDescriptionBlockResult.Success).translatedText,
    )
  }

  private fun createService(
      translationOutput: String? = null
  ): SubmissionDescriptionTranslationService {
    val randomSuffix = Random.nextLong().toString().replace('-', '0')
    val tempPath =
        "${FileSystem.SYSTEM_TEMPORARY_DIRECTORY}/fa2-test-$randomSuffix.preferences_pb".toPath()
    val keyValueStorage =
        KeyValueStorage(PreferenceDataStoreFactory.createWithPath(produceFile = { tempPath }))
    val settingsService = AppSettingsService(AppSettingsStorage(keyValueStorage))
    val translationPort =
        object : TranslationPort {
          override suspend fun translate(request: TranslationRequest): String =
              translationOutput ?: request.sourceText
        }

    return SubmissionDescriptionTranslationService(
        translationPort = translationPort,
        settingsService = settingsService,
    )
  }
}
