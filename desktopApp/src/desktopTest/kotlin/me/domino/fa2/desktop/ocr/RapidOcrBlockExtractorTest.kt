package me.domino.fa2.desktop.ocr

import io.github.hzkitty.entity.RecResult
import kotlin.test.Test
import kotlin.test.assertEquals
import org.opencv.core.Point

/** RapidOcrBlockExtractor 的单元测试。 */
class RapidOcrBlockExtractorTest {
  /** 验证多个 RapidOCR 片段被保留为独立的文字块。 */
  @Test
  fun keepsRapidOcrFragmentsAsSeparateBlocks() {
    val blocks =
        rapidOcrResultToBlocks(
            records =
                listOf(
                    recResult("Hello", 10.0, 10.0, 22.0, 17.0),
                    recResult("there", 11.0, 19.0, 23.0, 26.0),
                    recResult("friend", 9.0, 28.0, 25.0, 35.0),
                ),
            width = 100,
            height = 100,
        )

    assertEquals(3, blocks.size)
    assertEquals(listOf("Hello", "there", "friend"), blocks.map { it.text })
    assertEquals(listOf(0.9f, 0.9f, 0.9f), blocks.map { it.confidence })
  }

  /** 验证空白内容和点数不足的 RapidOCR 片段被正确过滤。 */
  @Test
  fun filtersBlankAndInvalidRapidOcrFragments() {
    val blocks =
        rapidOcrResultToBlocks(
            records =
                listOf(
                    recResult(" ", 10.0, 10.0, 20.0, 20.0),
                    RecResult(
                        arrayOf(Point(10.0, 10.0), Point(20.0, 10.0), Point(20.0, 20.0)),
                        "bad",
                        0.4f,
                        null,
                    ),
                    recResult("kept", 30.0, 30.0, 60.0, 50.0, confidence = 0.7f),
                ),
            width = 100,
            height = 100,
        )

    assertEquals(1, blocks.size)
    assertEquals("kept", blocks.single().text)
    assertEquals(0.7f, blocks.single().confidence)
  }
}

/** 构造用于测试的矩形边界 RapidOCR 识别结果。 */
private fun recResult(
    text: String,
    left: Double,
    top: Double,
    right: Double,
    bottom: Double,
    confidence: Float? = 0.9f,
): RecResult =
    RecResult(
        arrayOf(
            Point(left, top),
            Point(right, top),
            Point(right, bottom),
            Point(left, bottom),
        ),
        text,
        confidence ?: 0f,
        null,
    )
