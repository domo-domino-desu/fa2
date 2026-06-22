package me.domino.fa2.utils.logging

/** URL 输出摘要：仅裁剪首尾空白，保留原始 URL，不做脱敏。 */
fun summarizeUrl(url: String): String = url.trim()
