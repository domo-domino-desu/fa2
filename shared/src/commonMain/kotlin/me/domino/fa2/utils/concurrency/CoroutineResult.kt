package me.domino.fa2.utils.concurrency

import kotlinx.coroutines.CancellationException

/** Result wrapper for suspend work that preserves structured-concurrency cancellation. */
suspend inline fun <T> runSuspendCatching(block: suspend () -> T): Result<T> =
    try {
      Result.success(block())
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Throwable) {
      Result.failure(error)
    }
