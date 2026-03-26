package me.domino.fa2.app.challenge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import me.domino.fa2.data.network.challenge.CfChallengeSignal

class ChallengeSessionStoreTest {
  @Test
  fun acquireReusesActiveSessionAndTracksStateTransitions() = runTest {
    val store = ChallengeSessionStore()
    val first = store.acquire(CfChallengeSignal(requestUrl = "https://example.com", cfRay = "ray1"))
    val second =
        store.acquire(CfChallengeSignal(requestUrl = "https://example.com/other", cfRay = "ray2"))

    assertTrue(first.created)
    assertFalse(second.created)
    assertSame(first.session, second.session)
    assertEquals(
        CfChallengeUiState.Active(
            triggerUrl = "https://example.com",
            cfRay = "ray1",
            status = CfChallengeStatus.AwaitingUserAction,
        ),
        store.state.value,
    )

    store.markVerifying(first.session)
    assertEquals(
        CfChallengeStatus.Verifying,
        (store.state.value as CfChallengeUiState.Active).status,
    )

    store.markVerificationFailed(first.session, "boom")
    assertEquals(
        CfChallengeStatus.VerificationFailed("boom"),
        (store.state.value as CfChallengeUiState.Active).status,
    )

    val completed = store.complete(result = true)
    assertSame(first.session, completed)
    assertTrue(first.session.deferred.await())
    assertEquals(CfChallengeUiState.Idle, store.state.value)
  }
}
