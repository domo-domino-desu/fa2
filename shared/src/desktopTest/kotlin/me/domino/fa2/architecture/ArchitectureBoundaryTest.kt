package me.domino.fa2.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureBoundaryTest {
  private val repoRoot: Path = locateRepoRoot()
  private val commonMainRoot = repoRoot.resolve("shared/src/commonMain/kotlin/me/domino/fa2")
  private val commonTestRoot = repoRoot.resolve("shared/src/commonTest/kotlin/me/domino/fa2")

  @Test
  fun projectLocalWildcardImportsAreForbidden() {
    val offenders =
        listOf(commonMainRoot, commonTestRoot).flatMap(::kotlinFilesUnder).flatMap { file ->
          file.readLines().withIndex().mapNotNull { (index, line) ->
            if (line.startsWith("import me.domino.fa2.") && line.endsWith(".*")) {
              "${file.relativeTo(repoRoot)}:${index + 1}: $line"
            } else {
              null
            }
          }
        }

    assertTrue(
        offenders.isEmpty(),
        "Project-local wildcard imports hide coupling:\n${offenders.joinToString("\n")}",
    )
  }

  @Test
  fun lowerLayersDoNotDependOnHigherLayers() {
    val forbiddenByLayer =
        mapOf(
            "data" to
                listOf("me.domino.fa2.domain", "me.domino.fa2.ui", "me.domino.fa2.application"),
            "domain" to listOf("me.domino.fa2.ui", "me.domino.fa2.application"),
        )

    val offenders =
        forbiddenByLayer.flatMap { (layer, forbiddenReferences) ->
          kotlinFilesUnder(commonMainRoot.resolve(layer)).flatMap { file ->
            file.readLines().withIndex().flatMap { (index, line) ->
              forbiddenReferences
                  .filter { reference -> reference in line }
                  .map { _ -> "${file.relativeTo(repoRoot)}:${index + 1}: $line" }
            }
          }
        }

    assertTrue(
        offenders.isEmpty(),
        "Forbidden layer references found:\n${offenders.joinToString("\n")}",
    )
  }

  @Test
  fun dataResourcePackagesAreAcyclic() {
    val faRoot = commonMainRoot.resolve("data/fa")
    val packagePattern = Regex("me\\.domino\\.fa2\\.data\\.fa\\.([a-z0-9_]+)")
    val graph = mutableMapOf<String, MutableSet<String>>()

    kotlinFilesUnder(faRoot).forEach { file ->
      val relative = file.relativeTo(faRoot)
      val owner = relative.getName(0).toString()
      val dependencies =
          packagePattern
              .findAll(file.toFile().readText())
              .map { match -> match.groupValues[1] }
              .filter { dependency -> dependency != owner }
      graph.getOrPut(owner, ::mutableSetOf).addAll(dependencies)
    }

    val cycles = findCycles(graph)
    assertTrue(
        cycles.isEmpty(),
        "data/fa package dependencies must be acyclic:\n${cycles.joinToString("\n")}",
    )
  }

  @Test
  fun reusableUiAndViewerApiDoNotDependOnFeatureImplementations() {
    val protectedRoots =
        listOf(
            commonMainRoot.resolve("ui/components"),
            commonMainRoot.resolve("ui/submissionviewer/api"),
            commonMainRoot.resolve("ui/navigation"),
            commonMainRoot.resolve("ui/translation"),
        )
    val offenders =
        protectedRoots.flatMap(::kotlinFilesUnder).flatMap { file ->
          file.readLines().withIndex().mapNotNull { (index, line) ->
            if ("me.domino.fa2.ui.pages." in line) {
              "${file.relativeTo(repoRoot)}:${index + 1}: $line"
            } else {
              null
            }
          }
        }

    assertTrue(
        offenders.isEmpty(),
        "Reusable UI/API packages must not import feature implementations:\n" +
            offenders.joinToString("\n"),
    )
  }

  @Test
  fun submissionViewerDoesNotConstructOtherFeatureScreens() {
    val forbidden =
        listOf(
            "me.domino.fa2.ui.pages.browse",
            "me.domino.fa2.ui.pages.search",
            "me.domino.fa2.ui.pages.user",
        )
    val offenders =
        kotlinFilesUnder(commonMainRoot.resolve("ui/pages/submission")).flatMap { file ->
          file.readLines().withIndex().flatMap { (index, line) ->
            forbidden
                .filter { packageName -> packageName in line }
                .map { _ -> "${file.relativeTo(repoRoot)}:${index + 1}: $line" }
          }
        }

    assertTrue(
        offenders.isEmpty(),
        "Submission viewer must navigate through its app-level contract:\n" +
            offenders.joinToString("\n"),
    )
  }

  private fun findCycles(graph: Map<String, Set<String>>): List<String> {
    val cycles = mutableSetOf<String>()
    val visited = mutableSetOf<String>()
    val active = mutableListOf<String>()

    fun visit(node: String) {
      val cycleStart = active.indexOf(node)
      if (cycleStart >= 0) {
        cycles += (active.drop(cycleStart) + node).joinToString(" -> ")
        return
      }
      if (!visited.add(node)) return
      active += node
      graph[node].orEmpty().filter { dependency -> dependency in graph }.sorted().forEach(::visit)
      active.removeAt(active.lastIndex)
    }

    graph.keys.sorted().forEach(::visit)
    return cycles.sorted()
  }

  private fun kotlinFilesUnder(root: Path): List<Path> {
    if (!root.exists()) return emptyList()
    return Files.walk(root).use { stream ->
      stream.filter { path -> path.isRegularFile() && path.extension == "kt" }.sorted().toList()
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
