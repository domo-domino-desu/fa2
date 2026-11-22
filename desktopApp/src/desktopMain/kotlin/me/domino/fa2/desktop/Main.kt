package me.domino.fa2.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.material.MaterialDecoratedWindow
import dev.nucleusframework.window.material.MaterialTitleBar
import io.ktor.client.HttpClient
import me.domino.fa2.data.remote.fa.media.installCoilImageProgressSupport
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.di.KOIN_QUALIFIER_CACHED_DOWNLOAD_CLIENT
import me.domino.fa2.di.startAppKoin
import me.domino.fa2.di.stopAppKoin
import me.domino.fa2.ui.app.Fa2AppContent
import me.domino.fa2.ui.app.Fa2AppEnvironment
import me.domino.fa2.utils.logging.FaLog
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named

private const val coilDiskCacheMaxBytes = 1024L * 1024L * 1024L
private const val desktopLogLevelProp: String = "fa2.log.level"

/** Desktop 应用入口。 */
fun main() = runDesktopApplication()

internal fun runDesktopApplication(
    decorateContent: @Composable (content: @Composable () -> Unit, close: (Int) -> Unit) -> Unit =
        { content, _ ->
          content()
        },
) {
  System.getenv("FA2_E2E_HOME")?.let { root ->
    require(java.io.File(root).isAbsolute) { "E2E profile must be an absolute path" }
    System.setProperty("user.home", root)
  }
  nucleusApplication(backend = NucleusBackend.Tao) {
    installZoomImageMainThreadChecker()
    val severity = FaLog.parseDesktopSeverity(System.getProperty(desktopLogLevelProp))
    FaLog.init(severity)
    FaLog.withTag("DesktopMain").i { "桌面启动 -> 日志级别=${severity.name}" }
    val koin = startAppKoin(desktopPlatformModule())
    Fa2AppEnvironment {
      MaterialDecoratedWindow(
          onCloseRequest = {
            FaLog.withTag("DesktopMain").i { "窗口关闭 -> 释放资源" }
            stopAppKoin()
            exitApplication()
          },
          title = System.getProperty("e2e.windowTitle", "fa2"),
      ) {
        LaunchedEffect(koin) {
          runCatching {
                val settingsService = koin.get<AppSettingsService>()
                settingsService.ensureLoaded()
                if (System.getProperty(desktopLogLevelProp).isNullOrBlank()) {
                  FaLog.setMinSeverity(settingsService.settings.value.logLevel.severity)
                }
              }
              .onFailure { error -> FaLog.withTag("DesktopMain").e(error) { "应用读取日志级别失败" } }
        }
        setSingletonImageLoaderFactory { platformContext ->
          val cachedDownloadClient =
              GlobalContext.get()
                  .get<HttpClient>(qualifier = named(KOIN_QUALIFIER_CACHED_DOWNLOAD_CLIENT))
          ImageLoader.Builder(platformContext)
              .installCoilImageProgressSupport(cachedDownloadClient = cachedDownloadClient)
              .diskCache {
                DiskCache.Builder()
                    .directory(desktopCoilDiskCachePath())
                    .maxSizeBytes(coilDiskCacheMaxBytes)
                    .build()
              }
              .build()
        }
        Column(modifier = Modifier.fillMaxSize()) {
          MaterialTitleBar { Text("fa2") }
          Box(modifier = Modifier.weight(1f)) {
            decorateContent({ Fa2AppContent() }) {
              stopAppKoin()
              kotlin.system.exitProcess(it)
            }
          }
        }
      }
    }
  }
}

private fun desktopCoilDiskCachePath(): Path {
  val userHome = System.getProperty("user.home").orEmpty().ifBlank { "." }
  return "$userHome/.cache/fa2/coil-image-cache".toPath()
}
