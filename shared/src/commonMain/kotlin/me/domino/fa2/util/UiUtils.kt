package me.domino.fa2.util

/** 投稿详情最小宽高比。 */
private const val minDetailAspectRatio = 0.55f

/** 投稿详情最大宽高比。 */
private const val maxDetailAspectRatio = 2.2f

/** 投稿详情默认宽高比。 */
private const val fallbackDetailAspectRatio = 1f

/**
 * 规范化详情图宽高比。
 */
internal fun sanitizeDetailAspectRatio(
    /** 原始宽高比。 */
    rawRatio: Float,
): Float {
    if (!rawRatio.isFinite() || rawRatio <= 0f) {
        return fallbackDetailAspectRatio
    }
    return rawRatio.coerceIn(minDetailAspectRatio, maxDetailAspectRatio)
}

/**
 * 判断是否为 gif 链接。
 */
internal fun isGifUrl(url: String): Boolean {
    val normalized = url.trim().lowercase()
    return normalized.contains(".gif")
}
