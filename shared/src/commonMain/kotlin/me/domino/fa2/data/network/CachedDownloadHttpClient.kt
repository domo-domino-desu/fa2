package me.domino.fa2.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.save
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.BodyProgress
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.onDownload
import io.ktor.client.plugins.plugin
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.domino.fa2.util.logging.FaLog

private val cachedDownloadLog = FaLog.withTag("CachedDownload")

/** 构建图片、OCR、附件下载共用的缓存 HTTP 客户端。 */
fun createCachedDownloadHttpClient(
    /** 进度追踪器。 */
    progressTracker: ImageProgressTracker,
    /** FA Cookie 存储。 */
    cookiesStorage: FaCookiesStorage,
    /** User-Agent 存储。 */
    userAgentStorage: UserAgentStorage,
): HttpClient {
  val deduplicator = InFlightDownloadRequestDeduplicator()
  val client = HttpClient {
    expectSuccess = false
    install(HttpCache)
    install(BodyProgress)
  }
  return client.installCachedDownloadInterceptors(
      progressTracker = progressTracker,
      cookiesStorage = cookiesStorage,
      userAgentStorage = userAgentStorage,
      deduplicator = deduplicator,
  )
}

/** 构建带指定 engine 的缓存下载客户端，主要用于测试。 */
fun createCachedDownloadHttpClient(
    engine: HttpClientEngine,
    /** 进度追踪器。 */
    progressTracker: ImageProgressTracker,
    /** FA Cookie 存储。 */
    cookiesStorage: FaCookiesStorage,
    /** User-Agent 存储。 */
    userAgentStorage: UserAgentStorage,
): HttpClient {
  val deduplicator = InFlightDownloadRequestDeduplicator()
  val client =
      HttpClient(engine) {
        expectSuccess = false
        install(HttpCache)
        install(BodyProgress)
      }
  return client.installCachedDownloadInterceptors(
      progressTracker = progressTracker,
      cookiesStorage = cookiesStorage,
      userAgentStorage = userAgentStorage,
      deduplicator = deduplicator,
  )
}

private fun HttpClient.installCachedDownloadInterceptors(
    progressTracker: ImageProgressTracker,
    cookiesStorage: FaCookiesStorage,
    userAgentStorage: UserAgentStorage,
    deduplicator: InFlightDownloadRequestDeduplicator,
): HttpClient {
  plugin(HttpSend).intercept { request ->
    val progressKey = normalizeProgressKey(request.url.toString())
    if (progressKey.isBlank()) {
      return@intercept execute(request)
    }
    deduplicator.awaitOrExecute(deduplicationKey = "${request.method.value} $progressKey") {
      val isFaHost = isFuraffinityHost(request.url.host)
      userAgentStorage.loadPersistedIfNeeded()
      val cookieHeader = if (isFaHost) cookiesStorage.loadRawCookieHeader() else ""
      val cookieNames = extractCookieNames(cookieHeader)
      if (cookieHeader.isNotBlank() && request.headers[HttpHeaders.Cookie].isNullOrBlank()) {
        request.header(HttpHeaders.Cookie, cookieHeader)
      }
      if (request.headers[HttpHeaders.UserAgent].isNullOrBlank()) {
        request.header(HttpHeaders.UserAgent, userAgentStorage.currentUserAgent())
      }
      if (request.headers[HttpHeaders.Accept].isNullOrBlank()) {
        request.header(HttpHeaders.Accept, "*/*")
      }
      if (request.headers[HttpHeaders.AcceptLanguage].isNullOrBlank()) {
        request.header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
      }
      if (isFaHost && request.headers[HttpHeaders.Referrer].isNullOrBlank()) {
        request.header(HttpHeaders.Referrer, "https://www.furaffinity.net/")
      }

      cachedDownloadLog.d {
        val cookiesText = cookieNames.ifEmpty { listOf("-") }.joinToString(",")
        "缓存下载请求开始 -> method=${request.method.value}, url=$progressKey, " +
            "faHost=$isFaHost, cookies=$cookiesText, accept=${request.headers[HttpHeaders.Accept]}"
      }
      progressTracker.markRequestStarted(progressKey)
      request.onDownload { bytesRead, totalBytes ->
        progressTracker.updateDownloadProgress(progressKey, bytesRead, totalBytes ?: -1L)
      }

      try {
        val call = execute(request).save()
        val status = call.response.status
        val contentType = call.response.headers[HttpHeaders.ContentType].orEmpty().ifBlank { "-" }
        val contentLength =
            call.response.headers[HttpHeaders.ContentLength].orEmpty().ifBlank { "-" }
        val cfRay = call.response.headers["cf-ray"].orEmpty().ifBlank { "-" }
        cookiesStorage.mergeSetCookieValues(
            call.response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
        )
        val message =
            "缓存下载请求结束 -> status=${status.value}, contentType=$contentType, " +
                "contentLength=$contentLength, cf-ray=$cfRay, url=$progressKey"
        if (status.value >= 400) {
          cachedDownloadLog.w { message }
        } else {
          cachedDownloadLog.d { message }
        }
        call
      } catch (error: Throwable) {
        cachedDownloadLog.e(error) { "缓存下载请求异常 -> url=$progressKey" }
        throw error
      }
    }
  }
  return this
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

/** 按下载 URL 合并同一时刻的并发请求。 */
private class InFlightDownloadRequestDeduplicator {
  /** 保护 callsByKey 并发访问的互斥锁。 */
  private val mutex = Mutex()

  /** 当前正在进行中的请求，按 progressKey 索引。 */
  private val callsByKey = mutableMapOf<String, CompletableDeferred<HttpClientCall>>()

  /** 若已有相同 key 的请求在途则等待其结果，否则发起新请求并广播结果。 */
  suspend fun awaitOrExecute(
      deduplicationKey: String,
      executeRequest: suspend () -> HttpClientCall,
  ): HttpClientCall {
    val existing = mutex.withLock { callsByKey[deduplicationKey] }
    if (existing != null) {
      cachedDownloadLog.d { "缓存下载请求复用在途任务 -> key=$deduplicationKey" }
      return existing.await()
    }

    val owner = CompletableDeferred<HttpClientCall>()
    val racing =
        mutex.withLock {
          val inFlight = callsByKey[deduplicationKey]
          if (inFlight != null) {
            inFlight
          } else {
            callsByKey[deduplicationKey] = owner
            null
          }
        }

    if (racing != null) {
      cachedDownloadLog.d { "缓存下载请求复用竞态任务 -> key=$deduplicationKey" }
      return racing.await()
    }

    return try {
      executeRequest().also { call -> owner.complete(call) }
    } catch (error: Throwable) {
      owner.completeExceptionally(error)
      throw error
    } finally {
      mutex.withLock { callsByKey.remove(deduplicationKey, owner) }
    }
  }
}
