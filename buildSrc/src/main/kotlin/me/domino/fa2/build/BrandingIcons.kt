package me.domino.fa2.build

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

private const val logoBackground = "#12284f"

fun Project.registerGenerateAppIconsTask(): TaskProvider<Task> {
  val projectDir = layout.projectDirectory.asFile
  val logoSvg = layout.projectDirectory.file("branding/logo.svg").asFile
  val androidRes = layout.projectDirectory.dir("androidApp/src/main/res").asFile
  val desktopIconDir =
      layout.projectDirectory.dir("desktopApp/src/desktopMain/resources/icon").asFile
  val optimizationReport =
      layout.buildDirectory.file("reports/branding-icons/png-optimization.md").get().asFile
  val iconOutputs =
      listOf(
          file("androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher.png"),
          file("androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png"),
          file("desktopApp/src/desktopMain/resources/icon/icon.png"),
          file("desktopApp/src/desktopMain/resources/icon/icon.ico"),
          file("desktopApp/src/desktopMain/resources/icon/icon.icns"),
      )
  val generatedOutputs = iconOutputs + optimizationReport

  return tasks.register("generateAppIcons") {
    group = "branding"
    description = "Generate Android/Desktop app icons from branding/logo.svg"

    inputs.file(logoSvg)
    outputs.files(generatedOutputs)

    doLast {
      check(logoSvg.exists()) { "Missing logo source: ${logoSvg.absolutePath}" }
      val toolchain = detectToolchain()
      if (toolchain.missingCommands.isNotEmpty()) {
        check(iconOutputs.all(File::exists)) {
          "Missing required command(s): ${toolchain.missingCommands.joinToString()}"
        }
        writeSkippedOptimizationReport(optimizationReport, toolchain.missingCommands)
        logger.lifecycle(
            "generateAppIcons -> skip, using checked-in icons because tools are unavailable: ${toolchain.missingCommands.joinToString()}"
        )
        return@doLast
      }

      val densities =
          listOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)

      densities.forEach { (density, size) ->
        val dir = androidRes.resolve("mipmap-$density")
        dir.mkdirs()

        runCommand(
            listOf(
                "rsvg-convert",
                "-f",
                "png",
                "-w",
                size.toString(),
                "-h",
                size.toString(),
                "-b",
                logoBackground,
                "-o",
                dir.resolve("ic_launcher.png").absolutePath,
                logoSvg.absolutePath,
            )
        )

        Files.copy(
            dir.resolve("ic_launcher.png").toPath(),
            dir.resolve("ic_launcher_round.png").toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )

        val fgSize = size * 9 / 4
        runCommand(
            listOf(
                "rsvg-convert",
                "-f",
                "png",
                "-w",
                fgSize.toString(),
                "-h",
                fgSize.toString(),
                "-o",
                dir.resolve("ic_launcher_foreground.png").absolutePath,
                logoSvg.absolutePath,
            )
        )
      }

      desktopIconDir.mkdirs()
      val generatedPngs = mutableListOf<File>()
      listOf(16, 32, 64, 128, 256, 512, 1024).forEach { size ->
        val outputFile = desktopIconDir.resolve("icon-$size.png")
        runCommand(
            listOf(
                "rsvg-convert",
                "-f",
                "png",
                "-w",
                size.toString(),
                "-h",
                size.toString(),
                "-b",
                logoBackground,
                "-o",
                outputFile.absolutePath,
                logoSvg.absolutePath,
            )
        )
        generatedPngs += outputFile
      }

      Files.copy(
          desktopIconDir.resolve("icon-512.png").toPath(),
          desktopIconDir.resolve("icon.png").toPath(),
          StandardCopyOption.REPLACE_EXISTING,
      )
      generatedPngs += desktopIconDir.resolve("icon.png")

      densities.forEach { (density, _) ->
        val dir = androidRes.resolve("mipmap-$density")
        generatedPngs += dir.resolve("ic_launcher.png")
        generatedPngs += dir.resolve("ic_launcher_round.png")
        generatedPngs += dir.resolve("ic_launcher_foreground.png")
      }

      val imageMagickCmd = checkNotNull(toolchain.imageMagickCommand)
      runCommand(
          listOf(
              imageMagickCmd,
              desktopIconDir.resolve("icon-1024.png").absolutePath,
              "-define",
              "icon:auto-resize=16,24,32,48,64,128,256",
              desktopIconDir.resolve("icon.ico").absolutePath,
          )
      )

      runCommand(
          listOf(
              checkNotNull(toolchain.png2icnsCommand),
              desktopIconDir.resolve("icon.icns").absolutePath,
              desktopIconDir.resolve("icon-16.png").absolutePath,
              desktopIconDir.resolve("icon-32.png").absolutePath,
              desktopIconDir.resolve("icon-64.png").absolutePath,
              desktopIconDir.resolve("icon-128.png").absolutePath,
              desktopIconDir.resolve("icon-256.png").absolutePath,
              desktopIconDir.resolve("icon-512.png").absolutePath,
              desktopIconDir.resolve("icon-1024.png").absolutePath,
          )
      )

      val optimizationResults =
          generatedPngs.distinctBy(File::getAbsolutePath).sortedBy(File::getAbsolutePath).map {
              pngFile ->
            optimizePng(pngFile, toolchain)
          }

      writeOptimizationReport(projectDir, optimizationReport, optimizationResults)
      logger.lifecycle(
          buildString {
            val originalBytes = optimizationResults.sumOf(PngOptimizationResult::originalBytes)
            val optimizedBytes = optimizationResults.sumOf(PngOptimizationResult::optimizedBytes)
            val savedBytes = originalBytes - optimizedBytes
            append("generateAppIcons -> optimized ${optimizationResults.size} PNGs")
            append(", saved ${formatBytes(savedBytes)}")
            append(" (${formatPercent(savedBytes, originalBytes)})")
            append(", report: ${optimizationReport.absolutePath}")
          }
      )
    }
  }
}

private data class BrandingToolchain(
    val imageMagickCommand: String?,
    val oxipngCommand: String?,
    val pngquantCommand: String?,
    val png2icnsCommand: String?,
    val missingCommands: List<String>,
)

private fun detectToolchain(): BrandingToolchain {
  val imageMagickCommand = resolveCommandOrNull("magick", "convert")
  val oxipngCommand = resolveCommandOrNull("oxipng")
  val pngquantCommand = resolveCommandOrNull("pngquant")
  val png2icnsCommand = resolveCommandOrNull("png2icns")
  val missingCommands = buildList {
    if (resolveCommandOrNull("rsvg-convert") == null) add("rsvg-convert")
    if (imageMagickCommand == null) add("magick/convert")
    if (oxipngCommand == null) add("oxipng")
    if (pngquantCommand == null) add("pngquant")
    if (png2icnsCommand == null) add("png2icns")
  }
  return BrandingToolchain(
      imageMagickCommand = imageMagickCommand,
      oxipngCommand = oxipngCommand,
      pngquantCommand = pngquantCommand,
      png2icnsCommand = png2icnsCommand,
      missingCommands = missingCommands,
  )
}

private data class PngOptimizationResult(
    val file: File,
    val originalBytes: Long,
    val afterPngquantBytes: Long,
    val optimizedBytes: Long,
) {
  val pngquantSavedBytes: Long
    get() = originalBytes - afterPngquantBytes

  val oxipngSavedBytes: Long
    get() = afterPngquantBytes - optimizedBytes

  val totalSavedBytes: Long
    get() = originalBytes - optimizedBytes
}

private fun optimizePng(file: File, toolchain: BrandingToolchain): PngOptimizationResult {
  val originalBytes = file.length()

  runCommand(
      listOf(
          checkNotNull(toolchain.pngquantCommand),
          "--force",
          "--skip-if-larger",
          "--output",
          file.absolutePath,
          "--quality=65-90",
          "--speed",
          "1",
          "--strip",
          "--",
          file.absolutePath,
      )
  )
  val afterPngquantBytes = file.length()

  runCommand(
      listOf(
          checkNotNull(toolchain.oxipngCommand),
          "--opt",
          "4",
          "--strip",
          "safe",
          file.absolutePath,
      )
  )
  val optimizedBytes = file.length()

  return PngOptimizationResult(
      file = file,
      originalBytes = originalBytes,
      afterPngquantBytes = afterPngquantBytes,
      optimizedBytes = optimizedBytes,
  )
}

private fun writeOptimizationReport(
    projectDir: File,
    reportFile: File,
    results: List<PngOptimizationResult>,
) {
  reportFile.parentFile.mkdirs()
  val originalBytes = results.sumOf(PngOptimizationResult::originalBytes)
  val afterPngquantBytes = results.sumOf(PngOptimizationResult::afterPngquantBytes)
  val optimizedBytes = results.sumOf(PngOptimizationResult::optimizedBytes)
  val savedBytes = originalBytes - optimizedBytes

  reportFile.writeText(
      buildString {
        appendLine("# PNG Optimization Report")
        appendLine()
        appendLine("| Metric | Bytes | Human |")
        appendLine("| --- | ---: | ---: |")
        appendLine("| Original total | $originalBytes | ${formatBytes(originalBytes)} |")
        appendLine("| After pngquant | $afterPngquantBytes | ${formatBytes(afterPngquantBytes)} |")
        appendLine("| After oxipng | $optimizedBytes | ${formatBytes(optimizedBytes)} |")
        appendLine("| Total saved | $savedBytes | ${formatBytes(savedBytes)} |")
        appendLine("| Total reduction | ${formatPercent(savedBytes, originalBytes)} | n/a |")
        appendLine()
        appendLine(
            "| File | Original | After pngquant | Final | pngquant saved | oxipng saved | Total saved | Reduction |"
        )
        appendLine("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
        results.forEach { result ->
          appendLine(
              "| ${reportPath(projectDir, result.file)} | ${formatBytes(result.originalBytes)} | ${formatBytes(result.afterPngquantBytes)} | ${formatBytes(result.optimizedBytes)} | ${formatBytes(result.pngquantSavedBytes)} | ${formatBytes(result.oxipngSavedBytes)} | ${formatBytes(result.totalSavedBytes)} | ${formatPercent(result.totalSavedBytes, result.originalBytes)} |"
          )
        }
      }
  )
}

private fun writeSkippedOptimizationReport(reportFile: File, missingCommands: List<String>) {
  reportFile.parentFile.mkdirs()
  reportFile.writeText(
      buildString {
        appendLine("# PNG Optimization Report")
        appendLine()
        appendLine("PNG optimization was skipped because the required toolchain is unavailable.")
        appendLine()
        appendLine("Missing commands: ${missingCommands.joinToString()}")
      }
  )
}

private fun formatBytes(bytes: Long): String {
  if (bytes < 1024) return "$bytes B"

  val kib = bytes / 1024.0
  if (kib < 1024) return String.format(Locale.US, "%.1f KiB", kib)

  val mib = kib / 1024.0
  return String.format(Locale.US, "%.2f MiB", mib)
}

private fun formatPercent(savedBytes: Long, totalBytes: Long): String {
  if (totalBytes <= 0) return "0.0%"
  val percent = savedBytes * 100.0 / totalBytes
  return String.format(Locale.US, "%.1f%%", percent)
}

private fun reportPath(projectDir: File, file: File): String =
    file
        .toPath()
        .toAbsolutePath()
        .normalize()
        .toFile()
        .relativeTo(projectDir)
        .invariantSeparatorsPath

private fun runCommand(args: List<String>) {
  val process = ProcessBuilder(args).redirectErrorStream(true).inheritIO().start()
  val code = process.waitFor()
  check(code == 0) { "Command failed ($code): ${args.joinToString(" ")}" }
}

private fun resolveCommandOrNull(vararg candidates: String): String? {
  val pathDirs =
      (System.getenv("PATH") ?: "").split(File.pathSeparatorChar).filter { it.isNotBlank() }

  for (candidate in candidates) {
    if (
        pathDirs.any { dir ->
          val executable = File(dir, candidate)
          executable.isFile && executable.canExecute()
        }
    ) {
      return candidate
    }
  }

  return null
}
