package me.domino.fa2.util.logging

import co.touchlab.kermit.Severity
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
