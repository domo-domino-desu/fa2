package me.domino.fa2.util.attachmenttext

/** 附件文本解析器。 */
interface AttachmentTextParser {
  /** 解析器格式。 */
  val format: AttachmentTextFormat

  /** 当前文件名是否支持。 */
  fun supports(fileName: String): Boolean

  /** 解析附件。 */
  suspend fun parse(
      fileName: String,
      bytes: ByteArray,
      reporter: AttachmentTextProgressReporter,
  ): AttachmentTextDocument
}
