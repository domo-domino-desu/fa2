package me.domino.fa2.application.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.domino.fa2.data.repository.AuthSessionProfileStore
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizeUrl

data class AuthSessionUiState(
    val reloginRequestVersion: Long = 0L,
    val pendingRestoreUri: String? = null,
)

interface AuthSessionController {
  val state: StateFlow<AuthSessionUiState>

  suspend fun loadPersistedUsername(): String?

  suspend fun needsRelogin(): Boolean

  suspend fun completeLogin(username: String?)

  suspend fun markReloginRequired(restoreUri: String)

  suspend fun clearSessionProfile()
}

class DefaultAuthSessionController(
    private val profileStore: AuthSessionProfileStore,
    private val pendingFaRouteStore: PendingFaRouteStore,
) : AuthSessionController {
  private val log = FaLog.withTag("AuthSessionController")
  private val mutableState = MutableStateFlow(AuthSessionUiState())
  override val state: StateFlow<AuthSessionUiState> = mutableState.asStateFlow()

  override suspend fun loadPersistedUsername(): String? =
      profileStore.loadPersistedUsername().also { username ->
        log.d { "读取认证资料 -> username=${username ?: "-"}" }
      }

  override suspend fun needsRelogin(): Boolean =
      profileStore.loadNeedsRelogin().also { needed -> log.d { "读取认证资料 -> needsRelogin=$needed" } }

  override suspend fun completeLogin(username: String?) {
    val normalizedUsername = username?.trim()?.takeIf { it.isNotBlank() }
    log.i { "完成登录 -> 开始(user=${normalizedUsername ?: "-"})" }
    if (normalizedUsername == null) {
      profileStore.clearPersistedUsername()
    } else {
      profileStore.savePersistedUsername(normalizedUsername)
    }
    profileStore.saveNeedsRelogin(false)
    val restoreUri = pendingFaRouteStore.peek()
    mutableState.value = mutableState.value.copy(pendingRestoreUri = restoreUri)
    log.i {
      "完成登录 -> 成功(user=${normalizedUsername ?: "-"},pendingRestoreUri=${restoreUri?.let(::summarizeUrl) ?: "-"})"
    }
  }

  override suspend fun markReloginRequired(restoreUri: String) {
    log.w { "标记需要重登录 -> restoreUri=${summarizeUrl(restoreUri)}" }
    pendingFaRouteStore.save(restoreUri)
    profileStore.saveNeedsRelogin(true)
    mutableState.value =
        mutableState.value.copy(
            reloginRequestVersion = mutableState.value.reloginRequestVersion + 1L,
            pendingRestoreUri = pendingFaRouteStore.peek(),
        )
    log.w {
      "标记需要重登录 -> 完成(version=${mutableState.value.reloginRequestVersion},pendingRestoreUri=${mutableState.value.pendingRestoreUri?.let(::summarizeUrl) ?: "-"})"
    }
  }

  override suspend fun clearSessionProfile() {
    log.i { "清理认证资料 -> 开始" }
    profileStore.clearPersistedUsername()
    profileStore.saveNeedsRelogin(false)
    pendingFaRouteStore.clear()
    mutableState.value = AuthSessionUiState()
    log.i { "清理认证资料 -> 成功" }
  }
}
