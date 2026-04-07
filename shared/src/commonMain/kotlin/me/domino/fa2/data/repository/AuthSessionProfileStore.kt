package me.domino.fa2.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.domino.fa2.data.local.KeyValueStorage
import me.domino.fa2.util.logging.FaLog

/** 认证会话补充资料存储。 */
class AuthSessionProfileStore(
    private val keyValueStorage: KeyValueStorage,
) {
  private val log = FaLog.withTag("AuthSessionProfileStore")
  private val mutex = Mutex()

  suspend fun loadPersistedUsername(): String? =
      keyValueStorage
          .load(KeyValueStorage.KEY_AUTH_PERSISTED_USERNAME)
          ?.trim()
          ?.takeIf { it.isNotBlank() }
          .also { username -> log.d { "读取持久化用户名 -> ${username ?: "-"}" } }

  suspend fun savePersistedUsername(username: String) {
    val normalized = username.trim()
    if (normalized.isBlank()) {
      log.w { "保存持久化用户名 -> 跳过(空用户名)" }
      return
    }
    mutex.withLock { keyValueStorage.save(KeyValueStorage.KEY_AUTH_PERSISTED_USERNAME, normalized) }
    log.i { "保存持久化用户名 -> user=$normalized" }
  }

  suspend fun clearPersistedUsername() {
    mutex.withLock { keyValueStorage.delete(KeyValueStorage.KEY_AUTH_PERSISTED_USERNAME) }
    log.i { "清理持久化用户名 -> 成功" }
  }

  suspend fun loadNeedsRelogin(): Boolean =
      (keyValueStorage.load(KeyValueStorage.KEY_AUTH_NEEDS_RELOGIN)?.toBooleanStrictOrNull()
              ?: false)
          .also { needsRelogin -> log.d { "读取重登录标记 -> $needsRelogin" } }

  suspend fun saveNeedsRelogin(needsRelogin: Boolean) {
    mutex.withLock {
      if (needsRelogin) {
        keyValueStorage.save(KeyValueStorage.KEY_AUTH_NEEDS_RELOGIN, true.toString())
      } else {
        keyValueStorage.delete(KeyValueStorage.KEY_AUTH_NEEDS_RELOGIN)
      }
    }
    log.i { "保存重登录标记 -> $needsRelogin" }
  }
}
