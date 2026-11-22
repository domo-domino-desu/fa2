package me.domino.fa2.data.remote.fa.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.local.KeyValueStorage
import me.domino.fa2.data.remote.fa.core.FaHtmlDataSource
import me.domino.fa2.data.remote.fa.core.HtmlResponseResult
import me.domino.fa2.fake.TestFixtures
import okio.FileSystem
import okio.Path.Companion.toPath

class AuthExpiryFallbackTest {
  @Test
  fun serverLoggedOutResponseRequestsLoginAndCompletingLoginClearsTheNavigationLatch() = runTest {
    val path =
        "${FileSystem.SYSTEM_TEMPORARY_DIRECTORY}/fa2-auth-expiry-${Random.nextLong()}.preferences_pb"
            .toPath()
    val store =
        KeyValueStorage(
            PreferenceDataStoreFactory.createWithPath(
                scope = backgroundScope,
                produceFile = { path },
            )
        )
    val pending = PendingFaRouteStore()
    val controller = DefaultAuthSessionController(AuthSessionProfileStore(store), pending)
    val requested = "https://www.furaffinity.net/msg/submissions/"
    val delegate =
        object : FaHtmlDataSource {
          override suspend fun get(url: String) =
              HtmlResponseResult.classify(
                  200,
                  emptyMap(),
                  TestFixtures.read("live/home-guest.html"),
                  url,
                  "https://www.furaffinity.net/",
              )
        }
    val result = AuthAwareFaHtmlDataSource(delegate, controller).get(requested)
    assertIs<HtmlResponseResult.AuthRequired>(result)
    assertTrue(controller.needsRelogin())
    assertEquals(1L, controller.state.value.reloginRequestVersion)
    assertEquals(requested, pending.peek())
    controller.completeLogin("fixture-account")
    assertFalse(controller.needsRelogin())
    assertEquals(0L, controller.state.value.reloginRequestVersion)
    assertEquals(requested, pending.peek())
    assertEquals(requested, controller.state.value.pendingRestoreUri)
  }

  @Test
  fun loggedOutMaturePageRequestsLoginWhileAuthenticatedMaturePageStaysBlocked() {
    val mature = "This submission contains mature content."
    for ((file, expected) in
        listOf(
            "live/home-guest.html" to true,
            "live/home.html" to false,
        )) {
      val body =
          TestFixtures.read(file)
              .replace("/login\"", "/login/\"")
              .replace("/register\"", "/register/\"") + mature
      val result =
          HtmlResponseResult.classify(
              200,
              emptyMap(),
              body,
              "https://www.furaffinity.net/view/12345/",
              "https://www.furaffinity.net/view/12345/",
          )
      if (expected) assertIs<HtmlResponseResult.AuthRequired>(result)
      else assertIs<HtmlResponseResult.MatureBlocked>(result)
    }
  }
}
