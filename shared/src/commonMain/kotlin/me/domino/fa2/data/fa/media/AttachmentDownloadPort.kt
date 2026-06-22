package me.domino.fa2.data.fa.media

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
