package me.domino.fa2.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.domino.fa2.data.local.KeyValueStorage

/** 认证会话补充资料存储。 */
class AuthSessionProfileStore(
    private val keyValueStorage: KeyValueStorage,
) {
  private val mutex = Mutex()

  suspend fun loadPersistedUsername(): String? =
      keyValueStorage.load(KeyValueStorage.KEY_AUTH_PERSISTED_USERNAME)?.trim()?.takeIf {
        it.isNotBlank()
      }

  suspend fun savePersistedUsername(username: String) {
    val normalized = username.trim()
    if (normalized.isBlank()) return
    mutex.withLock { keyValueStorage.save(KeyValueStorage.KEY_AUTH_PERSISTED_USERNAME, normalized) }
  }

  suspend fun clearPersistedUsername() {
    mutex.withLock { keyValueStorage.delete(KeyValueStorage.KEY_AUTH_PERSISTED_USERNAME) }
  }

  suspend fun loadNeedsRelogin(): Boolean =
      keyValueStorage.load(KeyValueStorage.KEY_AUTH_NEEDS_RELOGIN)?.toBooleanStrictOrNull() ?: false

  suspend fun saveNeedsRelogin(needsRelogin: Boolean) {
    mutex.withLock {
      if (needsRelogin) {
        keyValueStorage.save(KeyValueStorage.KEY_AUTH_NEEDS_RELOGIN, true.toString())
      } else {
        keyValueStorage.delete(KeyValueStorage.KEY_AUTH_NEEDS_RELOGIN)
      }
    }
  }
}
