package me.domino.fa2.data.fa.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.domino.fa2.utils.concurrency.SequentialRequestThrottle

@OptIn(ExperimentalCoroutinesApi::class)
class SequentialRequestThrottleTest {
  @Test
  fun firstRequestIsImmediateAndLaterRequestsWaitFixedInterval() = runTest {
    val throttleIntervalMs = 600L
    val throttle = SequentialRequestThrottle(throttleIntervalMs)
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

    advanceTimeBy(throttleIntervalMs - 1)
    runCurrent()
    assertEquals(listOf(0L), requestTimes)

    advanceTimeBy(1)
    runCurrent()
    assertEquals(listOf(0L, throttleIntervalMs), requestTimes)

    advanceTimeBy(throttleIntervalMs)
    runCurrent()
    assertEquals(
        listOf(
            0L,
            throttleIntervalMs,
            throttleIntervalMs * 2,
        ),
        requestTimes,
    )
  }
}
