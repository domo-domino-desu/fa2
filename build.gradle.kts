import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
  alias(libs.plugins.composeCompiler) apply false
  alias(libs.plugins.composeMultiplatform) apply false
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.kotlinx.serialization) apply false
}

/** 在本机构建环境执行外部命令，失败即抛错。 */
fun runCommand(args: List<String>) {
  val process = ProcessBuilder(args).redirectErrorStream(true).inheritIO().start()
  val code = process.waitFor()
  check(code == 0) { "Command failed ($code): ${args.joinToString(" ")}" }
}

/**
 * Resolve the first executable command from candidates in PATH. Useful for CI images where
 * ImageMagick may expose `convert` (v6) instead of `magick` (v7).
 */
fun resolveCommand(vararg candidates: String): String {
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

  error("Missing required command. Tried: ${candidates.joinToString(", ")}")
}

val logoSvg = layout.projectDirectory.file("branding/logo.svg").asFile
val logoBackground = "#12284f"

tasks.register("generateAppIcons") {
  group = "branding"
  description = "Generate Android/Desktop app icons from branding/logo.svg"

  val androidRes = layout.projectDirectory.dir("androidApp/src/main/res").asFile
  val desktopIconDir =
      layout.projectDirectory.dir("desktopApp/src/desktopMain/resources/icon").asFile

  inputs.file(logoSvg)
  outputs.files(
      file("androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher.png"),
      file("androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png"),
      file("desktopApp/src/desktopMain/resources/icon/icon.png"),
      file("desktopApp/src/desktopMain/resources/icon/icon.ico"),
      file("desktopApp/src/desktopMain/resources/icon/icon.icns"),
  )

  doLast {
    check(logoSvg.exists()) { "Missing logo source: ${logoSvg.absolutePath}" }

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
    val iconSizes = listOf(16, 32, 64, 128, 256, 512, 1024)
    iconSizes.forEach { size ->
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

    val imageMagickCmd = resolveCommand("magick", "convert")
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
            "png2icns",
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
