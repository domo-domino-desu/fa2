package me.domino.fa2.integration

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import eu.anifantakis.lib.ksafe.KSafe
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.datasource.AuthDataSource
import me.domino.fa2.data.local.AppDatabase
import me.domino.fa2.data.local.AppDatabaseBuilderFactory
import me.domino.fa2.data.model.AuthProbeResult
import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.network.CookiePersistence
import me.domino.fa2.data.network.FaCookiesStorage
import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.data.network.UserAgentStorage
import me.domino.fa2.data.repository.FeedRepository
import me.domino.fa2.di.KOIN_QUALIFIER_COOKIE_VAULT
import me.domino.fa2.di.KOIN_QUALIFIER_RAW_HTML_DATA_SOURCE
import me.domino.fa2.di.KOIN_QUALIFIER_SETTINGS_SECRET_VAULT
import me.domino.fa2.di.appModules
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.util.FaUrls
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * 最简链路集成测试：
 * 1) 登录探测；
 * 2) 输入 Cookie；
 * 3) 进入 Feed。
 */
class MinimalFlowIntegrationTest {
  /** `输入 cookie -> 登录成功 -> feed 成功` 链路。 */
  @Test
  fun cookieLoginThenFeedSuccess() = runTest {
    withTestApp { source ->
      val authDataSource = authDataSource()
      val feedRepository = feedRepository()

      source.enqueue(
          url = FaUrls.home,
          response =
              HtmlResponseResult.Success(
                  body = TestFixtures.read("www.furaffinity.net:loggedout.html"),
                  url = FaUrls.home,
              ),
      )
      val firstProbe = authDataSource.probeLogin()
      assertTrue(firstProbe is AuthProbeResult.AuthInvalid)

      authDataSource.submitCookie("a=1; b=2; cf_clearance=test")

      source.enqueue(
          url = FaUrls.home,
          response =
              HtmlResponseResult.Success(
                  body = TestFixtures.read("www.furaffinity.net:loggedin.html"),
                  url = FaUrls.home,
              ),
      )
      val secondProbe = authDataSource.probeLogin()
      assertTrue(secondProbe is AuthProbeResult.LoggedIn)

      source.enqueue(
          url = FaUrls.submissions(),
          response =
              HtmlResponseResult.Success(
                  body = TestFixtures.read("www.furaffinity.net:msg:submissions-firstpage.html"),
                  url = FaUrls.submissions(),
              ),
      )
      val states = feedRepository.streamFirstPage().take(2).toList()
      assertTrue(states.first() is PageState.Loading)
      assertTrue(states.last() is PageState.Success)
    }
  }

  /** `CF challenge -> 登录探测返回认证无效` 链路。 */
  @Test
  fun cfChallengeProbeReturnsAuthInvalid() = runTest {
    withTestApp { source ->
      val authDataSource = authDataSource()

      source.enqueue(
          url = FaUrls.home,
          response = HtmlResponseResult.CfChallenge(cfRay = "ray123"),
      )
      val firstProbe = authDataSource.probeLogin()
      assertTrue(firstProbe is AuthProbeResult.AuthInvalid)
    }
  }

  /** 手动输入 cookie 时会过滤 Cloudflare 项，仅保留 auth 项； WebView 获得的 Cloudflare cookie 会被保留。 */
  @Test
  fun submitCookieFiltersCloudflareButKeepsWebViewCloudflare() = runTest {
    withTestApp { _ ->
      val authDataSource = authDataSource()

      authDataSource.mergeChallengeCookie(
          "cf_clearance=from_webview; __cf_bm=from_webview; a=ignore"
      )
      authDataSource.submitCookie("a=1; b=2; cf_clearance=manual; __cf_bm=manual")

      val persisted = authDataSource.loadCookieHeader()
      assertTrue(persisted.contains("a=1"))
      assertTrue(persisted.contains("b=2"))
      assertTrue(persisted.contains("cf_clearance=from_webview"))
      assertTrue(persisted.contains("__cf_bm=from_webview"))
      assertFalse(persisted.contains("cf_clearance=manual"))
      assertFalse(persisted.contains("__cf_bm=manual"))
    }
  }

  /** Cloudflare cookie 写入 KSafe 后，重建依赖仍可恢复。 */
  @Test
  fun challengeCookiePersistsAcrossRestart() = runTest {
    withTestApp(storage = newTestStorageConfig()) { _ ->
      val authDataSource = authDataSource()
      authDataSource.mergeChallengeCookie("cf_clearance=from_webview; __cf_bm=from_webview")

      val restoredAuthDataSource = createFreshAuthDataSource()
      assertTrue(restoredAuthDataSource.restorePersistedSession())

      val restored = restoredAuthDataSource.loadCookieHeader()
      assertTrue(restored.contains("cf_clearance=from_webview"))
      assertTrue(restored.contains("__cf_bm=from_webview"))
    }
  }

  /** WebView 捕获到的完整 cookie 快照会同时保留 auth 与 Cloudflare 项，并可跨重启恢复。 */
  @Test
  fun webViewCookieSyncPersistsAuthAndCloudflareAcrossRestart() = runTest {
    withTestApp(storage = newTestStorageConfig()) { _ ->
      val authDataSource = authDataSource()
      authDataSource.syncWebViewCookie("a=1; b=2; cf_clearance=from_webview; __cf_bm=from_webview")

      val restoredAuthDataSource = createFreshAuthDataSource()
      assertTrue(restoredAuthDataSource.restorePersistedSession())

      val restored = restoredAuthDataSource.loadCookieHeader()
      assertTrue(restored.contains("a=1"))
      assertTrue(restored.contains("b=2"))
      assertTrue(restored.contains("cf_clearance=from_webview"))
      assertTrue(restored.contains("__cf_bm=from_webview"))
    }
  }

  /** Set-Cookie 合并结果会落入 KSafe，删除语义也会持久化。 */
  @Test
  fun setCookieMergePersistsAndDeletesAcrossRestart() = runTest {
    withTestApp(storage = newTestStorageConfig()) { _ ->
      val cookiesStorage = cookiesStorage()
      cookiesStorage.mergeSetCookieValues(
          listOf("a=1; Path=/; HttpOnly", "cf_clearance=cloud; Path=/; Secure")
      )

      val restoredCookiesStorage = createFreshCookiesStorage()
      val restored = restoredCookiesStorage.loadRawCookieHeader()
      assertTrue(restored.contains("a=1"))
      assertTrue(restored.contains("cf_clearance=cloud"))

      restoredCookiesStorage.mergeSetCookieValues(
          listOf(
              "a=gone; Max-Age=0; Path=/",
              "cf_clearance=gone; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/",
          )
      )

      val afterDelete = restoredCookiesStorage.loadRawCookieHeader()
      assertFalse(afterDelete.contains("a=1"))
      assertFalse(afterDelete.contains("cf_clearance=cloud"))
    }
  }

  /** 清理会话后，KSafe 中不应再保留 cookie。 */
  @Test
  fun clearSessionDeletesEncryptedCookieEntry() = runTest {
    withTestApp(storage = newTestStorageConfig()) { _ ->
      val authDataSource = authDataSource()
      val cookieVault = cookieVault()

      authDataSource.submitCookie("a=1; b=2")
      authDataSource.mergeChallengeCookie("cf_clearance=from_webview")
      authDataSource.clearSession()

      assertNull(cookieVault.getKeyInfo(CookiePersistence.KEY_COOKIE_HEADER))

      val restoredAuthDataSource = createFreshAuthDataSource()
      val restoredCookieVault = cookieVault()
      assertFalse(restoredAuthDataSource.restorePersistedSession())
      assertEquals("", restoredAuthDataSource.loadCookieHeader())
      assertNull(restoredCookieVault.getKeyInfo(CookiePersistence.KEY_COOKIE_HEADER))
    }
  }

  /** 旧 DataStore cookie 键不会迁移到新的 KSafe 存储。 */
  @Test
  fun legacyDatastoreCookieIsIgnored() = runTest {
    withTestApp(storage = newTestStorageConfig()) { _ ->
      val dataStore = dataStore()
      dataStore.edit { preferences ->
        preferences[stringPreferencesKey(CookiePersistence.KEY_COOKIE_HEADER)] =
            "legacy=1; cf_clearance=legacy"
      }

      val authDataSource = authDataSource()
      val cookieVault = cookieVault()

      assertFalse(authDataSource.restorePersistedSession())
      assertEquals("", authDataSource.loadCookieHeader())
      assertNull(cookieVault.getKeyInfo(CookiePersistence.KEY_COOKIE_HEADER))
    }
  }
}

private fun testOverrideModule(
    htmlDataSource: FaHtmlDataSource,
    storage: TestStorageConfig,
): Module = module {
  single<AppDatabaseBuilderFactory> {
    AppDatabaseBuilderFactory {
      val dbPath =
          "/tmp/fa2-test-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(100_000)}.db"
      Room.databaseBuilder<AppDatabase>(name = dbPath).setDriver(BundledSQLiteDriver())
    }
  }
  single<FaHtmlDataSource> { htmlDataSource }
  single<FaHtmlDataSource>(qualifier = named(KOIN_QUALIFIER_RAW_HTML_DATA_SOURCE)) {
    htmlDataSource
  }
  single<DataStore<Preferences>> {
    PreferenceDataStoreFactory.createWithPath(produceFile = { storage.dataStorePath })
  }
  single(named(KOIN_QUALIFIER_COOKIE_VAULT)) { KSafe(fileName = storage.cookieVaultFileName) }
  single(named(KOIN_QUALIFIER_SETTINGS_SECRET_VAULT)) {
    KSafe(fileName = "${storage.cookieVaultFileName}_settings_secret_vault")
  }
}

private fun newTestStorageConfig(): TestStorageConfig {
  val randomSuffix = Random.nextLong().toString().replace('-', '0')
  return TestStorageConfig(
      dataStorePath =
          "${FileSystem.SYSTEM_TEMPORARY_DIRECTORY}/fa2-test-$randomSuffix.preferences_pb".toPath(),
      cookieVaultFileName = "fa2test_$randomSuffix",
  )
}

private data class TestStorageConfig(val dataStorePath: Path, val cookieVaultFileName: String)

private suspend fun withTestApp(
    storage: TestStorageConfig = newTestStorageConfig(),
    block: suspend MinimalFlowTestHarness.(ScriptedHtmlDataSource) -> Unit,
) {
  val source = ScriptedHtmlDataSource()
  val harness = MinimalFlowTestHarness(htmlDataSource = source, storage = storage)
  try {
    harness.block(source)
  } finally {
    harness.close()
  }
}

private class MinimalFlowTestHarness(
    htmlDataSource: FaHtmlDataSource,
    storage: TestStorageConfig,
) : AutoCloseable {
  private val koinApplication: KoinApplication = run {
    stopKoin()
    startKoin {
      allowOverride(true)
      modules(appModules(testOverrideModule(htmlDataSource, storage)))
    }
  }

  private val koin: Koin = koinApplication.koin

  fun authDataSource(): AuthDataSource = koin.get()

  fun feedRepository(): FeedRepository = koin.get()

  fun cookiesStorage(): FaCookiesStorage = koin.get()

  fun dataStore(): DataStore<Preferences> = koin.get()

  fun cookieVault(): KSafe = koin.get(named(KOIN_QUALIFIER_COOKIE_VAULT))

  fun settingsSecretVault(): KSafe = koin.get(named(KOIN_QUALIFIER_SETTINGS_SECRET_VAULT))

  fun createFreshCookiesStorage(): FaCookiesStorage {
    val cookiePersistence: CookiePersistence = koin.get()
    return FaCookiesStorage(cookiePersistence)
  }

  fun createFreshAuthDataSource(): AuthDataSource =
      AuthDataSource(
          homeEndpoint = koin.get(),
          cookiesStorage = createFreshCookiesStorage(),
          userAgentStorage = koin.get<UserAgentStorage>(),
      )

  override fun close() {
    runCatching { koin.get<AppDatabase>().close() }
    stopKoin()
  }
}

/** 脚本化 HTML 数据源，用于集成测试控制响应序列。 */
private class ScriptedHtmlDataSource : FaHtmlDataSource {
  /** 按 URL 存放待消费响应队列。 */
  private val queueByUrl: MutableMap<String, ArrayDeque<HtmlResponseResult>> = mutableMapOf()

  /**
   * 入队一个响应。
   *
   * @param url 请求地址键。
   * @param response 响应对象。
   */
  fun enqueue(url: String, response: HtmlResponseResult) {
    val queue = queueByUrl.getOrPut(url) { ArrayDeque() }
    queue.addLast(response)
  }

  /**
   * 读取一个响应；若队列为空则返回错误响应。
   *
   * @param url 请求地址。
   */
  override suspend fun get(url: String): HtmlResponseResult {
    val queue = queueByUrl[url]
    if (queue == null || queue.isEmpty()) {
      return HtmlResponseResult.Error(
          statusCode = 500,
          message = "No scripted response for $url",
      )
    }
    return queue.removeFirst()
  }
}
