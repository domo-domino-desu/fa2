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
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.domino.fa2.util.logging.FaLog

private val coilImageLog = FaLog.withTag("CoilImage")

/** 给 Coil 安装可观测下载进度的 Ktor fetcher。 */
@OptIn(ExperimentalCoilApi::class)
fun ImageLoader.Builder.installCoilImageProgressSupport(
    /** 进度追踪器。 */
    progressTracker: ImageProgressTracker,
    /** FA Cookie 存储。 */
    cookiesStorage: FaCookiesStorage,
    /** User-Agent 存储。 */
    userAgentStorage: UserAgentStorage,
): ImageLoader.Builder {
  val imageClient =
      createImageProgressHttpClient(
          progressTracker = progressTracker,
          cookiesStorage = cookiesStorage,
          userAgentStorage = userAgentStorage,
      )
  return components { add(KtorNetworkFetcherFactory(httpClient = imageClient)) }
}

/** 构建带下载进度上报的图片专用 HttpClient。 */
private fun createImageProgressHttpClient(
    /** 进度追踪器。 */
    progressTracker: ImageProgressTracker,
    /** FA Cookie 存储。 */
    cookiesStorage: FaCookiesStorage,
    /** User-Agent 存储。 */
    userAgentStorage: UserAgentStorage,
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
      val isFaImageHost = isFuraffinityHost(request.url.host)
      userAgentStorage.loadPersistedIfNeeded()
      val cookieHeader = if (isFaImageHost) cookiesStorage.loadRawCookieHeader() else ""
      val cookieNames = extractCookieNames(cookieHeader)
      if (cookieHeader.isNotBlank()) {
        request.header(HttpHeaders.Cookie, cookieHeader)
      }
      request.header(HttpHeaders.UserAgent, userAgentStorage.currentUserAgent())
      request.header(
          HttpHeaders.Accept,
          "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
      )
      request.header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
      if (isFaImageHost) {
        request.header(HttpHeaders.Referrer, "https://www.furaffinity.net/")
      }

      coilImageLog.d {
        val cookiesText = cookieNames.ifEmpty { listOf("-") }.joinToString(",")
        "图片请求开始 -> method=${request.method.value}, url=$progressKey, " +
            "faHost=$isFaImageHost, cookies=$cookiesText"
      }
      progressTracker.markRequestStarted(progressKey)
      request.onDownload { bytesRead, totalBytes ->
        progressTracker.updateDownloadProgress(progressKey, bytesRead, totalBytes ?: -1L)
      }
      // Save the response body once so every waiter can consume the same call safely.
      try {
        val call = execute(request).save()
        val status = call.response.status
        val contentType = call.response.headers["Content-Type"].orEmpty().ifBlank { "-" }
        val contentLength = call.response.headers["Content-Length"].orEmpty().ifBlank { "-" }
        val cfRay = call.response.headers["cf-ray"].orEmpty().ifBlank { "-" }
        cookiesStorage.mergeSetCookieValues(
            call.response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
        )
        val message =
            "图片请求结束 -> status=${status.value}, contentType=$contentType, " +
                "contentLength=$contentLength, cf-ray=$cfRay, url=$progressKey"
        if (status.value >= 400) {
          coilImageLog.w { message }
        } else {
          coilImageLog.d { message }
        }
        call
      } catch (error: Throwable) {
        coilImageLog.e(error) { "图片请求异常 -> url=$progressKey" }
        throw error
      }
    }
  }
  return client
}

private fun isFuraffinityHost(host: String): Boolean {
  val normalized = host.lowercase()
  return normalized == "furaffinity.net" || normalized.endsWith(".furaffinity.net")
}

private fun extractCookieNames(cookieHeader: String): List<String> =
    cookieHeader
        .split(';')
        .mapNotNull { token ->
          val name = token.substringBefore('=').trim()
          name.takeIf { it.isNotBlank() }
        }
        .distinct()

/** 按图片 URL 合并同一时刻的并发请求。 */
private class InFlightImageRequestDeduplicator {
  /** 保护 callsByKey 并发访问的互斥锁。 */
  private val mutex = Mutex()

  /** 当前正在进行中的请求，按 progressKey 索引。 */
  private val callsByKey = mutableMapOf<String, CompletableDeferred<HttpClientCall>>()

  /** 若已有相同 key 的请求在途则等待其结果，否则发起新请求并广播结果。 */
  suspend fun awaitOrExecute(
      progressKey: String,
      executeRequest: suspend () -> HttpClientCall,
  ): HttpClientCall {
    val existing = mutex.withLock { callsByKey[progressKey] }
    if (existing != null) {
      coilImageLog.d { "图片请求复用在途任务 -> url=$progressKey" }
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
      coilImageLog.d { "图片请求复用竞态任务 -> url=$progressKey" }
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
