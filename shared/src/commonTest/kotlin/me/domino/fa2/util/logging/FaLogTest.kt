package me.domino.fa2.util.logging

import co.touchlab.kermit.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.domino.fa2.data.settings.LogLevelSetting

/** FaLog 级别解析测试。 */
class FaLogTest {
  @Test
  fun parseDesktopSeverityUsesInfoAsDefault() {
    assertEquals(Severity.Info, FaLog.parseDesktopSeverity(null))
    assertEquals(Severity.Info, FaLog.parseDesktopSeverity(""))
    assertEquals(Severity.Info, FaLog.parseDesktopSeverity("  "))
    assertEquals(Severity.Info, FaLog.parseDesktopSeverity("unknown"))
  }

  @Test
  fun parseDesktopSeveritySupportsAliases() {
    assertEquals(Severity.Verbose, FaLog.parseDesktopSeverity("trace"))
    assertEquals(Severity.Debug, FaLog.parseDesktopSeverity("D"))
    assertEquals(Severity.Info, FaLog.parseDesktopSeverity("INFO"))
    assertEquals(Severity.Warn, FaLog.parseDesktopSeverity("warning"))
    assertEquals(Severity.Error, FaLog.parseDesktopSeverity("e"))
    assertEquals(Severity.Assert, FaLog.parseDesktopSeverity("assert"))
  }

  @Test
  fun logLevelSettingMapsAllSupportedSeverities() {
    LogLevelSetting.entries.forEach { logLevel ->
      assertEquals(logLevel, LogLevelSetting.fromPersistedValue(logLevel.persistedValue))
      assertEquals(logLevel, LogLevelSetting.fromSeverity(logLevel.severity))
    }
  }

  @Test
  fun setMinSeverityUpdatesExportedMetadata() {
    FaLog.setMinSeverity(Severity.Warn)

    val exported = FaLog.exportRuntimeLogText(appVersionName = "test")

    assertTrue(exported.contains("min_severity=Warn"))
  }
}
