package me.domino.fa2.ui.pages.journal

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.JournalDetail
import me.domino.fa2.data.model.QueryState
import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.model.errorOrNull
import me.domino.fa2.data.model.mapData
import me.domino.fa2.data.model.summarizeResourceResult
import me.domino.fa2.data.remote.fa.journal.JournalRepository
import me.domino.fa2.data.remote.translation.SubmissionTextTranslationEngine
import me.domino.fa2.ui.components.html.TranslationSourceMode
import me.domino.fa2.ui.components.html.TranslationUiState
import me.domino.fa2.ui.components.html.canReuseTranslationResult
import me.domino.fa2.ui.components.html.markPendingBlocksAsFailed
import me.domino.fa2.ui.components.html.resolveTranslationState
import me.domino.fa2.ui.components.html.toPendingState
import me.domino.fa2.ui.components.html.variantOf
import me.domino.fa2.ui.components.html.withBlockResult
import me.domino.fa2.ui.components.html.withVariant
import me.domino.fa2.utils.logging.FaLog

data class JournalDetailUiState(
    val detail: JournalDetail,
    val bodyTranslationState: TranslationUiState,
)

/** Journal 详情状态模型。 */
class JournalDetailScreenModel(
    /** Journal ID。 */
    private val journalId: Int,
    /** Journal URL（兜底）。 */
    private val journalUrl: String?,
    /** Journal 仓储。 */
    private val repository: JournalRepository,
    /** 正文翻译编排服务。 */
    private val translationService: SubmissionTextTranslationEngine,
) : StateScreenModel<QueryState<JournalDetailUiState>>(QueryState.Loading()) {
  private val log = FaLog.withTag("JournalDetailScreenModel")
  private var translationJob: Job? = null
  private var queryJob: Job? = null

  init {
    load()
  }

  /** 加载日志详情。 */
  fun load() {
    log.i { "加载Journal详情 -> 开始(id=$journalId,url=${journalUrl ?: "-"})" }
    val previousSuccess = state.value.data
    if (journalId > 0) {
      if (queryJob?.isActive != true) {
        queryJob =
            screenModelScope.launch {
              repository.journal(journalId).collect { query ->
                mutableState.value = query.mapData { buildSuccessState(it, state.value.data) }
              }
            }
      } else {
        screenModelScope.launch { repository.refreshJournal(journalId) }
      }
      return
    }
    mutableState.value = QueryState.Loading(previousSuccess)
    screenModelScope.launch {
      val result = run {
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
                QueryState.Success(
                    buildSuccessState(detail = result.data, previous = previousSuccess)
                )
            else -> QueryState.Failure(result.errorOrNull()!!, previousSuccess)
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
    val current = state.value.data ?: return
    val translationState = current.bodyTranslationState
    if (translationState.showTranslation) {
      if (translationState.translating) return
      mutableState.value =
          QueryState.Success(
              current.copy(bodyTranslationState = translationState.copy(showTranslation = false))
          )
      return
    }
    if (translationState.sourceBlocks.isEmpty()) return

    val sourceMode = translationState.sourceMode
    val activeVariant = translationState.variantOf(sourceMode)
    if (activeVariant.translating) return
    if (activeVariant.canReuseTranslationResult()) {
      mutableState.value =
          QueryState.Success(
              current.copy(bodyTranslationState = translationState.copy(showTranslation = true))
          )
      return
    }

    translationJob?.cancel()
    val pendingVariant = activeVariant.toPendingState(sourceMode = sourceMode)
    val pendingState =
        translationState
            .withVariant(mode = sourceMode, variant = pendingVariant)
            .copy(showTranslation = true)
    mutableState.value = QueryState.Success(current.copy(bodyTranslationState = pendingState))

    translationJob =
        screenModelScope.launch {
          try {
            translationService.translateTexts(
                pendingVariant.sourceBlocks.map { it.sourceText },
            ) translation@{ index, result ->
              val latest = state.value.data ?: return@translation
              val latestTranslationState = latest.bodyTranslationState
              if (latestTranslationState.sourceKey != pendingState.sourceKey) {
                return@translation
              }
              mutableState.value =
                  QueryState.Success(
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
                  )
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (_: Throwable) {
            val latest = state.value.data ?: return@launch
            val latestTranslationState = latest.bodyTranslationState
            if (latestTranslationState.sourceKey == pendingState.sourceKey) {
              mutableState.value =
                  QueryState.Success(
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
                  )
            }
          } finally {
            val currentJob = currentCoroutineContext()[Job]
            if (translationJob === currentJob) {
              translationJob = null
            }
            val latest = state.value.data ?: return@launch
            val latestTranslationState = latest.bodyTranslationState
            if (latestTranslationState.sourceKey == pendingState.sourceKey) {
              mutableState.value =
                  QueryState.Success(
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
                  )
            }
          }
        }
  }

  fun toggleWrapTextCurrent() {
    val current = state.value.data ?: return
    val translationState = current.bodyTranslationState
    if (translationState.showTranslation || translationState.translating) return

    val nextMode =
        when (translationState.sourceMode) {
          TranslationSourceMode.RAW -> TranslationSourceMode.WRAPPED
          TranslationSourceMode.WRAPPED -> TranslationSourceMode.RAW
        }
    mutableState.value =
        QueryState.Success(
            current.copy(bodyTranslationState = translationState.copy(sourceMode = nextMode))
        )
  }

  private fun buildSuccessState(
      detail: JournalDetail,
      previous: JournalDetailUiState?,
  ): JournalDetailUiState {
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
    return JournalDetailUiState(
        detail = detail,
        bodyTranslationState = bodyTranslationState,
    )
  }
}
