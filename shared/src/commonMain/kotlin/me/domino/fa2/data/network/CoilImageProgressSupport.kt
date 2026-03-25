package me.domino.fa2.data.network

import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.save
import io.ktor.client.plugins.BodyProgress
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.onDownload
import io.ktor.client.plugins.plugin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 给 Coil 安装可观测下载进度的 Ktor fetcher。 */
@OptIn(ExperimentalCoilApi::class)
fun ImageLoader.Builder.installCoilImageProgressSupport(
    /** 进度追踪器。 */
    progressTracker: ImageProgressTracker
): ImageLoader.Builder {
  val imageClient = createImageProgressHttpClient(progressTracker)
  return components { add(KtorNetworkFetcherFactory(httpClient = imageClient)) }
}

/** 构建带下载进度上报的图片专用 HttpClient。 */
private fun createImageProgressHttpClient(
    /** 进度追踪器。 */
    progressTracker: ImageProgressTracker
): HttpClient {
  val deduplicator = InFlightImageRequestDeduplicator()
  val client = HttpClient {
    expectSuccess = false
    install(BodyProgress)
  }
  client.plugin(HttpSend).intercept { request ->
    val progressKey = normalizeProgressKey(request.url.toString())
    if (progressKey.isBlank()) {
      return@intercept execute(request)
    }
    deduplicator.awaitOrExecute(progressKey) {
      progressTracker.markRequestStarted(progressKey)
      request.onDownload { bytesRead, totalBytes ->
        progressTracker.updateDownloadProgress(progressKey, bytesRead, totalBytes ?: -1L)
      }
      // Save the response body once so every waiter can consume the same call safely.
      execute(request).save()
    }
  }
  return client
}

/** 按图片 URL 合并同一时刻的并发请求。 */
private class InFlightImageRequestDeduplicator {
  private val mutex = Mutex()
  private val callsByKey = mutableMapOf<String, CompletableDeferred<HttpClientCall>>()

  suspend fun awaitOrExecute(
      progressKey: String,
      executeRequest: suspend () -> HttpClientCall,
  ): HttpClientCall {
    val existing = mutex.withLock { callsByKey[progressKey] }
    if (existing != null) {
      return existing.await()
    }

    val owner = CompletableDeferred<HttpClientCall>()
    val racing =
        mutex.withLock {
          val inFlight = callsByKey[progressKey]
          if (inFlight != null) {
            inFlight
          } else {
            callsByKey[progressKey] = owner
            null
          }
        }

    if (racing != null) {
      return racing.await()
    }

    return try {
      executeRequest().also { call -> owner.complete(call) }
    } catch (error: Throwable) {
      owner.completeExceptionally(error)
      throw error
    } finally {
      mutex.withLock { callsByKey.remove(progressKey, owner) }
    }
  }
}
