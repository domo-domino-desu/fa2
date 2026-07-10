package me.domino.fa2.data.fa.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.fa.session.FaCookiesStorage
import me.domino.fa2.data.fa.session.FaHttpClient
import me.domino.fa2.data.fa.session.UserAgentStorage
import me.domino.fa2.data.fa.session.createFaPageHttpClient
import me.domino.fa2.data.local.KeyValueStorage
import okio.FileSystem
import okio.Path.Companion.toPath

class FaHttpClientRedirectTest {
  @Test
  fun crossOriginRedirectCannotForwardFaCookie() = runTest {
    val cookiesByHost = mutableMapOf<String, String?>()
    val engine = MockEngine { request ->
      cookiesByHost[request.url.host] = request.headers[HttpHeaders.Cookie]
      if (request.url.host == "www.furaffinity.net") {
        respond(
            content = "",
            status = HttpStatusCode.Found,
            headers = headersOf(HttpHeaders.Location, "https://outside.example/page"),
        )
      } else {
        respond(content = "external", status = HttpStatusCode.OK)
      }
    }
    val keyValueStorage =
        KeyValueStorage(
            PreferenceDataStoreFactory.createWithPath {
              val path =
                  (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString().trimEnd('/') +
                          "/fa-http-${Random.nextLong()}.preferences_pb")
                      .toPath()
              path
            }
        )
    val userAgentStorage = UserAgentStorage(keyValueStorage).apply { saveOverride("test-agent") }
    val client = createFaPageHttpClient(engine)
    val faHttpClient =
        FaHttpClient(
            client = client,
            cookiesStorage = FaCookiesStorage(InMemoryCookiePersistence("session=secret")),
            userAgentStorage = userAgentStorage,
        )

    faHttpClient.get("https://www.furaffinity.net/")

    assertEquals("session=secret", cookiesByHost["www.furaffinity.net"])
    assertEquals(null, cookiesByHost["outside.example"])
    client.close()
  }
}
