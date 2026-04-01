package me.domino.fa2.ui.pages.submission

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.settings.TranslationProvider
import me.domino.fa2.domain.ocr.NormalizedImagePoint
import me.domino.fa2.ui.state.SubmissionDescriptionTranslationStatus

class SubmissionTranslationExportTest {
  @Test
  fun buildsExpectedSidecarFilesFromTranslatedSubmissionState() = runTest {
    val translationService = createTestSubmissionTranslationService()
    val descriptionState =
        translatedState(
            sourceHtml = "<p>first</p><p>second</p>",
            translatedTexts = listOf("FIRST", "SECOND"),
            translationService = translationService,
        )
    val attachmentState =
        translatedState(
            sourceHtml = "<p>attachment</p>",
            translatedTexts = listOf("ATTACHMENT"),
            translationService = translationService,
        )
    val imageSnapshot =
        SubmissionImageOcrTranslationExportSnapshot(
            imageUrl = "https://example.com/full.jpg",
            provider = TranslationProvider.MICROSOFT,
            blocks =
                listOf(
                    SubmissionImageOcrBlockUiState(
                        id = "ocr-block-0",
                        points =
                            listOf(
                                NormalizedImagePoint(0.1f, 0.2f),
                                NormalizedImagePoint(0.3f, 0.2f),
                                NormalizedImagePoint(0.3f, 0.4f),
                                NormalizedImagePoint(0.1f, 0.4f),
                            ),
                        originalText = "hello",
                        translatedText = "HELLO",
                        translationStatus = SubmissionImageOcrTranslationStatus.SUCCESS,
                    )
                ),
        )

    val files =
        buildSubmissionTranslationSidecarFiles(
            submissionId = 42,
            descriptionTranslationState = descriptionState,
            attachmentTranslationState = attachmentState,
            imageOcrSnapshot = imageSnapshot,
        )

    assertEquals(
        listOf(
            "42-translate-img.json",
            "42-translate-desc.txt",
            "42-translate-file.txt",
        ),
        files.map { it.fileName },
    )
    val imageJson = files.first { it.fileName.endsWith("-img.json") }.content
    assertTrue(imageJson.contains("\"provider\": \"microsoft\""))
    assertTrue(imageJson.contains("\"translatedText\": \"HELLO\""))
    val descriptionText = files.first { it.fileName.endsWith("-desc.txt") }.content
    assertEquals(
        "Original:\nfirst\nTranslated:\nFIRST\n<split>\nOriginal:\nsecond\nTranslated:\nSECOND",
        descriptionText,
    )
    val attachmentText = files.first { it.fileName.endsWith("-file.txt") }.content
    assertEquals("Original:\nattachment\nTranslated:\nATTACHMENT", attachmentText)
  }

  @Test
  fun skipsMissingOrFailedTranslationOutputs() = runTest {
    val translationService = createTestSubmissionTranslationService()
    val failedDescription =
        translatedState(
            sourceHtml = "<p>hello</p>",
            translatedTexts = emptyList(),
            translationService = translationService,
            status = SubmissionDescriptionTranslationStatus.FAILURE,
        )

    val files =
        buildSubmissionTranslationSidecarFiles(
            submissionId = 7,
            descriptionTranslationState = failedDescription,
            attachmentTranslationState = null,
            imageOcrSnapshot =
                SubmissionImageOcrTranslationExportSnapshot(
                    imageUrl = "https://example.com/full.jpg",
                    provider = TranslationProvider.GOOGLE,
                    blocks =
                        listOf(
                            SubmissionImageOcrBlockUiState(
                                id = "ocr-block-0",
                                points = emptyList(),
                                originalText = "hello",
                                translatedText = null,
                                translationStatus = SubmissionImageOcrTranslationStatus.FAILURE,
                            )
                        ),
                ),
        )

    assertTrue(files.isEmpty())
  }
}

private fun translatedState(
    sourceHtml: String,
    translatedTexts: List<String>,
    translationService:
        me.domino.fa2.application.translation.SubmissionDescriptionTranslationService,
    status: SubmissionDescriptionTranslationStatus = SubmissionDescriptionTranslationStatus.SUCCESS,
): SubmissionTranslationUiState {
  val state = resolveTranslationState(sourceHtml, null, null, translationService)
  val sourceBlocks = state.rawVariant.sourceBlocks
  val translatedBlocks =
      sourceBlocks.mapIndexed { index, block ->
        block.toDisplayBlock(
            translated = translatedTexts.getOrNull(index),
            status = status,
            useWrappedOriginalText = false,
        )
      }
  return state.copy(
      showTranslation = true,
      rawVariant =
          state.rawVariant.copy(
              blocks = translatedBlocks,
              translating = false,
              hasTriggered = true,
          ),
  )
}
