package me.domino.fa2.ui.pages.submission

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.domino.fa2.data.settings.TranslationProvider
import me.domino.fa2.ui.state.SubmissionDescriptionTranslationStatus

data class SubmissionImageOcrTranslationExportSnapshot(
    val imageUrl: String,
    val provider: TranslationProvider,
    val blocks: List<SubmissionImageOcrBlockUiState>,
)

data class SubmissionTranslationSidecarFile(
    val fileName: String,
    val content: String,
)

fun buildSubmissionTranslationSidecarFiles(
    submissionId: Int,
    descriptionTranslationState: SubmissionTranslationUiState?,
    attachmentTranslationState: SubmissionTranslationUiState?,
    imageOcrSnapshot: SubmissionImageOcrTranslationExportSnapshot?,
): List<SubmissionTranslationSidecarFile> {
  val files = mutableListOf<SubmissionTranslationSidecarFile>()
  buildImageOcrTranslationSidecarFile(submissionId, imageOcrSnapshot)?.let(files::add)
  buildTextTranslationSidecarFile(
          submissionId = submissionId,
          suffix = "desc",
          translationState = descriptionTranslationState,
      )
      ?.let(files::add)
  buildTextTranslationSidecarFile(
          submissionId = submissionId,
          suffix = "file",
          translationState = attachmentTranslationState,
      )
      ?.let(files::add)
  return files
}

private fun buildImageOcrTranslationSidecarFile(
    submissionId: Int,
    snapshot: SubmissionImageOcrTranslationExportSnapshot?,
): SubmissionTranslationSidecarFile? {
  val currentSnapshot = snapshot ?: return null
  val translatedBlocks =
      currentSnapshot.blocks.filter { block ->
        block.translationStatus == SubmissionImageOcrTranslationStatus.SUCCESS &&
            block.translatedText?.isNotBlank() == true
      }
  if (translatedBlocks.isEmpty()) return null
  val payload =
      SubmissionImageOcrTranslationExportPayload(
          submissionId = submissionId,
          imageUrl = currentSnapshot.imageUrl,
          provider = currentSnapshot.provider.persistedValue,
          blocks =
              translatedBlocks.map { block ->
                SubmissionImageOcrTranslationExportBlock(
                    id = block.id,
                    originalText = block.originalText,
                    translatedText = block.translatedText.orEmpty(),
                    translationStatus = block.translationStatus.name,
                    points =
                        block.points.map { point -> SubmissionImagePointExport(point.x, point.y) },
                )
              },
      )
  return SubmissionTranslationSidecarFile(
      fileName = "$submissionId-translate-img.json",
      content = submissionTranslationExportJson.encodeToString(payload),
  )
}

private fun buildTextTranslationSidecarFile(
    submissionId: Int,
    suffix: String,
    translationState: SubmissionTranslationUiState?,
): SubmissionTranslationSidecarFile? {
  val currentState = translationState ?: return null
  val exportBlocks =
      currentState.successfulExportBlocks().map { block ->
        buildString {
          append("Original:\n")
          append(block.originalText)
          append("\nTranslated:\n")
          append(block.translatedText)
        }
      }
  if (exportBlocks.isEmpty()) return null
  return SubmissionTranslationSidecarFile(
      fileName = "$submissionId-translate-$suffix.txt",
      content = exportBlocks.joinToString(separator = "\n<split>\n"),
  )
}

private fun SubmissionTranslationUiState.successfulExportBlocks():
    List<SubmissionTranslationExportBlock> {
  val sourceBlocks = variantOf(sourceMode).sourceBlocks
  val resultBlocks = variantOf(sourceMode).blocks
  return resultBlocks.mapIndexedNotNull { index, block ->
    val translatedText = block.translated?.trim().orEmpty()
    if (
        block.status != SubmissionDescriptionTranslationStatus.SUCCESS || translatedText.isBlank()
    ) {
      null
    } else {
      SubmissionTranslationExportBlock(
          originalText = sourceBlocks.getOrNull(index)?.sourceText?.trim().orEmpty(),
          translatedText = translatedText,
      )
    }
  }
}

private data class SubmissionTranslationExportBlock(
    val originalText: String,
    val translatedText: String,
)

@Serializable
private data class SubmissionImageOcrTranslationExportPayload(
    val submissionId: Int,
    val imageUrl: String,
    val provider: String,
    val blocks: List<SubmissionImageOcrTranslationExportBlock>,
)

@Serializable
private data class SubmissionImageOcrTranslationExportBlock(
    val id: String,
    val originalText: String,
    val translatedText: String,
    val translationStatus: String,
    val points: List<SubmissionImagePointExport>,
)

@Serializable
private data class SubmissionImagePointExport(
    val x: Float,
    val y: Float,
)

private val submissionTranslationExportJson = Json { prettyPrint = true }
