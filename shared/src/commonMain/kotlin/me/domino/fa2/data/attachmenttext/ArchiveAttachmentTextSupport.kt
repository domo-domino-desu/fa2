package me.domino.fa2.data.attachmenttext

import me.domino.fa2.application.attachmenttext.AttachmentTextProgressReporter
import me.domino.fa2.domain.attachmenttext.*

/** 读取压缩包内文本条目。 */
internal expect fun readArchiveTextEntry(
    bytes: ByteArray,
    entryPath: String,
    reporter: AttachmentTextProgressReporter,
): String
