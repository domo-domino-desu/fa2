package me.domino.fa2.ui.pages.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.domino.fa2.application.challenge.port.SessionWebViewPort
import me.domino.fa2.data.datasource.AuthDataSource
import me.domino.fa2.data.local.KeyValueStorage
import me.domino.fa2.data.network.CookiePersistence
import me.domino.fa2.data.network.FaCookiesStorage
import me.domino.fa2.data.network.FaHtmlDataSource
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.data.network.UserAgentStorage
import me.domino.fa2.data.network.endpoint.HomeEndpoint
import me.domino.fa2.data.repository.AuthRepository
import me.domino.fa2.fake.TestFixtures
import me.domino.fa2.util.FaUrls
import okio.FileSystem
import okio.Path.Companion.toPath

@OptIn(ExperimentalCoroutinesApi::class)
class AuthScreenModelTest {
  private val dispatcher = StandardTestDispatcher()

  @BeforeTest
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun loginMethodDefaultsToWebView() =
      runTest(dispatcher.scheduler) {
        val fixture = createAuthScreenModelFixture()
        assertEquals(AuthLoginMethod.WebView, fixture.screenModel.loginMethod().value)
      }

  @Test
  fun confirmWebViewLoginSyncsCookieAndUaThenAuthenticates() =
      runTest(dispatcher.scheduler) {
        val fixture = createAuthScreenModelFixture()
        fixture.htmlDataSource.enqueue(
            url = FaUrls.home,
            response =
                HtmlResponseResult.Success(
                    body = TestFixtures.read("www.furaffinity.net:loggedin.html"),
                    url = FaUrls.home,
                ),
        )

        fixture.screenModel.confirmWebViewLogin(
            FakeSessionWebViewPort(
                lastLoadedUrl = FaUrls.login,
                userAgent = "TestWebView/1.0",
                cookiesByUrl =
                    mapOf(
                        FaUrls.home to "a=1; cf_clearance=cloud",
                        FaUrls.login to "b=2; __cf_bm=bm-token",
                    ),
            )
        )

        val state = fixture.screenModel.state.value
        assertIs<AuthUiState.Authenticated>(state)
        assertTrue(fixture.screenModel.cookieDraft().value.contains("a=1"))
        assertTrue(fixture.screenModel.cookieDraft().value.contains("b=2"))
        assertTrue(fixture.screenModel.cookieDraft().value.contains("cf_clearance=cloud"))
        assertTrue(fixture.screenModel.cookieDraft().value.contains("__cf_bm=bm-token"))
        assertEquals("TestWebView/1.0", fixture.userAgentStorage.currentUserAgent())
      }

  @Test
  fun confirmWebViewLoginKeepsInvalidStateWhenProbeFails() =
      runTest(dispatcher.scheduler) {
        val fixture = createAuthScreenModelFixture()
        fixture.htmlDataSource.enqueue(
            url = FaUrls.home,
            response =
                HtmlResponseResult.Success(
                    body = TestFixtures.read("www.furaffinity.net:loggedout.html"),
                    url = FaUrls.home,
                ),
        )

        fixture.screenModel.confirmWebViewLogin(
            FakeSessionWebViewPort(
                lastLoadedUrl = FaUrls.login,
                userAgent = "TestWebView/1.0",
                cookiesByUrl = mapOf(FaUrls.login to "a=1; cf_clearance=cloud"),
            )
        )

        val state = fixture.screenModel.state.value
        assertIs<AuthUiState.AuthInvalid>(state)
        assertTrue(fixture.screenModel.webViewState().value.statusMessage.contains("登录未完成"))
      }

  @Test
  fun submitCookieShowsProbeFailedWhenProbeThrows() =
      runTest(dispatcher.scheduler) {
        val fixture = createAuthScreenModelFixture()
        fixture.htmlDataSource.enqueueFailure(
            url = FaUrls.home,
            error = IllegalStateException("boom"),
        )

        fixture.screenModel.updateCookieDraft("a=1")
        fixture.screenModel.submitCookie()
        runCurrent()

        val state = fixture.screenModel.state.value
        val failed = assertIs<AuthUiState.ProbeFailed>(state)
        assertTrue(failed.message.contains("boom"))
      }
}

private data class AuthScreenModelFixture(
    val screenModel: AuthScreenModel,
    val htmlDataSource: ScriptedAuthHtmlDataSource,
    val userAgentStorage: UserAgentStorage,
)

private fun createAuthScreenModelFixture(): AuthScreenModelFixture {
  val htmlDataSource = ScriptedAuthHtmlDataSource()
  val cookiePersistence = InMemoryCookiePersistence()
  val cookiesStorage = FaCookiesStorage(cookiePersistence)
  val randomSuffix = Random.nextLong().toString().replace('-', '0')
  val dataStorePath =
      "${FileSystem.SYSTEM_TEMPORARY_DIRECTORY}/fa2-auth-screen-$randomSuffix.preferences_pb"
          .toPath()
  val userAgentStorage =
      UserAgentStorage(
          KeyValueStorage(
              PreferenceDataStoreFactory.createWithPath(produceFile = { dataStorePath })
          )
      )
  val repository =
      AuthRepository(
          AuthDataSource(
              homeEndpoint = HomeEndpoint(htmlDataSource),
              cookiesStorage = cookiesStorage,
              userAgentStorage = userAgentStorage,
          )
      )
  return AuthScreenModelFixture(
      screenModel = AuthScreenModel(repository),
      htmlDataSource = htmlDataSource,
      userAgentStorage = userAgentStorage,
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

private class ScriptedAuthHtmlDataSource : FaHtmlDataSource {
  private val queueByUrl: MutableMap<String, ArrayDeque<ScriptedResult>> = mutableMapOf()

  fun enqueue(url: String, response: HtmlResponseResult) {
    val queue = queueByUrl.getOrPut(url) { ArrayDeque() }
    queue.addLast(ScriptedResult.Success(response))
  }

  fun enqueueFailure(url: String, error: Throwable) {
    val queue = queueByUrl.getOrPut(url) { ArrayDeque() }
    queue.addLast(ScriptedResult.Failure(error))
  }

  override suspend fun get(url: String): HtmlResponseResult =
      when (val next = queueByUrl[url]?.removeFirstOrNull()) {
        is ScriptedResult.Success -> next.response
        is ScriptedResult.Failure -> throw next.error
        null ->
            HtmlResponseResult.Error(statusCode = 500, message = "No scripted response for $url")
      }
}

private sealed interface ScriptedResult {
  data class Success(val response: HtmlResponseResult) : ScriptedResult

  data class Failure(val error: Throwable) : ScriptedResult
}

private class FakeSessionWebViewPort(
    override val lastLoadedUrl: String?,
    private val userAgent: String,
    private val cookiesByUrl: Map<String, String>,
) : SessionWebViewPort {
  val loadedUrls: MutableList<String> = mutableListOf()
  val injectedCookies: MutableMap<String, String> = mutableMapOf()

  override fun loadUrl(url: String) {
    loadedUrls += url
  }

  override suspend fun captureCookieHeader(url: String): String = cookiesByUrl[url].orEmpty()

  override suspend fun injectCookieHeader(url: String, cookieHeader: String) {
    injectedCookies[url] = cookieHeader
  }

  override suspend fun readUserAgent(): String = userAgent
}
