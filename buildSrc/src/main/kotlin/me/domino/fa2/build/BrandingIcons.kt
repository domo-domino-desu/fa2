package me.domino.fa2.build

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

private const val logoBackground = "#12284f"

fun Project.registerGenerateAppIconsTask(): TaskProvider<Task> {
  val logoSvg = layout.projectDirectory.file("branding/logo.svg").asFile
  val androidRes = layout.projectDirectory.dir("androidApp/src/main/res").asFile
  val desktopIconDir =
      layout.projectDirectory.dir("desktopApp/src/desktopMain/resources/icon").asFile
  val generatedOutputs =
      listOf(
          file("androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher.png"),
          file("androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png"),
          file("desktopApp/src/desktopMain/resources/icon/icon.png"),
          file("desktopApp/src/desktopMain/resources/icon/icon.ico"),
          file("desktopApp/src/desktopMain/resources/icon/icon.icns"),
      )

  return tasks.register("generateAppIcons") {
    group = "branding"
    description = "Generate Android/Desktop app icons from branding/logo.svg"

    inputs.file(logoSvg)
    outputs.files(generatedOutputs)

    doLast {
      check(logoSvg.exists()) { "Missing logo source: ${logoSvg.absolutePath}" }
      val toolchain = detectToolchain()
      if (toolchain.missingCommands.isNotEmpty()) {
        check(generatedOutputs.all(File::exists)) {
          "Missing required command(s): ${toolchain.missingCommands.joinToString()}"
        }
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
      listOf(16, 32, 64, 128, 256, 512, 1024).forEach { size ->
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
                desktopIconDir.resolve("icon-$size.png").absolutePath,
                logoSvg.absolutePath,
            )
        )
      }

      Files.copy(
          desktopIconDir.resolve("icon-512.png").toPath(),
          desktopIconDir.resolve("icon.png").toPath(),
          StandardCopyOption.REPLACE_EXISTING,
      )

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
    }
  }
}

private data class BrandingToolchain(
    val imageMagickCommand: String?,
    val png2icnsCommand: String?,
    val missingCommands: List<String>,
)

private fun detectToolchain(): BrandingToolchain {
  val imageMagickCommand = resolveCommandOrNull("magick", "convert")
  val png2icnsCommand = resolveCommandOrNull("png2icns")
  val missingCommands = buildList {
    if (resolveCommandOrNull("rsvg-convert") == null) add("rsvg-convert")
    if (imageMagickCommand == null) add("magick/convert")
    if (png2icnsCommand == null) add("png2icns")
  }
  return BrandingToolchain(
      imageMagickCommand = imageMagickCommand,
      png2icnsCommand = png2icnsCommand,
      missingCommands = missingCommands,
  )
}

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
