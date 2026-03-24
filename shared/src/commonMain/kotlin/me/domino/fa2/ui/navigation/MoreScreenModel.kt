package me.domino.fa2.ui.navigation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.launch
import me.domino.fa2.data.repository.ActivityHistoryRepository
import me.domino.fa2.data.repository.AuthRepository
import me.domino.fa2.util.logging.FaLog

/**
 * More 页面状态模型。
 */
class MoreScreenModel(
    /** 当前用户名。 */
    private val username: String,
    /** 认证仓储。 */
    private val authRepository: AuthRepository,
    /** 历史记录仓储。 */
    private val historyRepository: ActivityHistoryRepository,
) : StateScreenModel<MoreUiState>(MoreUiState.Loading) {
    private val log = FaLog.withTag("MoreScreenModel")

    init {
        load()
    }

    /**
     * 刷新登录状态展示。
     */
    fun load() {
        log.i { "加载More -> 开始" }
        screenModelScope.launch {
            runCatching {
                val hasCookie = authRepository.loadCookieHeader().isNotBlank()
                val submissionHistoryCount = historyRepository.loadSubmissionHistory().size
                val searchHistoryCount = historyRepository.loadSearchHistory().size
                mutableState.value = MoreUiState.Ready(
                    username = username,
                    hasCookie = hasCookie,
                    submissionHistoryCount = submissionHistoryCount,
                    searchHistoryCount = searchHistoryCount,
                    loggingOut = false,
                    errorMessage = null,
                    loggedOut = false,
                )
                log.i {
                    "加载More -> 成功(cookie=${if (hasCookie) "已设置" else "空"},history=$submissionHistoryCount/$searchHistoryCount)"
                }
            }.onFailure { error ->
                log.e(error) { "加载More -> 失败" }
            }
        }
    }

    /**
     * 退出登录并清理会话。
     */
    fun logout() {
        val snapshot = state.value
        if (snapshot !is MoreUiState.Ready) return
        if (snapshot.loggingOut) return
        log.i { "退出登录 -> 开始" }

        screenModelScope.launch {
            mutableState.value = snapshot.copy(
                loggingOut = true,
                errorMessage = null,
            )
            runCatching {
                authRepository.clearSession()
            }.onSuccess {
                mutableState.value = snapshot.copy(
                    hasCookie = false,
                    submissionHistoryCount = snapshot.submissionHistoryCount,
                    searchHistoryCount = snapshot.searchHistoryCount,
                    loggingOut = false,
                    errorMessage = null,
                    loggedOut = true,
                )
                log.i { "退出登录 -> 成功" }
            }.onFailure { throwable ->
                mutableState.value = snapshot.copy(
                    loggingOut = false,
                    errorMessage = throwable.message ?: "退出登录失败",
                    loggedOut = false,
                )
                log.e(throwable) { "退出登录 -> 失败" }
            }
        }
    }
}

/**
 * More 页面状态。
 */
sealed interface MoreUiState {
    /** 首次加载中。 */
    data object Loading : MoreUiState

    /**
     * 可展示状态。
     * @property username 当前用户名。
     * @property hasCookie 当前是否仍有 cookie 会话。
     * @property submissionHistoryCount 投稿历史数量。
     * @property searchHistoryCount 搜索历史数量。
     * @property loggingOut 是否正在退出。
     * @property errorMessage 错误提示。
     * @property loggedOut 是否已完成退出。
     */
    data class Ready(
        /** 当前用户名。 */
        val username: String,
        /** 当前是否有 cookie。 */
        val hasCookie: Boolean,
        /** 投稿历史数量。 */
        val submissionHistoryCount: Int,
        /** 搜索历史数量。 */
        val searchHistoryCount: Int,
        /** 是否正在执行退出。 */
        val loggingOut: Boolean,
        /** 错误信息。 */
        val errorMessage: String?,
        /** 是否已经退出成功。 */
        val loggedOut: Boolean,
    ) : MoreUiState
}
