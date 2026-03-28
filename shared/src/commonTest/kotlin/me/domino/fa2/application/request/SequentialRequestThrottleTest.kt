package me.domino.fa2.application.request

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SequentialRequestThrottleTest {
  @Test
  fun firstRequestIsImmediateAndLaterRequestsWaitFixedInterval() = runTest {
    val throttle = SequentialRequestThrottle(defaultSequentialRequestThrottleMs)
    val requestTimes = mutableListOf<Long>()

    backgroundScope.launch {
      throttle.awaitReady()
      requestTimes += testScheduler.currentTime
      throttle.awaitReady()
      requestTimes += testScheduler.currentTime
      throttle.awaitReady()
      requestTimes += testScheduler.currentTime
    }

    runCurrent()
    assertEquals(listOf(0L), requestTimes)

    advanceTimeBy(defaultSequentialRequestThrottleMs - 1)
    runCurrent()
    assertEquals(listOf(0L), requestTimes)

    advanceTimeBy(1)
    runCurrent()
    assertEquals(listOf(0L, defaultSequentialRequestThrottleMs), requestTimes)

    advanceTimeBy(defaultSequentialRequestThrottleMs)
    runCurrent()
    assertEquals(
        listOf(
            0L,
            defaultSequentialRequestThrottleMs,
            defaultSequentialRequestThrottleMs * 2,
        ),
        requestTimes,
    )
  }
}
