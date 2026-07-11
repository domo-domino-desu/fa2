package me.domino.fa2.data.remote.fa.session

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import me.domino.fa2.data.remote.fa.core.FaHtmlDataSource
import me.domino.fa2.data.remote.fa.core.FaOriginPolicy
import me.domino.fa2.data.remote.fa.core.HtmlResponseResult
import me.domino.fa2.data.remote.fa.core.summarizeHtmlResult
import me.domino.fa2.data.remote.fa.core.toUserFacingRequestMessage
import me.domino.fa2.utils.logging.FaLog

/** Dedicated credentialed client. Redirect hops are checked before cookies can leave FA. */
fun createFaPageHttpClient(): HttpClient =
    HttpClient { expectSuccess = false }.installFaCredentialBoundary()

/** Testable factory variant with an explicit engine. */
fun createFaPageHttpClient(engine: HttpClientEngine): HttpClient =
    HttpClient(engine) { expectSuccess = false }.installFaCredentialBoundary()

private fun HttpClient.installFaCredentialBoundary(): HttpClient = also { client ->
  client.plugin(HttpSend).intercept { request ->
    if (!FaOriginPolicy.isTrustedCookieOrigin(request.url.toString())) {
      request.headers.remove(HttpHeaders.Cookie)
    }
    execute(request)
  }
}

/** FA credential boundary: validates page origins and owns Cookie/User-Agent exchange. */
class FaHttpClient(
    private val client: HttpClient,
    private val cookiesStorage: FaCookiesStorage,
    private val userAgentStorage: UserAgentStorage,
) : FaHtmlDataSource {
  private val log = FaLog.withTag("FaHttpClient")

  override suspend fun get(url: String): HtmlResponseResult {
    val safeUrl = url.trim()
    if (!FaOriginPolicy.isTrustedPageOrigin(url)) {
      log.w { "请求页面 -> 拒绝不受信任地址(url=$safeUrl)" }
      return HtmlResponseResult.Error(statusCode = 0, message = "Untrusted FA page URL")
    }
    log.d { "请求页面 -> 开始(url=$safeUrl)" }
    userAgentStorage.loadPersistedIfNeeded()
    val cookieHeader = cookiesStorage.loadRawCookieHeader()
    val userAgent = userAgentStorage.currentUserAgent()
    val cookieNames = extractCookieNames(cookieHeader)
    log.d {
      val cookiesText = cookieNames.ifEmpty { listOf("-") }.joinToString(",")
      "请求页面 -> 上下文(cookies=$cookiesText,ua=${if (userAgent.isBlank()) "空" else "已设置"})"
    }
    return try {
      val response =
          client.get(url) {
            if (cookieHeader.isNotBlank()) header(HttpHeaders.Cookie, cookieHeader)
            header(HttpHeaders.UserAgent, userAgent)
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
            header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
          }

      val body = response.bodyAsText()
      val headers = response.headers.entries().associate { (key, values) -> key to values }
      val finalUrl = response.call.request.url.toString()
      val setCookieValues = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
      if (FaOriginPolicy.isTrustedCookieOrigin(finalUrl)) {
        cookiesStorage.mergeSetCookieValues(setCookieValues)
      } else {
        log.w { "请求页面 -> 忽略跨域响应 Cookie(finalUrl=${finalUrl.trim()})" }
      }

      val classified =
          HtmlResponseResult.classify(
              statusCode = response.status.value,
              headers = headers,
              body = body,
              requestUrl = url,
              finalUrl = finalUrl,
          )
      val cfRay =
          headers.entries
              .firstOrNull { (key, _) -> key.equals("cf-ray", ignoreCase = true) }
              ?.value
              ?.firstOrNull()
              .orEmpty()
      log.d {
        val ray = if (cfRay.isBlank()) "-" else cfRay
        "请求页面 -> ${summarizeHtmlResult(classified)}(status=${response.status.value},cf-ray=$ray)"
      }
      classified
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Throwable) {
      log.e(error) { "请求页面 -> 失败(url=$safeUrl)" }
      HtmlResponseResult.Error(statusCode = 0, message = error.toUserFacingRequestMessage())
    }
  }

  private fun extractCookieNames(cookieHeader: String): List<String> =
      cookieHeader
          .split(';')
          .mapNotNull { token ->
            val name = token.substringBefore('=').trim()
            name.takeIf { it.isNotBlank() }
          }
          .distinct()
}
