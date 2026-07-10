package me.domino.fa2.ui.pages.journal

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import fa2.shared.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.JournalDetail
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.summarizeResourceResult
import me.domino.fa2.data.remote.fa.journal.JournalDetailRepository
import me.domino.fa2.domain.translation.SubmissionDescriptionTranslationService
import me.domino.fa2.ui.components.html.TranslationSourceMode
import me.domino.fa2.ui.components.html.TranslationUiState
import me.domino.fa2.ui.components.html.canReuseTranslationResult
import me.domino.fa2.ui.components.html.markPendingBlocksAsFailed
import me.domino.fa2.ui.components.html.resolveTranslationState
import me.domino.fa2.ui.components.html.toPendingState
import me.domino.fa2.ui.components.html.variantOf
import me.domino.fa2.ui.components.html.withBlockResult
import me.domino.fa2.ui.components.html.withVariant
import me.domino.fa2.ui.i18n.appString
import me.domino.fa2.utils.logging.FaLog

/** Journal 详情状态。 */
sealed interface JournalDetailUiState {
  /** 加载中。 */
  data object Loading : JournalDetailUiState

  /** 成功态。 */
  data class Success(
      /** 详情数据。 */
      val detail: JournalDetail,
      /** 正文翻译状态。 */
      val bodyTranslationState: TranslationUiState,
  ) : JournalDetailUiState

  /** 错误态。 */
  data class Error(
      /** 错误文案。 */
      val message: String
  ) : JournalDetailUiState
}

/** Journal 详情状态模型。 */
class JournalDetailScreenModel(
    /** Journal ID。 */
    private val journalId: Int,
    /** Journal URL（兜底）。 */
    private val journalUrl: String?,
    /** Journal 仓储。 */
    private val repository: JournalDetailRepository,
    /** 正文翻译编排服务。 */
    private val translationService: SubmissionDescriptionTranslationService,
) : StateScreenModel<JournalDetailUiState>(JournalDetailUiState.Loading) {
  private val log = FaLog.withTag("JournalDetailScreenModel")
  private var translationJob: Job? = null

  init {
    load()
  }

  /** 加载日志详情。 */
  fun load() {
    log.i { "加载Journal详情 -> 开始(id=$journalId,url=${journalUrl ?: "-"})" }
    val previousSuccess = state.value as? JournalDetailUiState.Success
    mutableState.value = JournalDetailUiState.Loading
    screenModelScope.launch {
      val result =
          if (journalId > 0) {
            repository.loadJournalDetail(journalId)
          } else {
            val fallbackUrl = journalUrl?.trim().orEmpty()
            if (fallbackUrl.isBlank()) {
              ResourceResult.Error(IllegalStateException("Invalid journal id and url"))
            } else {
              repository.loadJournalDetailByUrl(fallbackUrl)
            }
          }

      mutableState.value =
          when (result) {
            is ResourceResult.Success ->
                buildSuccessState(detail = result.data, previous = previousSuccess)
            is ResourceResult.AuthRequired -> JournalDetailUiState.Error(result.message)
            ResourceResult.CfChallenge ->
                JournalDetailUiState.Error(appString(Res.string.cloudflare_challenge_title))
            is ResourceResult.MatureBlocked -> JournalDetailUiState.Error(result.reason)
            is ResourceResult.Error ->
                JournalDetailUiState.Error(result.exception.message ?: result.exception.toString())
          }
      when (result) {
        is ResourceResult.Success -> {
          log.i { "加载Journal详情 -> ${summarizeResourceResult(result)}" }
        }

        is ResourceResult.AuthRequired -> {
          log.w { "加载Journal详情 -> 需要重新登录" }
        }

        ResourceResult.CfChallenge -> {
          log.w { "加载Journal详情 -> Cloudflare验证" }
        }

        is ResourceResult.MatureBlocked -> {
          log.w { "加载Journal详情 -> 受限(${result.reason})" }
        }

        is ResourceResult.Error -> {
          log.e(result.exception) { "加载Journal详情 -> 失败" }
        }
      }
    }
  }

  fun translateCurrent() {
    val current = state.value as? JournalDetailUiState.Success ?: return
    val translationState = current.bodyTranslationState
    if (translationState.showTranslation) {
      if (translationState.translating) return
      mutableState.value =
          current.copy(bodyTranslationState = translationState.copy(showTranslation = false))
      return
    }
    if (translationState.sourceBlocks.isEmpty()) return

    val sourceMode = translationState.sourceMode
    val activeVariant = translationState.variantOf(sourceMode)
    if (activeVariant.translating) return
    if (activeVariant.canReuseTranslationResult()) {
      mutableState.value =
          current.copy(bodyTranslationState = translationState.copy(showTranslation = true))
      return
    }

    translationJob?.cancel()
    val pendingVariant = activeVariant.toPendingState(sourceMode = sourceMode)
    val pendingState =
        translationState
            .withVariant(mode = sourceMode, variant = pendingVariant)
            .copy(showTranslation = true)
    mutableState.value = current.copy(bodyTranslationState = pendingState)

    translationJob =
        screenModelScope.launch {
          try {
            translationService.translateBlocks(pendingVariant.sourceBlocks) { index, result ->
              val latest = state.value as? JournalDetailUiState.Success ?: return@translateBlocks
              val latestTranslationState = latest.bodyTranslationState
              if (latestTranslationState.sourceKey != pendingState.sourceKey) {
                return@translateBlocks
              }
              mutableState.value =
                  latest.copy(
                      bodyTranslationState =
                          latestTranslationState.withVariant(
                              mode = sourceMode,
                              variant =
                                  latestTranslationState
                                      .variantOf(sourceMode)
                                      .withBlockResult(index = index, result = result),
                          )
                  )
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (_: Throwable) {
            val latest = state.value as? JournalDetailUiState.Success ?: return@launch
            val latestTranslationState = latest.bodyTranslationState
            if (latestTranslationState.sourceKey == pendingState.sourceKey) {
              mutableState.value =
                  latest.copy(
                      bodyTranslationState =
                          latestTranslationState.withVariant(
                              mode = sourceMode,
                              variant =
                                  latestTranslationState
                                      .variantOf(sourceMode)
                                      .markPendingBlocksAsFailed(),
                          )
                  )
            }
          } finally {
            val currentJob = currentCoroutineContext()[Job]
            if (translationJob === currentJob) {
              translationJob = null
            }
            val latest = state.value as? JournalDetailUiState.Success ?: return@launch
            val latestTranslationState = latest.bodyTranslationState
            if (latestTranslationState.sourceKey == pendingState.sourceKey) {
              mutableState.value =
                  latest.copy(
                      bodyTranslationState =
                          latestTranslationState.withVariant(
                              mode = sourceMode,
                              variant =
                                  latestTranslationState
                                      .variantOf(sourceMode)
                                      .copy(translating = false),
                          )
                  )
            }
          }
        }
  }

  fun toggleWrapTextCurrent() {
    val current = state.value as? JournalDetailUiState.Success ?: return
    val translationState = current.bodyTranslationState
    if (translationState.showTranslation || translationState.translating) return

    val nextMode =
        when (translationState.sourceMode) {
          TranslationSourceMode.RAW -> TranslationSourceMode.WRAPPED
          TranslationSourceMode.WRAPPED -> TranslationSourceMode.RAW
        }
    mutableState.value =
        current.copy(bodyTranslationState = translationState.copy(sourceMode = nextMode))
  }

  private fun buildSuccessState(
      detail: JournalDetail,
      previous: JournalDetailUiState.Success?,
  ): JournalDetailUiState.Success {
    val bodyTranslationState =
        resolveTranslationState(
            sourceHtml = detail.bodyHtml,
            sourceFileName = null,
            previous = previous?.bodyTranslationState,
            translationService = translationService,
        )
    if (previous?.bodyTranslationState?.sourceKey != bodyTranslationState.sourceKey) {
      translationJob?.cancel()
      translationJob = null
    }
    return JournalDetailUiState.Success(
        detail = detail,
        bodyTranslationState = bodyTranslationState,
    )
  }
}
