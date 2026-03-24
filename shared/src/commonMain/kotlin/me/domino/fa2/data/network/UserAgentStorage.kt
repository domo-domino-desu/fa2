package me.domino.fa2.data.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.domino.fa2.data.local.KeyValueStorage

/**
 * User-Agent 存储。
 * 提供默认值、KV 持久化与运行时覆盖。
 */
class UserAgentStorage(
    /** KV 存储。 */
    private val keyValueStore: KeyValueStorage,
    /** 默认 UA。 */
    private val defaultUserAgent: String = DEFAULT_USER_AGENT,
) {
    /** 并发保护锁。 */
    private val mutex = Mutex()

    /** 当前运行时 UA 覆盖值。 */
    private var runtimeOverride: String? = null

    /** 是否已从 KV 读取过。 */
    private var loaded: Boolean = false

    /**
     * 懒加载持久化 UA。
     */
    suspend fun loadPersistedIfNeeded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            runtimeOverride = keyValueStore.load(KeyValueStorage.KEY_HTTP_USER_AGENT)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            loaded = true
        }
    }

    /**
     * 返回当前生效 UA。
     */
    fun currentUserAgent(): String = runtimeOverride ?: defaultUserAgent

    /**
     * 保存运行时覆盖 UA 并持久化。
     * @param userAgent 新 UA。
     */
    suspend fun saveOverride(userAgent: String) {
        val normalized = userAgent.trim()
        if (normalized.isBlank()) {
            clearOverride()
            return
        }
        mutex.withLock {
            runtimeOverride = normalized
            keyValueStore.save(KeyValueStorage.KEY_HTTP_USER_AGENT, normalized)
            loaded = true
        }
    }

    /**
     * 清理 UA 覆盖并回退默认值。
     */
    suspend fun clearOverride() {
        mutex.withLock {
            runtimeOverride = null
            keyValueStore.delete(KeyValueStorage.KEY_HTTP_USER_AGENT)
            loaded = true
        }
    }

    companion object {
        /** 默认浏览器 UA。 */
        const val DEFAULT_USER_AGENT: String =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"
    }
}
