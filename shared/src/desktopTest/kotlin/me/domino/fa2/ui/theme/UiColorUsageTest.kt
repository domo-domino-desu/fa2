package me.domino.fa2.ui.theme

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertTrue

class UiColorUsageTest {
  @Test
  fun uiLayerDoesNotUseHardcodedOpaqueColors() {
    val repoRoot = locateRepoRoot(Path.of(System.getProperty("user.dir")))
    val uiSourceRoots =
        listOf(
            repoRoot.resolve("shared/src/commonMain/kotlin/me/domino/fa2/ui"),
            repoRoot.resolve("shared/src/androidMain/kotlin/me/domino/fa2/ui"),
            repoRoot.resolve("shared/src/desktopMain/kotlin/me/domino/fa2/ui"),
        )

    val forbiddenPatterns =
        listOf(
            Regex(
                """\bColor\.(Black|White|Red|Blue|Green|Gray|LightGray|DarkGray|Yellow|Cyan|Magenta)\b"""
            ),
            Regex("""\bColor\(0x[0-9A-Fa-f_]+\)"""),
        )

    val violations =
        uiSourceRoots
            .filter { Files.exists(it) }
            .flatMap { sourceRoot ->
              Files.walk(sourceRoot).use { paths ->
                paths
                    .asSequence()
                    .filter { path -> Files.isRegularFile(path) && path.name.endsWith(".kt") }
                    .flatMap { path ->
                      path
                          .readText()
                          .lineSequence()
                          .mapIndexedNotNull { index, line ->
                            if (
                                forbiddenPatterns.any { pattern -> pattern.containsMatchIn(line) }
                            ) {
                              "${repoRoot.relativize(path)}:${index + 1}: ${line.trim()}"
                            } else {
                              null
                            }
                          }
                          .asSequence()
                    }
                    .toList()
              }
            }

    assertTrue(
        violations.isEmpty(),
        buildString {
          appendLine(
              "Found hardcoded opaque colors in UI code. Use MaterialTheme.colorScheme instead:"
          )
          violations.forEach(::appendLine)
        },
    )
  }

  private fun locateRepoRoot(start: Path): Path {
    var current: Path? = start.toAbsolutePath()
    while (current != null) {
      if (Files.exists(current.resolve("settings.gradle.kts"))) {
        return current
      }
      current = current.parent
    }
    error("Unable to locate repository root from $start")
  }
}
