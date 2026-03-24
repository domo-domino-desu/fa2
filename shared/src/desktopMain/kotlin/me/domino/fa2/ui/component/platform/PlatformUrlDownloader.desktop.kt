package me.domino.fa2.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberPlatformUrlDownloader(): suspend (String) -> Boolean {
  return remember { { url -> downloadUrlWithSaveDialog(url) } }
}

private suspend fun downloadUrlWithSaveDialog(url: String): Boolean {
  val normalized = url.trim()
  if (normalized.isBlank()) return false
  val target =
    runCatching { chooseSaveFile(defaultFileNameForUrl(normalized)) }.getOrNull() ?: return false
  return withContext(Dispatchers.IO) {
    runCatching {
        target.parentFile?.mkdirs()
        val connection =
          URI(normalized).toURL().openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 30_000
          }
        connection.getInputStream().use { input ->
          target.outputStream().use { output -> input.copyTo(output) }
        }
      }
      .isSuccess
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
