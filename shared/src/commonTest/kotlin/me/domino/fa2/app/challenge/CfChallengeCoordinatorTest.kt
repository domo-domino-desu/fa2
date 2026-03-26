package me.domino.fa2.app.challenge

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.local.KeyValueStorage
import me.domino.fa2.data.network.CookiePersistence
import me.domino.fa2.data.network.FaCookiesStorage
import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.data.network.UserAgentStorage
import me.domino.fa2.data.network.challenge.CfChallengeSignal
import me.domino.fa2.ui.pages.auth.SessionWebViewPort
import me.domino.fa2.util.FaUrls
import okio.FileSystem
import okio.Path.Companion.toPath

class CfChallengeCoordinatorTest {
  @Test
  fun confirmFromWebViewCompletesSessionAndPersistsCloudflareCookies() = runTest {
    val fixture =
        createCoordinatorFixture(HtmlResponseResult.Success(body = "ok", url = FaUrls.home))
    fixture.cookiesStorage.saveRawCookieHeader("a=1")
    val awaiting =
        async(start = CoroutineStart.UNDISPATCHED) {
          fixture.coordinator.awaitResolution(
              CfChallengeSignal(requestUrl = FaUrls.home, cfRay = "ray-1")
          )
        }

    val confirmed =
        fixture.coordinator.confirmFromWebView(
            port =
                FakeChallengeSessionWebViewPort(
                    lastLoadedUrl = FaUrls.login,
                    userAgent = "ChallengeWebView/1.0",
                    cookiesByUrl =
                        mapOf(
                            FaUrls.home to "cf_clearance=cloud",
                            FaUrls.login to "__cf_bm=bm-token",
                        ),
                ),
            triggerUrl = FaUrls.home,
        )

    assertTrue(confirmed)
    assertTrue(awaiting.await())
    val merged = fixture.cookiesStorage.loadRawCookieHeader()
    assertTrue(merged.contains("a=1"))
    assertTrue(merged.contains("cf_clearance=cloud"))
    assertTrue(merged.contains("__cf_bm=bm-token"))
    assertEquals("ChallengeWebView/1.0", fixture.userAgentStorage.currentUserAgent())
    assertEquals(CfChallengeUiState.Idle, fixture.sessionStore.state.value)
  }

  @Test
  fun confirmFromWebViewRollsBackCookiesWhenProbeFails() = runTest {
    val fixture =
        createCoordinatorFixture(
            HtmlResponseResult.Error(statusCode = 503, message = "still blocked")
        )
    fixture.cookiesStorage.saveRawCookieHeader("a=1")
    val awaiting =
        async(start = CoroutineStart.UNDISPATCHED) {
          fixture.coordinator.awaitResolution(
              CfChallengeSignal(requestUrl = FaUrls.home, cfRay = "ray-2")
          )
        }

    val confirmed =
        fixture.coordinator.confirmFromWebView(
            port =
                FakeChallengeSessionWebViewPort(
                    lastLoadedUrl = FaUrls.login,
                    userAgent = "ChallengeWebView/1.0",
                    cookiesByUrl = mapOf(FaUrls.home to "cf_clearance=cloud"),
                ),
            triggerUrl = FaUrls.home,
        )

    assertFalse(confirmed)
    assertEquals("a=1", fixture.cookiesStorage.loadRawCookieHeader())
    assertTrue(fixture.sessionStore.state.value is CfChallengeUiState.Active)
    fixture.coordinator.cancel()
    assertFalse(awaiting.await())
  }
}

private data class ChallengeCoordinatorFixture(
    val coordinator: CfChallengeCoordinator,
    val cookiesStorage: FaCookiesStorage,
    val userAgentStorage: UserAgentStorage,
    val sessionStore: ChallengeSessionStore,
)

private fun createCoordinatorFixture(response: HtmlResponseResult): ChallengeCoordinatorFixture {
  val cookiesStorage = FaCookiesStorage(InMemoryCookiePersistence())
  val randomSuffix = Random.nextLong().toString().replace('-', '0')
  val dataStorePath =
      "${FileSystem.SYSTEM_TEMPORARY_DIRECTORY}/fa2-challenge-$randomSuffix.preferences_pb".toPath()
  val userAgentStorage =
      UserAgentStorage(
          KeyValueStorage(
              PreferenceDataStoreFactory.createWithPath(produceFile = { dataStorePath })
          )
      )
  val htmlDataSource = ScriptedChallengeHtmlDataSource(response)
  val sessionStore = ChallengeSessionStore()
  return ChallengeCoordinatorFixture(
      coordinator =
          CfChallengeCoordinator(
              sessionStore = sessionStore,
              cookiesStorage = cookiesStorage,
              userAgentStorage = userAgentStorage,
              cookiePolicy = CloudflareChallengeCookiePolicy(),
              probeVerifier =
                  ChallengeProbeVerifier(
                      rawHtmlDataSource = htmlDataSource,
                      retryDelaysMs = listOf(0L),
                  ),
          ),
      cookiesStorage = cookiesStorage,
      userAgentStorage = userAgentStorage,
      sessionStore = sessionStore,
  )
}

private class InMemoryCookiePersistence : CookiePersistence {
  private var rawCookieHeader: String = ""

  override suspend fun loadCookieHeader(): String = rawCookieHeader

  override suspend fun saveCookieHeader(value: String) {
    rawCookieHeader = value
  }

  override suspend fun deleteCookieHeader() {
    rawCookieHeader = ""
  }
}

private class ScriptedChallengeHtmlDataSource(private val response: HtmlResponseResult) :
    FaHtmlDataSource {
  override suspend fun get(url: String): HtmlResponseResult = response
}

private class FakeChallengeSessionWebViewPort(
    override val lastLoadedUrl: String?,
    private val userAgent: String,
    private val cookiesByUrl: Map<String, String>,
) : SessionWebViewPort {
  override fun loadUrl(url: String) = Unit

  override suspend fun captureCookieHeader(url: String): String = cookiesByUrl[url].orEmpty()

  override suspend fun injectCookieHeader(url: String, cookieHeader: String) = Unit

  override suspend fun readUserAgent(): String = userAgent
}
