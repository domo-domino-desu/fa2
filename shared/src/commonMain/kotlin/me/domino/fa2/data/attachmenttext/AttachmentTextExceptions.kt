package me.domino.fa2.data.attachmenttext

import me.domino.fa2.domain.attachmenttext.*

/** 不支持的附件格式异常。 */
class UnsupportedAttachmentTextFormatException(fileName: String) :
    IllegalArgumentException("不支持的附件文本格式：${fileName.trim().ifBlank { "<empty>" }}")
