package me.domino.fa2.application.request

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.domino.fa2.util.logging.FaLog

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
  private val log = FaLog.withTag("SequentialRequestThrottle")
  private val mutex = Mutex()
  private var hasRequested: Boolean = false

  suspend fun awaitReady() {
    mutex.withLock {
      if (hasRequested && minIntervalMs > 0L) {
        log.d { "顺序请求节流 -> 等待(${minIntervalMs}ms)" }
        delayBlock(minIntervalMs)
      }
      hasRequested = true
    }
  }
}
