package me.domino.fa2.ui.screen.auth

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.domino.fa2.data.model.AuthProbeResult
import me.domino.fa2.data.repository.AuthRepository
import me.domino.fa2.util.logging.FaLog

/**
 * 登录页面状态模型。
 */
class AuthScreenModel(
    /** 认证仓储。 */
    private val authRepository: AuthRepository,
) : StateScreenModel<AuthUiState>(AuthUiState.Loading) {
    private val log = FaLog.withTag("AuthScreenModel")

    /** Cookie 输入草稿流。 */
    private val cookieDraftState = MutableStateFlow("")

    /**
     * 暴露 Cookie 输入草稿。
     */
    fun cookieDraft(): StateFlow<String> = cookieDraftState.asStateFlow()

    /**
     * 更新 Cookie 输入内容。
     * @param draft 新输入文本。
     */
    fun updateCookieDraft(draft: String) {
        cookieDraftState.value = draft
    }

    /**
     * 启动初始化：先读 KV，再决定是否进入认证无效态。
     */
    fun bootstrap() {
        log.i { "认证初始化 -> 开始" }
        screenModelScope.launch {
            mutableState.value = AuthUiState.Loading
            val hasCookie = authRepository.restorePersistedSession()
            cookieDraftState.value = authRepository.loadCookieHeader()
            if (!hasCookie) {
                log.w { "认证初始化 -> 无可用Cookie" }
                mutableState.value = AuthUiState.AuthInvalid(
                    message = "未找到持久化 Cookie，请先输入 Cookie 登录。",
                )
                return@launch
            }
            probeAndUpdate()
        }
    }

    /**
     * 提交 Cookie 并重新探测登录态。
     */
    fun submitCookie() {
        log.i { "提交Cookie -> 开始" }
        screenModelScope.launch {
            val normalized = cookieDraftState.value.trim()
            if (normalized.isBlank()) {
                log.w { "提交Cookie -> 失败(空输入)" }
                mutableState.value = AuthUiState.AuthInvalid(
                    message = "Cookie 不能为空。",
                )
                return@launch
            }

            authRepository.submitCookie(normalized)
            cookieDraftState.value = authRepository.loadCookieHeader()
            probeAndUpdate()
        }
    }

    /**
     * 重试当前登录态探测。
     */
    fun retryProbe() {
        log.i { "重试探测 -> 开始" }
        screenModelScope.launch {
            probeAndUpdate()
        }
    }

    /**
     * 执行一次登录态探测并更新 UI 状态。
     */
    private suspend fun probeAndUpdate() {
        val nextState = when (val result = authRepository.probeLogin()) {
            is AuthProbeResult.LoggedIn -> {
                AuthUiState.Authenticated(username = result.username)
            }

            is AuthProbeResult.AuthInvalid -> {
                AuthUiState.AuthInvalid(
                    message = result.message,
                )
            }

            is AuthProbeResult.Error -> {
                AuthUiState.AuthInvalid(
                    message = result.message,
                )
            }
        }
        mutableState.value = nextState
        log.i { "登录态探测 -> ${summarizeAuthUiState(nextState)}" }
    }

    private fun summarizeAuthUiState(state: AuthUiState): String =
        when (state) {
            AuthUiState.Loading -> "加载中"
            is AuthUiState.Authenticated -> "已认证"
            is AuthUiState.AuthInvalid -> "认证无效"
        }
}

/**
 * 登录页状态定义。
 */
sealed interface AuthUiState {
    /** 初始化中。 */
    data object Loading : AuthUiState

    /**
     * 认证无效，需要用户输入 Cookie。
     * @property message 提示文案。
     */
    data class AuthInvalid(
        /** 展示给用户的输入提示文案。 */
        val message: String,
    ) : AuthUiState

    /**
     * 已认证完成。
     * @property username 当前用户名，可能为空。
     */
    data class Authenticated(
        /** 已登录用户名；页面缺信息时可能为空。 */
        val username: String?,
    ) : AuthUiState
}
