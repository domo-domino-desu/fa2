package me.domino.fa2.application.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.domino.fa2.data.repository.AuthSessionProfileStore

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
  private val mutableState = MutableStateFlow(AuthSessionUiState())
  override val state: StateFlow<AuthSessionUiState> = mutableState.asStateFlow()

  override suspend fun loadPersistedUsername(): String? = profileStore.loadPersistedUsername()

  override suspend fun needsRelogin(): Boolean = profileStore.loadNeedsRelogin()

  override suspend fun completeLogin(username: String?) {
    val normalizedUsername = username?.trim()?.takeIf { it.isNotBlank() }
    if (normalizedUsername == null) {
      profileStore.clearPersistedUsername()
    } else {
      profileStore.savePersistedUsername(normalizedUsername)
    }
    profileStore.saveNeedsRelogin(false)
    mutableState.value = mutableState.value.copy(pendingRestoreUri = pendingFaRouteStore.peek())
  }

  override suspend fun markReloginRequired(restoreUri: String) {
    pendingFaRouteStore.save(restoreUri)
    profileStore.saveNeedsRelogin(true)
    mutableState.value =
        mutableState.value.copy(
            reloginRequestVersion = mutableState.value.reloginRequestVersion + 1L,
            pendingRestoreUri = pendingFaRouteStore.peek(),
        )
  }

  override suspend fun clearSessionProfile() {
    profileStore.clearPersistedUsername()
    profileStore.saveNeedsRelogin(false)
    pendingFaRouteStore.clear()
    mutableState.value = AuthSessionUiState()
  }
}
