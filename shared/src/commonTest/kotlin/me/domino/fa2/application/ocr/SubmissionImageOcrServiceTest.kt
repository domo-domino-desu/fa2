package me.domino.fa2.application.ocr

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import me.domino.fa2.domain.ocr.ImageOcrResult
import me.domino.fa2.domain.ocr.ImageTextRecognitionPort
import me.domino.fa2.domain.ocr.NormalizedImagePoint
import me.domino.fa2.domain.ocr.RecognizedTextBlock

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
        RemoteSubmissionImageOcrService(
            client = mockImageClient(byteArrayOf(1, 2, 3)),
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
  fun rejectsEmptyDownloadedPayload() = runTest {
    val service =
        RemoteSubmissionImageOcrService(
            client = mockImageClient(byteArrayOf()),
            recognitionPort =
                object : ImageTextRecognitionPort {
                  override suspend fun recognize(imageBytes: ByteArray): ImageOcrResult =
                      ImageOcrResult(emptyList())
                },
        )

    assertFailsWith<IllegalStateException> { service.recognize("https://example.com/empty.png") }
  }
}

private fun mockImageClient(imageBytes: ByteArray): HttpClient =
    HttpClient(
        MockEngine { _ ->
          respond(
              content = imageBytes,
              status = HttpStatusCode.OK,
              headers = headersOf(HttpHeaders.ContentType, "image/png"),
          )
        }
    )
