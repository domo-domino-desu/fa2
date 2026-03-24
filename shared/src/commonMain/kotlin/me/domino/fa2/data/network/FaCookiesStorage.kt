package me.domino.fa2.data.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
/**
 * 轻量 Cookie 存储。
 * 负责内存态与持久化双写。
 */
class FaCookiesStorage(
    /** Cookie 持久化端口。 */
    private val cookiePersistence: CookiePersistence,
) {
    /** 并发保护锁。 */
    private val mutex = Mutex()

    /** 当前 Cookie Header 原文。 */
    private var rawCookieHeader: String = ""

    /** 是否已经尝试从持久化层恢复。 */
    private var restoredFromStore: Boolean = false

    /**
     * 读取当前 Cookie Header。
     */
    suspend fun loadRawCookieHeader(): String {
        restorePersistedIfNeeded()
        return mutex.withLock { rawCookieHeader }
    }

    /**
     * 判断当前是否有可用 Cookie。
     */
    suspend fun hasCookie(): Boolean = loadRawCookieHeader().isNotBlank()

    /**
     * 启动时从持久化层恢复 Cookie。
     */
    suspend fun restorePersistedIfNeeded() {
        if (restoredFromStore) return
        mutex.withLock {
            if (restoredFromStore) return
            val persisted = cookiePersistence.loadCookieHeader()
            rawCookieHeader = normalizeCookieHeader(persisted)
            restoredFromStore = true
        }
    }

    /**
     * 直接覆盖 Cookie Header。
     * @param raw 形如 `a=...; b=...`。
     */
    suspend fun saveRawCookieHeader(raw: String) {
        restorePersistedIfNeeded()
        val normalized = normalizeCookieHeader(raw)
        mutex.withLock {
            rawCookieHeader = normalized
            persistLocked()
        }
    }

    /**
     * 用新输入替换 Cookie，同时可选择保留部分已有 Cookie。
     * @param raw 新输入 Cookie 文本。
     * @param preserveExisting 现有 Cookie 保留条件。
     * @param acceptIncoming 新输入 Cookie 接受条件。
     */
    suspend fun replaceRawCookieHeader(
        raw: String,
        preserveExisting: (String) -> Boolean = { false },
        acceptIncoming: (String) -> Boolean = { true },
    ) {
        restorePersistedIfNeeded()
        mutex.withLock {
            val existing = parseCookieMap(rawCookieHeader)
            val incoming = parseCookieMap(raw)
            val merged = LinkedHashMap<String, String>()

            existing.forEach { (name, value) ->
                if (preserveExisting(name)) {
                    merged[name] = value
                }
            }
            incoming.forEach { (name, value) ->
                if (acceptIncoming(name)) {
                    merged[name] = value
                }
            }

            rawCookieHeader = merged.entries.joinToString("; ") { (name, value) -> "$name=$value" }
            persistLocked()
        }
    }

    /**
     * 合并 `Set-Cookie` 响应头。
     * @param setCookieValues 响应中的所有 `Set-Cookie` 条目。
     */
    suspend fun mergeSetCookieValues(setCookieValues: List<String>) {
        if (setCookieValues.isEmpty()) return
        restorePersistedIfNeeded()
        mutex.withLock {
            val existing = parseCookieMap(rawCookieHeader).toMutableMap()
            setCookieValues.forEach { setCookie ->
                val pair = setCookie.substringBefore(';').trim()
                if (!pair.contains('=')) return@forEach
                val name = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=', "").trim()
                if (name.isBlank()) return@forEach

                val lowered = setCookie.lowercase()
                val shouldDelete = lowered.contains("max-age=0") || lowered.contains("expires=thu, 01 jan 1970")
                if (shouldDelete) {
                    existing.remove(name)
                } else {
                    existing[name] = value
                }
            }
            rawCookieHeader = existing.entries.joinToString("; ") { (name, value) -> "$name=$value" }
            persistLocked()
        }
    }

    /**
     * 按过滤条件合并原始 Cookie Header。
     * @param raw 待合并 Cookie 文本。
     * @param shouldMerge 仅当返回 true 时才合并该 cookie 名。
     */
    suspend fun mergeRawCookieHeader(
        raw: String,
        shouldMerge: (String) -> Boolean = { true },
    ) {
        restorePersistedIfNeeded()
        mutex.withLock {
            val merged = parseCookieMap(rawCookieHeader).toMutableMap()
            parseCookieMap(raw).forEach { (name, value) ->
                if (shouldMerge(name)) {
                    merged[name] = value
                }
            }
            rawCookieHeader = merged.entries.joinToString("; ") { (name, value) -> "$name=$value" }
            persistLocked()
        }
    }

    /**
     * 清空 Cookie。
     */
    suspend fun clear() {
        restorePersistedIfNeeded()
        mutex.withLock {
            rawCookieHeader = ""
            persistLocked()
        }
    }

    /**
     * 规范化 Cookie Header，去重并保持最后值。
     * @param raw 原始 Cookie 文本。
     */
    private fun normalizeCookieHeader(raw: String): String =
        parseCookieMap(raw).entries.joinToString("; ") { (name, value) -> "$name=$value" }

    /**
     * 解析 Cookie 文本到键值映射。
     * @param raw 原始 Cookie 文本。
     */
    private fun parseCookieMap(raw: String): LinkedHashMap<String, String> {
        val output = LinkedHashMap<String, String>()
        raw.split(';')
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains('=') }
            .forEach { token ->
                val name = token.substringBefore('=').trim()
                val value = token.substringAfter('=', "").trim()
                if (name.isNotBlank()) {
                    output[name] = value
                }
            }
        return output
    }

    /**
     * 持久化当前内存 Cookie。
     */
    private suspend fun persistLocked() {
        if (rawCookieHeader.isBlank()) {
            cookiePersistence.deleteCookieHeader()
        } else {
            cookiePersistence.saveCookieHeader(rawCookieHeader)
        }
    }
}
