package me.domino.fa2.data.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizeHtmlResult
import me.domino.fa2.util.logging.summarizeUrl

/** HTML 数据源抽象。 */
interface FaHtmlDataSource {
  /**
   * 读取指定 URL 的 HTML 响应并完成分类。
   *
   * @param url 目标页面地址。
   */
  suspend fun get(url: String): HtmlResponseResult
}

/** 最小 HTTP 数据源实现。 负责加 Cookie、合并 Set-Cookie、并调用响应分类器。 */
class FaHttpClient(
    /** Ktor 客户端实例。 */
    private val client: HttpClient,
    /** Cookie 读写存储。 */
    private val cookiesStorage: FaCookiesStorage,
    /** UA 读写存储。 */
    private val userAgentStorage: UserAgentStorage,
) : FaHtmlDataSource {
  private val log = FaLog.withTag("FaHttpClient")

  /**
   * 发起 GET 请求并返回分类结果。
   *
   * @param url 页面地址。
   */
  override suspend fun get(url: String): HtmlResponseResult {
    val safeUrl = summarizeUrl(url)
    log.d { "请求页面 -> 开始(url=$safeUrl)" }
    userAgentStorage.loadPersistedIfNeeded()
    val cookieHeader = cookiesStorage.loadRawCookieHeader()
    val userAgent = userAgentStorage.currentUserAgent()
    val cookieNames = extractCookieNames(cookieHeader)
    log.d {
      val cookiesText = cookieNames.ifEmpty { listOf("-") }.joinToString(",")
      "请求页面 -> 上下文(cookies=$cookiesText,ua=${if (userAgent.isBlank()) "空" else "已设置"})"
    }
    val response =
        client.get(url) {
          if (cookieHeader.isNotBlank()) {
            header(HttpHeaders.Cookie, cookieHeader)
          }
          header(HttpHeaders.UserAgent, userAgent)
          header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
          header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
        }

    val body = response.bodyAsText()
    val headers = response.headers.entries().associate { (key, values) -> key to values }
    val setCookieValues = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
    cookiesStorage.mergeSetCookieValues(setCookieValues)

    val classified =
        HtmlResponseResult.classify(
            statusCode = response.status.value,
            headers = headers,
            body = body,
            url = url,
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
    return classified
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
