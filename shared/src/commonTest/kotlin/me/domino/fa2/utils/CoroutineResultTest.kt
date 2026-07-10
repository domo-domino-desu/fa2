package me.domino.fa2.utils

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import me.domino.fa2.utils.concurrency.runSuspendCatching

class CoroutineResultTest {
  @Test
  fun cancellationIsNeverConvertedToFailure() = runTest {
    assertFailsWith<CancellationException> {
      runSuspendCatching<Unit> { throw CancellationException("cancelled") }
    }
  }

  @Test
  fun ordinaryFailureIsCaptured() = runTest {
    val result = runSuspendCatching<Unit> { error("failed") }
    assertTrue(result.exceptionOrNull() is IllegalStateException)
  }
}
