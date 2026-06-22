package me.domino.fa2.domain.ocr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.fa.media.ImageBytesSource

class SubmissionImageOcrServiceTest {
  @Test
  fun returnsPlatformBlocksWithoutApplyingAdditionalMerge() = runTest {
    val expected =
        ImageOcrResult(
            blocks =
                listOf(
                    RecognizedTextBlock(
                        text = "Hello",
                        points =
                            listOf(
                                NormalizedImagePoint(0.10f, 0.10f),
                                NormalizedImagePoint(0.20f, 0.10f),
                                NormalizedImagePoint(0.20f, 0.18f),
                                NormalizedImagePoint(0.10f, 0.18f),
                            ),
                    ),
                    RecognizedTextBlock(
                        text = "there",
                        points =
                            listOf(
                                NormalizedImagePoint(0.11f, 0.22f),
                                NormalizedImagePoint(0.21f, 0.22f),
                                NormalizedImagePoint(0.21f, 0.30f),
                                NormalizedImagePoint(0.11f, 0.30f),
                            ),
                    ),
                )
        )
    val service =
        DefaultSubmissionImageOcrService(
            imageBytesSource = ImageBytesSource { byteArrayOf(1, 2, 3) },
            recognitionPort =
                object : ImageTextRecognitionPort {
                  override suspend fun recognize(imageBytes: ByteArray): ImageOcrResult {
                    assertEquals(byteArrayOf(1, 2, 3).toList(), imageBytes.toList())
                    return expected
                  }
                },
        )

    val result = service.recognize("https://example.com/image.png")

    assertEquals(expected, result)
  }

  @Test
  fun propagatesImageSourceFailure() = runTest {
    val service =
        DefaultSubmissionImageOcrService(
            imageBytesSource = ImageBytesSource { error("Image download returned empty payload") },
            recognitionPort =
                object : ImageTextRecognitionPort {
                  override suspend fun recognize(imageBytes: ByteArray): ImageOcrResult =
                      ImageOcrResult(emptyList())
                },
        )

    assertFailsWith<IllegalStateException> { service.recognize("https://example.com/empty.png") }
  }
}
