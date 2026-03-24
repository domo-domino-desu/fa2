package me.domino.fa2.ui.pages.user

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.JournalDetail
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.repository.JournalRepository
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizePageState

/** Journal 详情状态。 */
sealed interface JournalDetailUiState {
  /** 加载中。 */
  data object Loading : JournalDetailUiState

  /** 成功态。 */
  data class Success(
      /** 详情数据。 */
      val detail: JournalDetail
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
    private val repository: JournalRepository,
) : StateScreenModel<JournalDetailUiState>(JournalDetailUiState.Loading) {
  private val log = FaLog.withTag("JournalDetailScreenModel")

  init {
    load()
  }

  /** 加载日志详情。 */
  fun load() {
    log.i { "加载Journal详情 -> 开始(id=$journalId,url=${journalUrl ?: "-"})" }
    mutableState.value = JournalDetailUiState.Loading
    screenModelScope.launch {
      val result =
          if (journalId > 0) {
            repository.loadJournalDetail(journalId)
          } else {
            val fallbackUrl = journalUrl?.trim().orEmpty()
            if (fallbackUrl.isBlank()) {
              PageState.Error(IllegalStateException("Invalid journal id and url"))
            } else {
              repository.loadJournalDetailByUrl(fallbackUrl)
            }
          }

      mutableState.value =
          when (result) {
            is PageState.Success -> JournalDetailUiState.Success(result.data)
            PageState.CfChallenge -> JournalDetailUiState.Error("需要 Cloudflare 验证")
            is PageState.MatureBlocked -> JournalDetailUiState.Error(result.reason)
            is PageState.Error ->
                JournalDetailUiState.Error(result.exception.message ?: result.exception.toString())

            PageState.Loading -> JournalDetailUiState.Error("加载中断")
          }
      when (result) {
        is PageState.Success -> {
          log.i { "加载Journal详情 -> ${summarizePageState(result)}" }
        }

        PageState.CfChallenge -> {
          log.w { "加载Journal详情 -> Cloudflare验证" }
        }

        is PageState.MatureBlocked -> {
          log.w { "加载Journal详情 -> 受限(${result.reason})" }
        }

        is PageState.Error -> {
          log.e(result.exception) { "加载Journal详情 -> 失败" }
        }

        PageState.Loading -> {
          log.w { "加载Journal详情 -> 加载中断" }
        }
      }
    }
  }
}
