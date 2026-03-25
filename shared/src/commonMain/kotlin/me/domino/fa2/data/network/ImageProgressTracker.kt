package me.domino.fa2.data.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** 单个图片请求的下载进度快照。 */
data class ImageDownloadProgressSnapshot(
    /** 已下载字节数。 */
    val bytesRead: Long,
    /** 总字节数（未知时 <= 0）。 */
    val totalBytes: Long,
) {
  /** 是否具备可用总量。 */
  val hasKnownTotal: Boolean = totalBytes > 0L

  /** 归一化进度（0..1），未知总量时返回 `null`。 */
  val progressFraction: Float? =
      if (!hasKnownTotal) {
        null
      } else {
        (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
      }
}

private const val maxTrackedImageRequestCount = 256

/** 图片下载进度追踪器。 */
class ImageProgressTracker {
  private val snapshotsByKey =
      MutableStateFlow<Map<String, ImageDownloadProgressSnapshot>>(emptyMap())

  /** 观察指定 key 的下载快照。 */
  fun observe(progressKey: String): Flow<ImageDownloadProgressSnapshot?> {
    val normalizedKey = normalizeProgressKey(progressKey)
    if (normalizedKey.isBlank()) return flowOf(null)
    return snapshotsByKey.map { snapshots -> snapshots[normalizedKey] }.distinctUntilChanged()
  }

  /** 标记请求开始（未知总量）。 */
  fun markRequestStarted(progressKey: String) {
    updateSnapshot(progressKey) { current ->
      current?.takeIf { snapshot -> snapshot.bytesRead > 0L }
          ?: ImageDownloadProgressSnapshot(0L, -1L)
    }
  }

  /** 更新下载进度。 */
  fun updateDownloadProgress(progressKey: String, bytesRead: Long, totalBytes: Long) {
    updateSnapshot(progressKey) {
      ImageDownloadProgressSnapshot(
          bytesRead = bytesRead.coerceAtLeast(0L),
          totalBytes = totalBytes,
      )
    }
  }

  /** 清理指定 key。 */
  fun clear(progressKey: String) {
    updateSnapshot(progressKey) { null }
  }

  /** 清理全部 key。 */
  fun clearAll() {
    snapshotsByKey.value = emptyMap()
  }

  private fun updateSnapshot(
      progressKey: String,
      updater: (ImageDownloadProgressSnapshot?) -> ImageDownloadProgressSnapshot?,
  ) {
    val normalizedKey = normalizeProgressKey(progressKey)
    if (normalizedKey.isBlank()) return

    snapshotsByKey.update { current ->
      val mutable = current.toMutableMap()
      val nextValue = updater(mutable[normalizedKey])
      if (nextValue == null) {
        mutable.remove(normalizedKey)
      } else {
        mutable[normalizedKey] = nextValue
      }
      trimOverflow(mutable)
      mutable.toMap()
    }
  }

  private fun trimOverflow(target: MutableMap<String, ImageDownloadProgressSnapshot>) {
    val overflow = target.size - maxTrackedImageRequestCount
    if (overflow <= 0) return
    target.keys.take(overflow).toList().forEach { key -> target.remove(key) }
  }
}

/** 规范化进度 key。 */
internal fun normalizeProgressKey(progressKey: String): String = progressKey.trim()
