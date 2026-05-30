package me.domino.fa2.data.network.endpoint

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import me.domino.fa2.data.network.FaCookiesStorage
import me.domino.fa2.data.network.UserAgentStorage
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizeUrl

/** FA 标签屏蔽操作的接口端点 URL。 */
internal const val TAG_BLOCKING_URL: String = "https://www.furaffinity.net/route/tag_blocking"

/** 社交动作 HTTP 响应的数据封装。 */
internal data class SocialActionHttpResponse(
    /** HTTP 状态码。 */
    val statusCode: Int,
    /** 响应头映射。 */
    val headers: Map<String, List<String>>,
    /** 响应体文本。 */
    val body: String,
)

/** 执行社交动作（GET/POST）的 HTTP 传输接口。 */
internal interface SocialActionHttpTransport {
  /** 向指定 URL 发起 GET 请求。 */
  suspend fun get(url: String): SocialActionHttpResponse

  /** 向 FA 发起添加或移除标签屏蔽的 POST 请求。 */
  suspend fun postTagBlocklist(
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): SocialActionHttpResponse
}

/** SocialActionHttpTransport 的默认实现，携带 Cookie 与 UserAgent。 */
internal class DefaultSocialActionHttpTransport(
    private val client: HttpClient,
    private val cookiesStorage: FaCookiesStorage,
    private val userAgentStorage: UserAgentStorage,
) : SocialActionHttpTransport {
  /** 日志标签。 */
  private val log = FaLog.withTag("SocialActionHttpTransport")

  override suspend fun get(url: String): SocialActionHttpResponse {
    log.d { "社交动作传输 -> GET(url=${summarizeUrl(url)})" }
    val context = loadRequestContext()
    val response =
        client.get(url) {
          applyCommonHeaders(context = context)
          header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
        }
    return response.toSocialActionHttpResponse(cookiesStorage).also { result ->
      log.d { "社交动作传输 -> GET完成(status=${result.statusCode})" }
    }
  }

  override suspend fun postTagBlocklist(
      tagName: String,
      nonce: String,
      toAdd: Boolean,
  ): SocialActionHttpResponse {
    log.d { "社交动作传输 -> POST标签屏蔽(tag=$tagName,toAdd=$toAdd)" }
    val context = loadRequestContext()
    val action = if (toAdd) "add-tag" else "remove-tag"
    val response =
        client.post(TAG_BLOCKING_URL) {
          applyCommonHeaders(context = context)
          header(HttpHeaders.Accept, "application/json,text/plain,*/*")
          setBody(
              FormDataContent(
                  Parameters.build {
                    append("action", action)
                    append("key", nonce)
                    append("tag_name", tagName)
                  }
              )
          )
        }
    return response.toSocialActionHttpResponse(cookiesStorage).also { result ->
      log.d { "社交动作传输 -> POST标签屏蔽完成(status=${result.statusCode})" }
    }
  }

  /** 加载当前请求所需的 Cookie 与 UserAgent 上下文。 */
  private suspend fun loadRequestContext(): SocialActionRequestContext {
    userAgentStorage.loadPersistedIfNeeded()
    return SocialActionRequestContext(
            cookieHeader = cookiesStorage.loadRawCookieHeader(),
            userAgent = userAgentStorage.currentUserAgent(),
        )
        .also { context ->
          log.d {
            "社交动作传输 -> 读取上下文(cookie=${if (context.cookieHeader.isBlank()) "空" else "已设置"},ua=${if (context.userAgent.isBlank()) "空" else "已设置"})"
          }
        }
  }

  /** 向请求附加通用请求头（Cookie、UserAgent、Accept-Language）。 */
  private fun io.ktor.client.request.HttpRequestBuilder.applyCommonHeaders(
      context: SocialActionRequestContext
  ) {
    if (context.cookieHeader.isNotBlank()) {
      header(HttpHeaders.Cookie, context.cookieHeader)
    }
    header(HttpHeaders.UserAgent, context.userAgent)
    header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
  }

  /** 将 Ktor HttpResponse 转换为 SocialActionHttpResponse 并同步 Cookie。 */
  private suspend fun HttpResponse.toSocialActionHttpResponse(
      cookiesStorage: FaCookiesStorage
  ): SocialActionHttpResponse {
    val body = bodyAsText()
    val headersMap = headers.entries().associate { (key, values) -> key to values }
    val setCookieValues = headers.getAll(HttpHeaders.SetCookie).orEmpty()
    cookiesStorage.mergeSetCookieValues(setCookieValues)
    return SocialActionHttpResponse(
        statusCode = status.value,
        headers = headersMap,
        body = body,
    )
  }
}

/** 发起社交动作请求所需的上下文（Cookie 与 UserAgent）。 */
private data class SocialActionRequestContext(
    /** Cookie 请求头内容。 */
    val cookieHeader: String,
    /** UserAgent 字符串。 */
    val userAgent: String,
)
