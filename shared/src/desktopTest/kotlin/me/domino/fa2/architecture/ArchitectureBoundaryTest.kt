package me.domino.fa2.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchitectureBoundaryTest {
  private val repoRoot: Path = locateRepoRoot()
  private val commonMainRoot = repoRoot.resolve("shared/src/commonMain/kotlin/me/domino/fa2")
  private val commonTestRoot = repoRoot.resolve("shared/src/commonTest/kotlin/me/domino/fa2")

  @Test
  fun applicationPackageDoesNotExist() {
    assertFalse(
        commonMainRoot.resolve("application").exists(),
        "commonMain must not contain application",
    )
    assertFalse(
        commonTestRoot.resolve("application").exists(),
        "commonTest must not contain application",
    )

    val offenders =
        listOf(commonMainRoot, commonTestRoot)
            .flatMap(::kotlinFilesUnder)
            .filter { file -> file.readText().contains("me.domino.fa2.application") }
            .map { file -> file.relativeTo(repoRoot).toString() }

    assertTrue(offenders.isEmpty(), "application imports/packages remain: $offenders")
  }

  @Test
  fun dataAndDomainDoNotDependOnUiOrApplication() {
    val forbiddenByLayer =
        mapOf(
            "data" to
                listOf("me.domino.fa2.domain", "me.domino.fa2.ui", "me.domino.fa2.application"),
            "domain" to listOf("me.domino.fa2.ui", "me.domino.fa2.application"),
        )

    val offenders =
        forbiddenByLayer.flatMap { (layer, forbiddenImports) ->
          kotlinFilesUnder(commonMainRoot.resolve(layer)).flatMap { file ->
            val lines = file.readLines()
            forbiddenImports.flatMap { forbidden ->
              lines
                  .withIndex()
                  .filter { (_, line) -> line.startsWith("import $forbidden") }
                  .map { (index, line) -> "${file.relativeTo(repoRoot)}:${index + 1}: $line" }
            }
          }
        }

    assertTrue(
        offenders.isEmpty(),
        "Forbidden layer imports found:\n${offenders.joinToString("\n")}",
    )
  }

  @Test
  fun topLevelUtilsStaySmallAndShared() {
    val allowedTopLevelUtils =
        setOf(
            "FaUrls.kt",
            "TemplateStringUtils.kt",
            "UrlParsingUtils.kt",
        )
    val allowedLoggingUtils = setOf("FaLog.kt", "LogSummaries.kt")
    val allowedHtmlUtils = setOf("HtmlTextBlockExtractor.kt")
    val allowedConcurrencyUtils = setOf("SequentialRequestThrottle.kt")

    val topLevelFiles =
        kotlinFilesUnder(commonMainRoot.resolve("utils"))
            .filter { file -> file.parent.name == "utils" }
            .map { file -> file.name }
            .toSet()
    val loggingFiles =
        kotlinFilesUnder(commonMainRoot.resolve("utils/logging")).map { file -> file.name }.toSet()
    val htmlFiles =
        kotlinFilesUnder(commonMainRoot.resolve("utils/html")).map { file -> file.name }.toSet()
    val concurrencyFiles =
        kotlinFilesUnder(commonMainRoot.resolve("utils/concurrency"))
            .map { file -> file.name }
            .toSet()

    assertTrue(
        topLevelFiles == allowedTopLevelUtils,
        "Unexpected top-level utils: expected=$allowedTopLevelUtils actual=$topLevelFiles",
    )
    assertTrue(
        loggingFiles == allowedLoggingUtils,
        "Unexpected logging utils: expected=$allowedLoggingUtils actual=$loggingFiles",
    )
    assertTrue(
        htmlFiles == allowedHtmlUtils,
        "Unexpected html utils: expected=$allowedHtmlUtils actual=$htmlFiles",
    )
    assertTrue(
        concurrencyFiles == allowedConcurrencyUtils,
        "Unexpected concurrency utils: expected=$allowedConcurrencyUtils actual=$concurrencyFiles",
    )
  }

  @Test
  fun collapsedDataPackagesDoNotExist() {
    val removedDataDirectories =
        listOf(
            "attachmenttext",
            "datasource",
            "network",
            "parser",
            "repository",
            "store",
        )
    val offenders =
        removedDataDirectories
            .map { name -> commonMainRoot.resolve("data").resolve(name) }
            .filter { path -> path.exists() }
            .map { path -> path.relativeTo(repoRoot).toString() }

    assertTrue(offenders.isEmpty(), "Removed data packages still exist: $offenders")

    val expectedDataDirectories =
        listOf(
            "fa/core",
            "fa/session",
            "fa/media",
            "fa/feed",
            "fa/submission",
            "model",
            "settings",
            "local/settings",
            "translation",
        )
    val missing =
        expectedDataDirectories
            .map { name -> commonMainRoot.resolve("data").resolve(name) }
            .filterNot { path -> path.exists() && path.isDirectory() }
            .map { path -> path.relativeTo(repoRoot).toString() }

    assertTrue(missing.isEmpty(), "Expected data packages are missing: $missing")
  }

  @Test
  fun domainOwnsComplexBusinessServices() {
    val expectedDomainDirectories =
        listOf(
            "attachmenttext",
            "submissionseries",
            "translation",
            "watchrecommendation",
        )
    val missing =
        expectedDomainDirectories
            .map { name -> commonMainRoot.resolve("domain").resolve(name) }
            .filterNot { path -> path.exists() && path.isDirectory() }
            .map { path -> path.relativeTo(repoRoot).toString() }

    assertTrue(missing.isEmpty(), "Expected domain packages are missing: $missing")
  }

  @Test
  fun dataDoesNotContainUtilsSubpackages() {
    val offenders =
        directoriesUnder(commonMainRoot.resolve("data"))
            .filter { path -> path.name == "utils" }
            .map { path -> path.relativeTo(repoRoot).toString() }

    assertTrue(offenders.isEmpty(), "data must not contain utils subpackages: $offenders")
  }

  @Test
  fun uiPackagesFollowHybridLayout() {
    val removedUiDirectories =
        listOf(
            "search",
            "components/submission",
            "host",
            "navigation",
            "layouts",
            "theme",
        )
    val offenders =
        removedUiDirectories
            .map { name -> commonMainRoot.resolve("ui").resolve(name) }
            .filter { path -> path.exists() }
            .map { path -> path.relativeTo(repoRoot).toString() }

    assertTrue(offenders.isEmpty(), "Removed ui packages still exist: $offenders")

    val expectedUiDirectories =
        listOf(
            "i18n/search",
            "components/waterfall",
            "pages/submission/attachmenttext",
            "pages/submission/content",
            "pages/submission/imageocr",
            "pages/submission/pager",
            "pages/submission/series",
            "pages/submission/translation",
            "pages/overlays",
            "pages/settings/components",
            "app",
            "app/challenge",
            "app/navigation",
            "app/scaffold",
            "app/theme",
        )
    val missing =
        expectedUiDirectories
            .map { name -> commonMainRoot.resolve("ui").resolve(name) }
            .filterNot { path -> path.exists() && path.isDirectory() }
            .map { path -> path.relativeTo(repoRoot).toString() }

    assertTrue(missing.isEmpty(), "Expected ui packages are missing: $missing")
  }

  @Test
  fun diPackageOnlyContainsLayerModules() {
    val expectedFiles =
        setOf(
            "AppModule.kt",
            "DataModule.kt",
            "DependencyQualifiers.kt",
            "DomainModule.kt",
            "UiModule.kt",
        )
    val actualFiles =
        kotlinFilesUnder(commonMainRoot.resolve("di"))
            .filter { file -> file.parent == commonMainRoot.resolve("di") }
            .map { file -> file.name }
            .toSet()

    assertTrue(
        actualFiles == expectedFiles,
        "Unexpected DI module files: expected=$expectedFiles actual=$actualFiles",
    )
  }

  private fun kotlinFilesUnder(root: Path): List<Path> {
    if (!root.exists()) return emptyList()
    return Files.walk(root).use { stream ->
      stream.filter { path -> path.isRegularFile() && path.extension == "kt" }.sorted().toList()
    }
  }

  private fun directoriesUnder(root: Path): List<Path> {
    if (!root.exists()) return emptyList()
    return Files.walk(root).use { stream ->
      stream.filter { path -> path.isDirectory() }.sorted().toList()
    }
  }

  private fun locateRepoRoot(): Path {
    var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    while (current.parent != null) {
      if (current.resolve("settings.gradle.kts").exists()) return current
      current = current.parent
    }
    error("Unable to locate repo root from ${System.getProperty("user.dir")}")
  }
}
