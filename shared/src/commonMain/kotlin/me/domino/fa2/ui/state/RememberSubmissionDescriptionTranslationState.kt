package me.domino.fa2.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import me.domino.fa2.application.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.domain.translation.SubmissionDescriptionBlock
import me.domino.fa2.domain.translation.SubmissionDescriptionBlockResult

/** 投稿描述翻译 UI 状态控制器。 */
@Composable
fun rememberSubmissionDescriptionTranslationState(
    descriptionHtml: String,
    service: SubmissionDescriptionTranslationService,
): SubmissionDescriptionTranslationController {
  val scope = rememberCoroutineScope()
  val sourceBlocks = remember(descriptionHtml) { service.extractBlocks(descriptionHtml) }

  var blockStates by
      remember(descriptionHtml) {
        mutableStateOf(
            sourceBlocks.map { block ->
              SubmissionDescriptionBlockState(
                  block = block,
                  translated = null,
                  status = SubmissionDescriptionTranslationStatus.IDLE,
              )
            }
        )
      }

  var translating by remember(descriptionHtml) { mutableStateOf(false) }
  var hasTriggered by remember(descriptionHtml) { mutableStateOf(false) }
  var runningJob by remember(descriptionHtml) { mutableStateOf<Job?>(null) }

  DisposableEffect(descriptionHtml) { onDispose { runningJob?.cancel() } }

  val translateAction: () -> Unit = translateAction@{
    if (translating || sourceBlocks.isEmpty()) return@translateAction

    runningJob?.cancel()
    translating = true
    hasTriggered = true
    blockStates =
        sourceBlocks.map { block ->
          SubmissionDescriptionBlockState(
              block = block,
              translated = null,
              status = SubmissionDescriptionTranslationStatus.PENDING,
          )
        }

    runningJob =
        scope.launch {
          try {
            service.translateBlocks(sourceBlocks) { index, result ->
              blockStates =
                  blockStates.mapIndexed { currentIndex, state ->
                    if (currentIndex != index) return@mapIndexed state

                    when (result) {
                      is SubmissionDescriptionBlockResult.Success ->
                          state.copy(
                              translated = result.translatedText,
                              status = SubmissionDescriptionTranslationStatus.SUCCESS,
                          )

                      SubmissionDescriptionBlockResult.EmptyResult ->
                          state.copy(
                              translated = null,
                              status = SubmissionDescriptionTranslationStatus.EMPTY,
                          )

                      is SubmissionDescriptionBlockResult.Failure ->
                          state.copy(
                              translated = null,
                              status = SubmissionDescriptionTranslationStatus.FAILURE,
                          )
                    }
                  }
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (_: Throwable) {
            blockStates =
                blockStates.map { state ->
                  if (state.status == SubmissionDescriptionTranslationStatus.PENDING) {
                    state.copy(status = SubmissionDescriptionTranslationStatus.FAILURE)
                  } else {
                    state
                  }
                }
          } finally {
            val currentJob = currentCoroutineContext()[Job]
            if (runningJob == currentJob) {
              translating = false
              runningJob = null
            }
          }
        }
  }

  val displayBlocks =
      remember(blockStates, hasTriggered) {
        if (!hasTriggered) {
          blockStates.map { state ->
            SubmissionDescriptionDisplayBlock(
                originalHtml = state.block.originalHtml,
                translated = null,
                status = SubmissionDescriptionTranslationStatus.IDLE,
            )
          }
        } else {
          blockStates.map { state ->
            SubmissionDescriptionDisplayBlock(
                originalHtml = state.block.originalHtml,
                translated = state.translated,
                status = state.status,
            )
          }
        }
      }

  return remember(displayBlocks, translating) {
    SubmissionDescriptionTranslationController(
        blocks = displayBlocks,
        translating = translating,
        translate = translateAction,
    )
  }
}

data class SubmissionDescriptionTranslationController(
    val blocks: List<SubmissionDescriptionDisplayBlock>,
    val translating: Boolean,
    val translate: () -> Unit,
)

data class SubmissionDescriptionDisplayBlock(
    val originalHtml: String,
    val originalText: String? = null,
    val translated: String?,
    val status: SubmissionDescriptionTranslationStatus,
)

private data class SubmissionDescriptionBlockState(
    val block: SubmissionDescriptionBlock,
    val translated: String?,
    val status: SubmissionDescriptionTranslationStatus,
)

enum class SubmissionDescriptionTranslationStatus {
  IDLE,
  PENDING,
  SUCCESS,
  EMPTY,
  FAILURE,
}
