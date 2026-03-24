package me.domino.fa2.util.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter

/**
 * 全局日志入口。
 */
object FaLog {
    private var initialized: Boolean = false

    /**
     * 初始化 Kermit。
     */
    fun init(minSeverity: Severity) {
        Logger.setLogWriters(platformLogWriter())
        Logger.setMinSeverity(minSeverity)
        initialized = true
        Logger.withTag("FaLog").i { "初始化日志 -> 最小级别=${minSeverity.name}" }
    }

    /**
     * 获取带标签的 Logger。
     */
    fun withTag(tag: String): Logger = Logger.withTag(tag)

    /**
     * 解析桌面端日志级别系统属性。
     */
    fun parseDesktopSeverity(raw: String?): Severity =
        when (raw?.trim()?.lowercase()) {
            "verbose", "trace", "v" -> Severity.Verbose
            "debug", "d" -> Severity.Debug
            "info", "i", null, "" -> Severity.Info
            "warn", "warning", "w" -> Severity.Warn
            "error", "e" -> Severity.Error
            "assert", "a" -> Severity.Assert
            else -> Severity.Info
        }

    /**
     * 日志是否已初始化。
     */
    fun isInitialized(): Boolean = initialized
}
