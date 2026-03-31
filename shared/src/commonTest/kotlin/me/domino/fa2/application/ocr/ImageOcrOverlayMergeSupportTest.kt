package me.domino.fa2.application.ocr

import kotlin.test.Test
import kotlin.test.assertEquals
import me.domino.fa2.domain.ocr.NormalizedImagePoint
import me.domino.fa2.domain.ocr.RecognizedTextBlock

class ImageOcrOverlayMergeSupportTest {
  @Test
  fun collectsEveryBlockIntersectingSelectedRegion() {
    val blocks =
        listOf(
            ocrBlock("hello", 0.10f, 0.10f, 0.20f, 0.18f),
            ocrBlock("there", 0.22f, 0.11f, 0.32f, 0.19f),
            ocrBlock("friend", 0.12f, 0.21f, 0.30f, 0.29f),
            ocrBlock("outside", 0.70f, 0.70f, 0.82f, 0.80f),
        )

    val selected =
        collectRecognizedTextBlocksIntersectingRegion(
            blocks = blocks,
            regionPoints =
                listOf(
                    NormalizedImagePoint(0.09f, 0.09f),
                    NormalizedImagePoint(0.33f, 0.09f),
                    NormalizedImagePoint(0.33f, 0.30f),
                    NormalizedImagePoint(0.09f, 0.30f),
                ),
        )

    assertEquals(listOf("hello", "there", "friend"), selected.map { it.text })
  }

  @Test
  fun mergesSelectedBlocksUsingReadingOrderAndUnionBounds() {
    val merged =
        mergeRecognizedTextBlocksForOverlay(
            listOf(
                ocrBlock("hello", 0.10f, 0.10f, 0.20f, 0.18f),
                ocrBlock("there", 0.22f, 0.11f, 0.32f, 0.19f),
                ocrBlock("friend", 0.12f, 0.21f, 0.30f, 0.29f),
            )
        )

    assertEquals("hello there friend", merged?.text)
    assertEquals(
        listOf(
            NormalizedImagePoint(0.10f, 0.10f),
            NormalizedImagePoint(0.32f, 0.10f),
            NormalizedImagePoint(0.32f, 0.29f),
            NormalizedImagePoint(0.10f, 0.29f),
        ),
        merged?.points,
    )
  }
}

private fun ocrBlock(
    text: String,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
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
    )
