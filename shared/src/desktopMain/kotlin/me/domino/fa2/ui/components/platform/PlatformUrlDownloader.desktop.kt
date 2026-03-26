package me.domino.fa2.ui.components.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.domino.fa2.data.network.endpoint.AttachmentDownloadResult
import me.domino.fa2.data.network.endpoint.AttachmentDownloadSource
import org.koin.compose.koinInject

@Composable
actual fun rememberPlatformUrlDownloader(): suspend (String) -> Boolean {
  val downloadSource = koinInject<AttachmentDownloadSource>()
  return remember(downloadSource) { { url -> downloadUrlWithSaveDialog(url, downloadSource) } }
}

private suspend fun downloadUrlWithSaveDialog(
    url: String,
    downloadSource: AttachmentDownloadSource,
): Boolean {
  val normalized = url.trim()
  if (normalized.isBlank()) return false
  val target =
      runCatching { chooseSaveFile(defaultFileNameForUrl(normalized)) }.getOrNull() ?: return false
  return withContext(Dispatchers.IO) {
    runCatching {
          target.parentFile?.mkdirs()
          when (val result = downloadSource.fetch(url = normalized, fileName = target.name)) {
            is AttachmentDownloadResult.Success -> {
              target.outputStream().use { output -> output.write(result.payload.bytes) }
              true
            }

            is AttachmentDownloadResult.Blocked,
            is AttachmentDownloadResult.Challenge,
            is AttachmentDownloadResult.Failed -> false
          }
        }
        .getOrDefault(false)
  }
}

private fun chooseSaveFile(defaultFileName: String): File? {
  return runOnAwtEventThread {
    val owner = Frame()
    try {
      FileDialog(owner, "保存文件", FileDialog.SAVE)
          .apply {
            file = defaultFileName
            isVisible = true
          }
          .let { dialog ->
            val selectedFile = dialog.file ?: return@runOnAwtEventThread null
            val selectedDirectory = dialog.directory ?: return@runOnAwtEventThread null
            File(selectedDirectory, selectedFile)
          }
    } finally {
      owner.dispose()
    }
  }
}

private fun defaultFileNameForUrl(url: String): String {
  val pathSegment = url.substringAfterLast('/').substringBefore('?').substringBefore('#').trim()
  return pathSegment.ifBlank { "download.bin" }
}

private fun <T> runOnAwtEventThread(block: () -> T): T {
  if (EventQueue.isDispatchThread()) return block()
  var result: Result<T>? = null
  EventQueue.invokeAndWait { result = runCatching(block) }
  return checkNotNull(result).getOrThrow()
}
