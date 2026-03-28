package me.domino.fa2.application.request

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val defaultSequentialRequestThrottleMs: Long = 500L

/**
 * 为同一批顺序请求施加固定间隔节流。
 *
 * 首次请求不等待，后续每次请求前至少等待 [minIntervalMs]。
 */
class SequentialRequestThrottle(
    private val minIntervalMs: Long = defaultSequentialRequestThrottleMs,
    private val delayBlock: suspend (Long) -> Unit = { delay(it) },
) {
  private val mutex = Mutex()
  private var hasRequested: Boolean = false

  suspend fun awaitReady() {
    mutex.withLock {
      if (hasRequested && minIntervalMs > 0L) {
        delayBlock(minIntervalMs)
      }
      hasRequested = true
    }
  }
}
