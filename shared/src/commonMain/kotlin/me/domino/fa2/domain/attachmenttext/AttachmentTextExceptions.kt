package me.domino.fa2.domain.attachmenttext

/** 不支持的附件格式异常。 */
class UnsupportedAttachmentTextFormatException(fileName: String) :
    IllegalArgumentException(
        "Unsupported attachment text format: ${fileName.trim().ifBlank { "<empty>" }}"
    )
