package me.domino.fa2.desktop.e2e

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.runBlocking
import me.domino.fa2.data.local.KeyValueStorage
import me.domino.fa2.data.network.FaCookiesStorage
import me.domino.fa2.data.network.KSafeCookiePersistence
import me.domino.fa2.data.network.UserAgentStorage

/** E2E 测试会话数据辅助工具，负责读写测试用的 Cookie 与用户代理数据。 */
internal object DesktopE2eSessionStorage {
  /** 将会话快照写入给定的测试档案存储中。 */
  suspend fun seedProfile(
      stores: DesktopE2eProfileStores,
      snapshot: DesktopE2eSessionSnapshot,
  ) {
    val cookiesStorage = FaCookiesStorage(KSafeCookiePersistence(cookieVault = stores.cookieVault))
    val userAgentStorage = UserAgentStorage(KeyValueStorage(stores.dataStore))
    cookiesStorage.saveRawCookieHeader(snapshot.cookieHeader)
    userAgentStorage.saveOverride(snapshot.userAgent)
  }

  /** 清空指定 Cookie 存储中的所有数据。 */
  suspend fun clearCookieVault(cookieVault: KSafe) {
    cookieVault.clearAll()
  }

  /** 从真实的 Cookie 存储中读取 Cookie Header 字符串。 */
  suspend fun readRealCookieHeader(): String {
    val cookiePersistence =
        KSafeCookiePersistence(cookieVault = KSafe(fileName = realCookieVaultFileName))
    return cookiePersistence.loadCookieHeader()
  }

  /** 从真实的偏好设置存储中读取持久化的用户代理字符串。 */
  suspend fun readRealPersistedUserAgent(): String {
    val realDataStore = createPreferencesDataStore(realDesktopPreferencesFile())
    return KeyValueStorage(realDataStore).load(KeyValueStorage.KEY_HTTP_USER_AGENT).orEmpty()
  }

  /** 同步版本：将会话快照写入测试档案存储。 */
  fun seedProfileBlocking(stores: DesktopE2eProfileStores, snapshot: DesktopE2eSessionSnapshot) {
    runBlocking { seedProfile(stores = stores, snapshot = snapshot) }
  }

  /** 同步版本：清空指定 Cookie 存储中的所有数据。 */
  fun clearCookieVaultBlocking(cookieVault: KSafe) {
    runBlocking { clearCookieVault(cookieVault) }
  }

  /** 同步版本：从真实 Cookie 存储中读取 Cookie Header 字符串。 */
  fun readRealCookieHeaderBlocking(): String = runBlocking { readRealCookieHeader() }

  /** 同步版本：从真实偏好设置存储中读取持久化的用户代理字符串。 */
  fun readRealPersistedUserAgentBlocking(): String = runBlocking { readRealPersistedUserAgent() }
}
