package me.domino.fa2.data.network.endpoint

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import me.domino.fa2.data.network.FaCookiesStorage
import me.domino.fa2.data.network.HtmlResponseResult
import me.domino.fa2.data.network.UserAgentStorage
import me.domino.fa2.data.network.challenge.CfChallengeSignal
import me.domino.fa2.data.network.challenge.ChallengeResolver
import me.domino.fa2.util.attachmenttext.extensionIn

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
    if (url.isBlank()) return AttachmentDownloadResult.Failed("附件下载地址为空")
    if (fileName.isBlank()) return AttachmentDownloadResult.Failed("附件文件名为空")

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
      when (val classified = HtmlResponseResult.classify(statusCode, headers, htmlishBody, url)) {
        is HtmlResponseResult.CfChallenge -> {
          val resolved =
              challengeResolver.awaitResolution(
                  CfChallengeSignal(requestUrl = url, cfRay = classified.cfRay)
              )
          if (!resolved) {
            return AttachmentDownloadResult.Failed("Cloudflare challenge unresolved")
          }
          if (challengeRetryCount >= 1) {
            return AttachmentDownloadResult.Challenge(classified.cfRay)
          }
          return fetchInternal(
              url = url,
              fileName = fileName,
              challengeRetryCount = challengeRetryCount + 1,
          )
        }

        is HtmlResponseResult.MatureBlocked -> {
          return AttachmentDownloadResult.Blocked(classified.reason)
        }

        is HtmlResponseResult.Error -> {
          return AttachmentDownloadResult.Failed(classified.message)
        }

        is HtmlResponseResult.Success -> {
          if (!fileName.extensionIn("htm", "html")) {
            return AttachmentDownloadResult.Failed("下载附件时返回了 HTML 页面")
          }
        }
      }
    }

    if (statusCode !in 200..299) {
      return AttachmentDownloadResult.Failed("HTTP $statusCode for $url")
    }

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
