package me.domino.fa2.application.attachmenttext

import me.domino.fa2.data.model.PageState
import me.domino.fa2.data.network.endpoint.AttachmentDownloadResult
import me.domino.fa2.data.network.endpoint.AttachmentDownloadSource
import me.domino.fa2.domain.attachmenttext.AttachmentTextDocument
import me.domino.fa2.domain.attachmenttext.AttachmentTextProgress
import me.domino.fa2.util.logging.FaLog
import me.domino.fa2.util.logging.summarizeUrl

class AttachmentTextService(
    private val attachmentDownloadSource: AttachmentDownloadSource,
) {
  private val log = FaLog.withTag("AttachmentTextService")

  suspend fun load(
      downloadUrl: String,
      downloadFileName: String,
      onProgress: (AttachmentTextProgress) -> Unit = {},
  ): PageState<AttachmentTextDocument> {
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
            attachmentDownloadSource.fetch(
                url = normalizedUrl,
                fileName = normalizedFileName,
            )
    ) {
      is AttachmentDownloadResult.Success ->
          runCatching {
                AttachmentTextExtractor.parse(
                    fileName = normalizedFileName,
                    bytes = result.payload.bytes,
                    onProgress = onProgress,
                )
              }
              .fold(
                  onSuccess = { document ->
                    log.i { "加载附件文本 -> 成功(file=$normalizedFileName)" }
                    PageState.Success(document)
                  },
                  onFailure = { error ->
                    log.e(error) { "加载附件文本 -> 解析失败(file=$normalizedFileName)" }
                    PageState.Error(error)
                  },
              )

      is AttachmentDownloadResult.Challenge -> {
        log.w { "加载附件文本 -> Cloudflare验证(file=$normalizedFileName)" }
        PageState.CfChallenge
      }

      is AttachmentDownloadResult.Blocked -> {
        log.w { "加载附件文本 -> 受限(file=$normalizedFileName,reason=${result.reason})" }
        PageState.MatureBlocked(result.reason)
      }

      is AttachmentDownloadResult.Failed -> {
        log.w { "加载附件文本 -> 失败(file=$normalizedFileName,message=${result.message})" }
        PageState.Error(IllegalStateException(result.message))
      }
    }
  }
}
