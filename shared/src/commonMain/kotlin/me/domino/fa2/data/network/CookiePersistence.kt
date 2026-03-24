package me.domino.fa2.data.network

import eu.anifantakis.lib.ksafe.KSafe

/**
 * Cookie Header 持久化端口。
 */
interface CookiePersistence {
    /**
     * 读取已持久化的 Cookie Header。
     */
    suspend fun loadCookieHeader(): String

    /**
     * 保存 Cookie Header。
     * @param value 规范化后的 Cookie Header。
     */
    suspend fun saveCookieHeader(value: String)

    /**
     * 删除已持久化的 Cookie Header。
     */
    suspend fun deleteCookieHeader()

    companion object {
        /** KSafe 中用于存储整串 Cookie Header 的键。 */
        const val KEY_COOKIE_HEADER: String = "cookie_header"
    }
}

/**
 * 基于 KSafe 的 Cookie 持久化实现。
 */
class KSafeCookiePersistence(
    private val cookieVault: KSafe,
) : CookiePersistence {
    override suspend fun loadCookieHeader(): String =
        cookieVault.get(
            key = CookiePersistence.KEY_COOKIE_HEADER,
            defaultValue = "",
        )

    override suspend fun saveCookieHeader(value: String) {
        cookieVault.put(
            key = CookiePersistence.KEY_COOKIE_HEADER,
            value = value,
        )
    }

    override suspend fun deleteCookieHeader() {
        cookieVault.delete(CookiePersistence.KEY_COOKIE_HEADER)
    }
}
