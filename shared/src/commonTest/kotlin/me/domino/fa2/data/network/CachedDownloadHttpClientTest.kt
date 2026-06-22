package me.domino.fa2.data.fa.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.fa.media.ImageProgressTracker
import me.domino.fa2.data.fa.media.createCachedDownloadHttpClient
import me.domino.fa2.data.fa.session.CookiePersistence
import me.domino.fa2.data.fa.session.FaCookiesStorage
import me.domino.fa2.data.fa.session.UserAgentStorage
import me.domino.fa2.data.local.KeyValueStorage
import okio.FileSystem
import okio.Path.Companion.toPath

class CachedDownloadHttpClientTest {
  @Test
  fun addsAuthenticatedFaDownloadHeadersAndMergesSetCookie() = runTest {
    val cookiesStorage = FaCookiesStorage(InMemoryCookiePersistence("a=1"))
    val userAgentStorage = createTestUserAgentStorage()
    userAgentStorage.saveOverride("Test UA")
    var capturedCookie: String? = null
    var capturedUserAgent: String? = null
    var capturedAccept: String? = null
    var capturedAcceptLanguage: String? = null
    var capturedReferrer: String? = null

    val client =
        createCachedDownloadHttpClient(
            engine =
                MockEngine { request ->
                  capturedCookie = request.headers[HttpHeaders.Cookie]
                  capturedUserAgent = request.headers[HttpHeaders.UserAgent]
                  capturedAccept = request.headers[HttpHeaders.Accept]
                  capturedAcceptLanguage = request.headers[HttpHeaders.AcceptLanguage]
                  capturedReferrer = request.headers[HttpHeaders.Referrer]
                  respond(
                      content = byteArrayOf(1, 2, 3),
                      status = HttpStatusCode.OK,
                      headers =
                          headersOf(
                              HttpHeaders.ContentType to listOf("image/png"),
                              HttpHeaders.SetCookie to listOf("b=2; Path=/"),
                          ),
                  )
                },
            progressTracker = ImageProgressTracker(),
            cookiesStorage = cookiesStorage,
            userAgentStorage = userAgentStorage,
        )

    client.get("https://www.furaffinity.net/image.png").body<ByteArray>()

    assertEquals("a=1", capturedCookie)
    assertEquals("Test UA", capturedUserAgent)
    assertEquals("*/*", capturedAccept)
    assertEquals("en-US,en;q=0.9", capturedAcceptLanguage)
    assertEquals("https://www.furaffinity.net/", capturedReferrer)
    assertEquals("a=1; b=2", cookiesStorage.loadRawCookieHeader())
  }

  @Test
  fun preservesRequestSpecificAcceptHeader() = runTest {
    val cookiesStorage = FaCookiesStorage(InMemoryCookiePersistence())
    val userAgentStorage = createTestUserAgentStorage()
    var capturedAccept: String? = null

    val client =
        createCachedDownloadHttpClient(
            engine =
                MockEngine { request ->
                  capturedAccept = request.headers[HttpHeaders.Accept]
                  respond(content = byteArrayOf(1), status = HttpStatusCode.OK)
                },
            progressTracker = ImageProgressTracker(),
            cookiesStorage = cookiesStorage,
            userAgentStorage = userAgentStorage,
        )

    client
        .get("https://www.furaffinity.net/file.txt") { header(HttpHeaders.Accept, "*/*") }
        .body<ByteArray>()

    assertEquals("*/*", capturedAccept)
  }
}

internal fun createTestUserAgentStorage(): UserAgentStorage {
  val randomSuffix = Random.nextLong().toString().replace('-', '0')
  val dataStorePath =
      "${FileSystem.SYSTEM_TEMPORARY_DIRECTORY}/fa2-cached-download-$randomSuffix.preferences_pb"
          .toPath()
  return UserAgentStorage(
      KeyValueStorage(PreferenceDataStoreFactory.createWithPath(produceFile = { dataStorePath }))
  )
}

internal class InMemoryCookiePersistence(initialCookieHeader: String = "") : CookiePersistence {
  private var rawCookieHeader: String = initialCookieHeader

  override suspend fun loadCookieHeader(): String = rawCookieHeader

  override suspend fun saveCookieHeader(value: String) {
    rawCookieHeader = value
  }

  override suspend fun deleteCookieHeader() {
    rawCookieHeader = ""
  }
}
