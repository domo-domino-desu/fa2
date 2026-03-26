package me.domino.fa2.desktop.e2e

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.runBlocking
import me.domino.fa2.data.local.KeyValueStorage
import me.domino.fa2.data.network.FaCookiesStorage
import me.domino.fa2.data.network.KSafeCookiePersistence
import me.domino.fa2.data.network.UserAgentStorage

internal object DesktopE2eSessionStorage {
  suspend fun seedProfile(
      stores: DesktopE2eProfileStores,
      snapshot: DesktopE2eSessionSnapshot,
  ) {
    val cookiesStorage = FaCookiesStorage(KSafeCookiePersistence(cookieVault = stores.cookieVault))
    val userAgentStorage = UserAgentStorage(KeyValueStorage(stores.dataStore))
    cookiesStorage.saveRawCookieHeader(snapshot.cookieHeader)
    userAgentStorage.saveOverride(snapshot.userAgent)
  }

  suspend fun clearCookieVault(cookieVault: KSafe) {
    cookieVault.clearAll()
  }

  suspend fun readRealCookieHeader(): String {
    val cookiePersistence =
        KSafeCookiePersistence(cookieVault = KSafe(fileName = realCookieVaultFileName))
    return cookiePersistence.loadCookieHeader()
  }

  suspend fun readRealPersistedUserAgent(): String {
    val realDataStore = createPreferencesDataStore(realDesktopPreferencesFile())
    return KeyValueStorage(realDataStore).load(KeyValueStorage.KEY_HTTP_USER_AGENT).orEmpty()
  }

  fun seedProfileBlocking(stores: DesktopE2eProfileStores, snapshot: DesktopE2eSessionSnapshot) {
    runBlocking { seedProfile(stores = stores, snapshot = snapshot) }
  }

  fun clearCookieVaultBlocking(cookieVault: KSafe) {
    runBlocking { clearCookieVault(cookieVault) }
  }

  fun readRealCookieHeaderBlocking(): String = runBlocking { readRealCookieHeader() }

  fun readRealPersistedUserAgentBlocking(): String = runBlocking { readRealPersistedUserAgent() }
}
