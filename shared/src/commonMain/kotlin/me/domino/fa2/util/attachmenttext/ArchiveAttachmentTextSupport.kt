package me.domino.fa2.util.attachmenttext

/** 读取压缩包内文本条目。 */
internal expect fun readArchiveTextEntry(
    bytes: ByteArray,
    entryPath: String,
    reporter: AttachmentTextProgressReporter,
): String
