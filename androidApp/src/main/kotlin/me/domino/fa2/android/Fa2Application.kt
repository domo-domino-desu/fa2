package me.domino.fa2.android

import android.app.Application
import co.touchlab.kermit.Severity
import me.domino.fa2.di.startAppKoin
import me.domino.fa2.util.logging.FaLog

/**
 * Android 应用进程入口。
 */
class Fa2Application : Application() {
    /**
     * 应用启动回调。
     */
    override fun onCreate() {
        super.onCreate()
        FaLog.init(
            if (BuildConfig.DEBUG) {
                Severity.Debug
            } else {
                Severity.Info
            },
        )
        startAppKoin(androidPlatformModule(applicationContext))
        FaLog.withTag("Fa2Application").i { "应用启动 -> Koin已初始化" }
    }
}
