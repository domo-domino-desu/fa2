package me.domino.fa2.domain.attachmenttext

import me.domino.fa2.data.model.ResourceResult
import me.domino.fa2.data.remote.fa.media.AttachmentDownloadResult
import me.domino.fa2.data.remote.fa.media.AttachmentDownloadSource
import me.domino.fa2.utils.concurrency.runSuspendCatching
import me.domino.fa2.utils.logging.FaLog
import me.domino.fa2.utils.logging.summarizeUrl

class AttachmentTextService(private val attachmentDownloadSource: AttachmentDownloadSource) {
  private val log = FaLog.withTag("AttachmentTextService")

  suspend fun load(
      downloadUrl: String,
      downloadFileName: String,
      onProgress: (AttachmentTextProgress) -> Unit = {},
  ): ResourceResult<AttachmentTextDocument> {
    val normalizedUrl = downloadUrl.trim()
    val normalizedFileName = downloadFileName.trim()
    require(normalizedUrl.isNotBlank()) { "Missing attachment download url" }
    require(normalizedFileName.isNotBlank()) { "Missing attachment file name" }
    require(AttachmentTextExtractor.isSupported(normalizedFileName)) {
      "Unsupported attachment format: $normalizedFileName"
    }

    log.i { "加载附件文本 -> 开始(file=$normalizedFileName,url=${summarizeUrl(normalizedUrl)})" }
    return when (
        val result =
            attachmentDownloadSource.fetch(url = normalizedUrl, fileName = normalizedFileName)
    ) {
      is AttachmentDownloadResult.Success ->
          runSuspendCatching {
                AttachmentTextExtractor.parse(
                    fileName = normalizedFileName,
                    bytes = result.payload.bytes,
                    onProgress = onProgress,
                )
              }
              .fold(
                  onSuccess = { document ->
                    log.i { "加载附件文本 -> 成功(file=$normalizedFileName)" }
                    ResourceResult.Success(document)
                  },
                  onFailure = { error ->
                    log.e(error) { "加载附件文本 -> 解析失败(file=$normalizedFileName)" }
                    ResourceResult.Error(error)
                  },
              )

      is AttachmentDownloadResult.Challenge -> {
        log.w { "加载附件文本 -> Cloudflare验证(file=$normalizedFileName)" }
        ResourceResult.CfChallenge
      }

      is AttachmentDownloadResult.Blocked -> {
        log.w { "加载附件文本 -> 受限(file=$normalizedFileName,reason=${result.reason})" }
        ResourceResult.MatureBlocked(result.reason)
      }

      is AttachmentDownloadResult.Failed -> {
        log.w { "加载附件文本 -> 失败(file=$normalizedFileName,message=${result.message})" }
        ResourceResult.Error(IllegalStateException(result.message))
      }
    }
  }
}
