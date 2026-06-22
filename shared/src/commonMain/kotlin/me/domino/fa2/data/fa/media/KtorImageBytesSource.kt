package me.domino.fa2.data.fa.media

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl

class KtorImageBytesSource(
    private val client: HttpClient,
) : ImageBytesSource {
  private val log = FaLog.withTag("ImageBytesSource")

  override suspend fun fetch(imageUrl: String): ByteArray {
    val normalizedUrl = imageUrl.trim()
    log.i { "图片下载 -> 开始(url=${summarizeUrl(normalizedUrl)})" }
    val response = client.get(normalizedUrl)
    if (!response.status.isSuccess()) {
      log.w { "图片下载 -> 失败(status=${response.status})" }
      throw IllegalStateException("Image download failed: ${response.status}")
    }
    return response.body<ByteArray>().also { bytes ->
      if (bytes.isEmpty()) {
        log.w { "图片下载 -> 空内容" }
        throw IllegalStateException("Image download returned empty payload")
      }
      log.i { "图片下载 -> 成功(bytes=${bytes.size})" }
    }
  }
}
