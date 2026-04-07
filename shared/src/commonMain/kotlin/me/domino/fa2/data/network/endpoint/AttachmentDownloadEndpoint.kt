package me.domino.fa2.data.network.endpoint

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import me.domino.fa2.data.network.FaCookiesStorage
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.data.network.UserAgentStorage
import me.domino.fa2.domain.attachmenttext.extensionIn
import me.domino.fa2.domain.challenge.CfChallengeSignal
import me.domino.fa2.domain.challenge.ChallengeResolver
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizeHtmlResult
import me.domino.fa2.util.logging.summarizeUrl

interface AttachmentDownloadSource {
  suspend fun fetch(url: String, fileName: String): AttachmentDownloadResult
}

data class AttachmentDownloadPayload(
    val bytes: ByteArray,
    val contentType: String?,
)

sealed interface AttachmentDownloadResult {
  data class Success(val payload: AttachmentDownloadPayload) : AttachmentDownloadResult

  data class Challenge(val cfRay: String?) : AttachmentDownloadResult

  data class Blocked(val reason: String) : AttachmentDownloadResult

  data class Failed(val message: String) : AttachmentDownloadResult
}

/** 附件下载端点。 */
class AttachmentDownloadEndpoint(
    private val client: HttpClient,
    private val cookiesStorage: FaCookiesStorage,
    private val userAgentStorage: UserAgentStorage,
    private val challengeResolver: ChallengeResolver,
) : AttachmentDownloadSource {
  private val log = FaLog.withTag("AttachmentDownloadEndpoint")

  override suspend fun fetch(url: String, fileName: String): AttachmentDownloadResult =
      fetchInternal(
          url = url.trim(),
          fileName = fileName.trim(),
          challengeRetryCount = 0,
      )

  private suspend fun fetchInternal(
      url: String,
      fileName: String,
      challengeRetryCount: Int,
  ): AttachmentDownloadResult {
    if (url.isBlank()) {
      log.w { "下载附件 -> 失败(空地址)" }
      return AttachmentDownloadResult.Failed("附件下载地址为空")
    }
    if (fileName.isBlank()) {
      log.w { "下载附件 -> 失败(空文件名,url=${summarizeUrl(url)})" }
      return AttachmentDownloadResult.Failed("附件文件名为空")
    }
    log.i {
      "下载附件 -> 开始(file=$fileName,url=${summarizeUrl(url)},challengeRetry=$challengeRetryCount)"
    }

    userAgentStorage.loadPersistedIfNeeded()
    val cookieHeader = cookiesStorage.loadRawCookieHeader()
    val userAgent = userAgentStorage.currentUserAgent()
    val response =
        client.get(url) {
          if (cookieHeader.isNotBlank()) {
            header(HttpHeaders.Cookie, cookieHeader)
          }
          header(HttpHeaders.UserAgent, userAgent)
          header(HttpHeaders.Accept, "*/*")
          header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
        }

    val statusCode = response.status.value
    val headers = response.headers.entries().associate { (key, values) -> key to values }
    val setCookieValues = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
    cookiesStorage.mergeSetCookieValues(setCookieValues)
    val bytes: ByteArray = response.body()
    val contentType = response.headers[HttpHeaders.ContentType]

    val htmlishBody = bytes.decodeToString().trim()
    if (looksLikeHtmlResponse(contentType = contentType, body = htmlishBody)) {
      when (
          val classified =
              HtmlResponseResult.classify(
                  statusCode = statusCode,
                  headers = headers,
                  body = htmlishBody,
                  requestUrl = url,
                  finalUrl = url,
              )
      ) {
        is HtmlResponseResult.AuthRequired -> {
          log.w { "下载附件 -> HTML分类${summarizeHtmlResult(classified)}(file=$fileName)" }
          return AttachmentDownloadResult.Failed(classified.message)
        }

        is HtmlResponseResult.CfChallenge -> {
          log.w {
            "下载附件 -> 命中Challenge(file=$fileName,cf-ray=${classified.cfRay ?: "-"},retry=$challengeRetryCount)"
          }
          val resolved =
              challengeResolver.awaitResolution(
                  CfChallengeSignal(requestUrl = url, cfRay = classified.cfRay)
              )
          if (!resolved) {
            log.w { "下载附件 -> Challenge未解决(file=$fileName)" }
            return AttachmentDownloadResult.Failed("Cloudflare challenge unresolved")
          }
          if (challengeRetryCount >= 1) {
            log.w { "下载附件 -> Challenge重试耗尽(file=$fileName)" }
            return AttachmentDownloadResult.Challenge(classified.cfRay)
          }
          return fetchInternal(
              url = url,
              fileName = fileName,
              challengeRetryCount = challengeRetryCount + 1,
          )
        }

        is HtmlResponseResult.MatureBlocked -> {
          log.w { "下载附件 -> 受限(file=$fileName,reason=${classified.reason})" }
          return AttachmentDownloadResult.Blocked(classified.reason)
        }

        is HtmlResponseResult.Error -> {
          log.w { "下载附件 -> HTML错误(file=$fileName,message=${classified.message})" }
          return AttachmentDownloadResult.Failed(classified.message)
        }

        HtmlResponseResult.ChallengeAborted -> {
          log.w { "下载附件 -> Challenge已取消(file=$fileName)" }
          return AttachmentDownloadResult.Failed("Cloudflare challenge aborted")
        }

        is HtmlResponseResult.Success -> {
          if (!fileName.extensionIn("htm", "html")) {
            log.w { "下载附件 -> 返回HTML页面(file=$fileName)" }
            return AttachmentDownloadResult.Failed("下载附件时返回了 HTML 页面")
          }
        }
      }
    }

    if (statusCode !in 200..299) {
      log.w { "下载附件 -> HTTP失败(file=$fileName,status=$statusCode)" }
      return AttachmentDownloadResult.Failed("HTTP $statusCode for $url")
    }

    log.i { "下载附件 -> 成功(file=$fileName,bytes=${bytes.size},contentType=${contentType ?: "-"})" }
    return AttachmentDownloadResult.Success(
        AttachmentDownloadPayload(
            bytes = bytes,
            contentType = contentType,
        )
    )
  }

  private fun looksLikeHtmlResponse(contentType: String?, body: String): Boolean {
    if (body.isBlank()) return false
    val normalizedContentType = contentType.orEmpty().lowercase()
    if (
        normalizedContentType.contains("text/html") ||
            normalizedContentType.contains("application/xhtml+xml")
    ) {
      return true
    }

    val normalizedBody = body.trimStart().lowercase()
    return normalizedBody.startsWith("<!doctype html") ||
        normalizedBody.startsWith("<html") ||
        normalizedBody.startsWith("<head") ||
        normalizedBody.startsWith("<body")
  }
}
