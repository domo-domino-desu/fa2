package me.domino.fa2.android

import android.app.Application
import co.touchlab.kermit.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.domino.fa2.data.settings.AppSettingsService
import me.domino.fa2.di.startAppKoin
import me.domino.fa2.domain.attachmenttext.initializeAttachmentTextPlatform
import me.domino.fa2.ui.pages.about.AboutLibrariesAndroidContextHolder
import me.domino.fa2.utils.logging.FaLog

/** Android 应用进程入口。 */
class Fa2Application : Application() {
  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  /** 应用启动回调。 */
  override fun onCreate() {
    super.onCreate()
    AboutLibrariesAndroidContextHolder.initialize(applicationContext)
    initializeAttachmentTextPlatform(applicationContext)
    FaLog.init(
        if (BuildConfig.DEBUG) {
          Severity.Debug
        } else {
          Severity.Info
        }
    )
    val koin = startAppKoin(androidPlatformModule(applicationContext))
    applicationScope.launch {
      runCatching {
            val settingsService = koin.get<AppSettingsService>()
            settingsService.ensureLoaded()
            FaLog.setMinSeverity(settingsService.settings.value.logLevel.severity)
          }
          .onFailure { error -> FaLog.withTag("Fa2Application").e(error) { "应用读取日志级别失败" } }
    }
    FaLog.withTag("Fa2Application").i { "应用启动 -> Koin 与附件文本平台已初始化" }
  }
}
