package me.domino.fa2.application.ocr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import me.domino.fa2.domain.ocr.NormalizedImagePoint
import me.domino.fa2.domain.ocr.RecognizedTextBlock

class ComicDialogueOcrPostProcessorTest {
  @Test
  fun mergesVerticallyStackedDialogueFragmentsIntoSingleBlock() {
    val merged =
        mergeComicDialogueBlocks(
            listOf(
                ocrBlock("Hello", left = 0.10f, top = 0.10f, right = 0.22f, bottom = 0.17f),
                ocrBlock("there", left = 0.11f, top = 0.19f, right = 0.23f, bottom = 0.26f),
                ocrBlock("friend", left = 0.09f, top = 0.28f, right = 0.25f, bottom = 0.35f),
            )
        )

    assertEquals(1, merged.size)
    assertEquals("Hello there friend", merged.single().text)
    assertEquals(
        listOf(
            NormalizedImagePoint(0.09f, 0.10f),
            NormalizedImagePoint(0.25f, 0.10f),
            NormalizedImagePoint(0.25f, 0.35f),
            NormalizedImagePoint(0.09f, 0.35f),
        ),
        merged.single().points,
    )
    assertEquals(0.9f, merged.single().confidence)
  }

  @Test
  fun mergesTwoColumnsInsideSameDialogueCluster() {
    val merged =
        mergeComicDialogueBlocks(
            listOf(
                ocrBlock("What", left = 0.10f, top = 0.10f, right = 0.18f, bottom = 0.16f),
                ocrBlock("is", left = 0.22f, top = 0.11f, right = 0.27f, bottom = 0.17f),
                ocrBlock("this", left = 0.11f, top = 0.20f, right = 0.19f, bottom = 0.26f),
                ocrBlock("place", left = 0.21f, top = 0.21f, right = 0.30f, bottom = 0.27f),
            )
        )

    assertEquals(1, merged.size)
    assertEquals("What is this place", merged.single().text)
  }

  @Test
  fun keepsDistantSpeechBubblesSeparate() {
    val merged =
        mergeComicDialogueBlocks(
            listOf(
                ocrBlock("Left", left = 0.10f, top = 0.10f, right = 0.18f, bottom = 0.16f),
                ocrBlock("bubble", left = 0.11f, top = 0.19f, right = 0.21f, bottom = 0.25f),
                ocrBlock("Right", left = 0.72f, top = 0.12f, right = 0.81f, bottom = 0.18f),
            )
        )

    assertEquals(2, merged.size)
    assertEquals(listOf("Left bubble", "Right"), merged.map { it.text })
  }

  @Test
  fun keepsTitleAndSmallDialogueSeparateWhenSizeDiffIsExtreme() {
    val merged =
        mergeComicDialogueBlocks(
            listOf(
                ocrBlock("TITLE", left = 0.08f, top = 0.08f, right = 0.56f, bottom = 0.24f),
                ocrBlock("hi", left = 0.18f, top = 0.30f, right = 0.24f, bottom = 0.35f),
                ocrBlock("there", left = 0.19f, top = 0.37f, right = 0.29f, bottom = 0.42f),
            )
        )

    assertEquals(2, merged.size)
    assertEquals(listOf("TITLE", "hi there"), merged.map { it.text })
  }

  @Test
  fun singleBlockRemainsUnchanged() {
    val source =
        ocrBlock(
            text = "solo",
            left = 0.15f,
            top = 0.20f,
            right = 0.28f,
            bottom = 0.30f,
            confidence = null,
        )

    val merged = mergeComicDialogueBlocks(listOf(source))

    assertEquals(listOf(source), merged)
    assertNull(merged.single().confidence)
  }
}

private fun ocrBlock(
    text: String,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    confidence: Float? = 0.9f,
): RecognizedTextBlock =
    RecognizedTextBlock(
        text = text,
        points =
            listOf(
                NormalizedImagePoint(left, top),
                NormalizedImagePoint(right, top),
                NormalizedImagePoint(right, bottom),
                NormalizedImagePoint(left, bottom),
            ),
        confidence = confidence,
    )
