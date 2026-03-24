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
import kotlin.test.AfterTest
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
import me.domino.fa2.di.appModules
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.util.FaUrls
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.context.GlobalContext
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
  /** 每条测试后关闭 Koin 与数据库。 */
  @AfterTest
  fun tearDown() {
    shutdownTestKoin()
  }

  /** `输入 cookie -> 登录成功 -> feed 成功` 链路。 */
  @Test
  fun cookieLoginThenFeedSuccess() = runTest {
    val source = ScriptedHtmlDataSource()
    startTestKoin(source, newTestStorageConfig())
    val koin = GlobalContext.get()
    val authDataSource: AuthDataSource = koin.get()
    val feedRepository: FeedRepository = koin.get()

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

  /** `CF challenge -> 登录探测返回错误` 链路。 */
  @Test
  fun cfChallengeProbeReturnsError() = runTest {
    val source = ScriptedHtmlDataSource()
    startTestKoin(source, newTestStorageConfig())
    val authDataSource: AuthDataSource = GlobalContext.get().get()

    source.enqueue(
        url = FaUrls.home,
        response = HtmlResponseResult.CfChallenge(cfRay = "ray123"),
    )
    val firstProbe = authDataSource.probeLogin()
    assertTrue(firstProbe is AuthProbeResult.Error)
  }

  /** 手动输入 cookie 时会过滤 Cloudflare 项，仅保留 auth 项； WebView 获得的 Cloudflare cookie 会被保留。 */
  @Test
  fun submitCookieFiltersCloudflareButKeepsWebViewCloudflare() = runTest {
    val source = ScriptedHtmlDataSource()
    startTestKoin(source, newTestStorageConfig())
    val authDataSource: AuthDataSource = GlobalContext.get().get()

    authDataSource.mergeChallengeCookie("cf_clearance=from_webview; __cf_bm=from_webview; a=ignore")
    authDataSource.submitCookie("a=1; b=2; cf_clearance=manual; __cf_bm=manual")

    val persisted = authDataSource.loadCookieHeader()
    assertTrue(persisted.contains("a=1"))
    assertTrue(persisted.contains("b=2"))
    assertTrue(persisted.contains("cf_clearance=from_webview"))
    assertTrue(persisted.contains("__cf_bm=from_webview"))
    assertFalse(persisted.contains("cf_clearance=manual"))
    assertFalse(persisted.contains("__cf_bm=manual"))
  }

  /** Cloudflare cookie 写入 KSafe 后，重建依赖仍可恢复。 */
  @Test
  fun challengeCookiePersistsAcrossRestart() = runTest {
    val source = ScriptedHtmlDataSource()
    val storage = newTestStorageConfig()
    startTestKoin(source, storage)

    val authDataSource: AuthDataSource = GlobalContext.get().get()
    authDataSource.mergeChallengeCookie("cf_clearance=from_webview; __cf_bm=from_webview")

    val restoredAuthDataSource = newFreshAuthDataSource()
    assertTrue(restoredAuthDataSource.restorePersistedSession())

    val restored = restoredAuthDataSource.loadCookieHeader()
    assertTrue(restored.contains("cf_clearance=from_webview"))
    assertTrue(restored.contains("__cf_bm=from_webview"))
  }

  /** Set-Cookie 合并结果会落入 KSafe，删除语义也会持久化。 */
  @Test
  fun setCookieMergePersistsAndDeletesAcrossRestart() = runTest {
    val source = ScriptedHtmlDataSource()
    val storage = newTestStorageConfig()
    startTestKoin(source, storage)

    val cookiesStorage: FaCookiesStorage = GlobalContext.get().get()
    cookiesStorage.mergeSetCookieValues(
        listOf("a=1; Path=/; HttpOnly", "cf_clearance=cloud; Path=/; Secure")
    )

    val restoredCookiesStorage = newFreshCookiesStorage()
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

  /** 清理会话后，KSafe 中不应再保留 cookie。 */
  @Test
  fun clearSessionDeletesEncryptedCookieEntry() = runTest {
    val source = ScriptedHtmlDataSource()
    val storage = newTestStorageConfig()
    startTestKoin(source, storage)

    val authDataSource: AuthDataSource = GlobalContext.get().get()
    val cookieVault: KSafe = GlobalContext.get().get(named(KOIN_QUALIFIER_COOKIE_VAULT))

    authDataSource.submitCookie("a=1; b=2")
    authDataSource.mergeChallengeCookie("cf_clearance=from_webview")
    authDataSource.clearSession()

    assertNull(cookieVault.getKeyInfo(CookiePersistence.KEY_COOKIE_HEADER))

    val restoredAuthDataSource = newFreshAuthDataSource()
    val restoredCookieVault: KSafe = GlobalContext.get().get(named(KOIN_QUALIFIER_COOKIE_VAULT))
    assertFalse(restoredAuthDataSource.restorePersistedSession())
    assertEquals("", restoredAuthDataSource.loadCookieHeader())
    assertNull(restoredCookieVault.getKeyInfo(CookiePersistence.KEY_COOKIE_HEADER))
  }

  /** 旧 DataStore cookie 键不会迁移到新的 KSafe 存储。 */
  @Test
  fun legacyDatastoreCookieIsIgnored() = runTest {
    val source = ScriptedHtmlDataSource()
    val storage = newTestStorageConfig()
    startTestKoin(source, storage)

    val dataStore: DataStore<Preferences> = GlobalContext.get().get()
    dataStore.edit { preferences ->
      preferences[stringPreferencesKey(CookiePersistence.KEY_COOKIE_HEADER)] =
          "legacy=1; cf_clearance=legacy"
    }

    val authDataSource: AuthDataSource = GlobalContext.get().get()
    val cookieVault: KSafe = GlobalContext.get().get(named(KOIN_QUALIFIER_COOKIE_VAULT))

    assertFalse(authDataSource.restorePersistedSession())
    assertEquals("", authDataSource.loadCookieHeader())
    assertNull(cookieVault.getKeyInfo(CookiePersistence.KEY_COOKIE_HEADER))
  }
}

/**
 * 启动测试专用 Koin 容器。
 *
 * @param htmlDataSource 用于覆盖网络层的数据源。
 */
private fun startTestKoin(htmlDataSource: FaHtmlDataSource, storage: TestStorageConfig) {
  shutdownTestKoin()
  startKoin {
    allowOverride(true)
    modules(appModules(testOverrideModule(htmlDataSource, storage)))
  }
}

/**
 * 构建测试覆写模块。
 *
 * @param htmlDataSource 需要注入的脚本化数据源。
 */
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
  single<FaHtmlDataSource>(qualifier = named("rawHtmlDataSource")) { htmlDataSource }
  single<DataStore<Preferences>> {
    PreferenceDataStoreFactory.createWithPath(produceFile = { storage.dataStorePath })
  }
  single(named(KOIN_QUALIFIER_COOKIE_VAULT)) { KSafe(fileName = storage.cookieVaultFileName) }
}

/** 关闭测试 Koin，避免跨测试污染。 */
private fun shutdownTestKoin() {
  val context = GlobalContext.getOrNull() ?: return
  runCatching { context.get<AppDatabase>().close() }
  stopKoin()
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

private fun newFreshCookiesStorage(): FaCookiesStorage {
  val cookiePersistence: CookiePersistence = GlobalContext.get().get()
  return FaCookiesStorage(cookiePersistence)
}

private fun newFreshAuthDataSource(): AuthDataSource {
  val koin = GlobalContext.get()
  return AuthDataSource(
      homeEndpoint = koin.get(),
      cookiesStorage = newFreshCookiesStorage(),
      userAgentStorage = koin.get<UserAgentStorage>(),
  )
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
