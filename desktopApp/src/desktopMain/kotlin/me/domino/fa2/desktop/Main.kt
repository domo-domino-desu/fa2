package me.domino.fa2.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import me.domino.fa2.di.startAppKoin
import me.domino.fa2.di.stopAppKoin
import me.domino.fa2.ui.app.Fa2App
import me.domino.fa2.util.logging.FaLog
import okio.Path
import okio.Path.Companion.toPath

private const val coilDiskCacheMaxBytes = 1024L * 1024L * 1024L
private const val desktopLogLevelProp: String = "fa2.log.level"

/** Desktop 应用入口。 */
fun main() = application {
  val severity = FaLog.parseDesktopSeverity(System.getProperty(desktopLogLevelProp))
  FaLog.init(severity)
  FaLog.withTag("DesktopMain").i { "桌面启动 -> 日志级别=${severity.name}" }
  startAppKoin(desktopPlatformModule())
  Window(
    onCloseRequest = {
      FaLog.withTag("DesktopMain").i { "窗口关闭 -> 释放资源" }
      stopAppKoin()
      exitApplication()
    },
    title = "fa2",
  ) {
    setSingletonImageLoaderFactory { platformContext ->
      ImageLoader.Builder(platformContext)
        .diskCache {
          DiskCache.Builder()
            .directory(desktopCoilDiskCachePath())
            .maxSizeBytes(coilDiskCacheMaxBytes)
            .build()
        }
        .build()
    }
    Fa2App()
  }
}

private fun desktopCoilDiskCachePath(): Path {
  val userHome = System.getProperty("user.home").orEmpty().ifBlank { "." }
  return "$userHome/.cache/fa2/coil-image-cache".toPath()
}
