import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.testing.Test

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.nucleus)
}

val appVersionName = providers.gradleProperty("APP_VERSION_NAME").get()

val desktopPackageVersion =
    Regex("""^(\d+\.\d+\.\d+)(?:[-+].*)?$""").matchEntire(appVersionName)?.groupValues?.get(1)
        ?: error("APP_VERSION_NAME must be SemVer (e.g. 1.2.3 or 1.2.3-alpha1)")

kotlin {
  jvm("desktop")

  sourceSets {
    val desktopMain by getting {
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation(libs.compose.material3)
        implementation(libs.coil.compose)
        implementation(libs.datastore.preferences.core)
        implementation(libs.ksafe)
        implementation(project(":shared"))
        implementation(libs.koin.core)
        implementation(libs.ktor.client.core)
        implementation(libs.kermit)
        implementation(libs.rapidocr4j)
        implementation(libs.room.runtime)
        implementation(libs.sqlite.bundled)
        implementation(libs.slf4j.simple)
        implementation(libs.nucleus.application)
        implementation(libs.nucleus.core.runtime)
        implementation(libs.nucleus.decorated.window.material3)
        implementation(libs.nucleus.decorated.window.tao)
      }
    }

    val desktopTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.compose.ui.test.junit4)
        implementation(compose.desktop.currentOs)
      }
    }
  }
}

nucleus.application {
  mainClass = "me.domino.fa2.desktop.MainKt"

  nativeDistributions {
    targetFormats(TargetFormat.Dmg, TargetFormat.Nsis, TargetFormat.Deb)
    appName = "fa2"
    packageName = "fa2"
    packageVersion = desktopPackageVersion

    linux { iconFile.set(project.file("src/desktopMain/resources/icon/icon.png")) }
    windows { iconFile.set(project.file("src/desktopMain/resources/icon/icon.ico")) }
    macOS { iconFile.set(project.file("src/desktopMain/resources/icon/icon.icns")) }
  }
}

val shouldGenerateAppIcons =
    providers
        .gradleProperty("skipGenerateAppIcons")
        .map { value -> !value.toBoolean() }
        .orElse(true)

val enableDesktopE2e =
    providers.gradleProperty("enableDesktopE2e").map(String::toBoolean).orElse(false)

tasks
    .matching { it.name in setOf("desktopProcessResources", "processResources") }
    .configureEach {
      if (shouldGenerateAppIcons.get()) {
        dependsOn(rootProject.tasks.named("generateAppIcons"))
      }
    }

tasks.named<Test>("desktopTest").configure {
  if (!enableDesktopE2e.get()) {
    exclude("me/domino/fa2/desktop/e2e/**")
    failOnNoDiscoveredTests = false
  }
}
