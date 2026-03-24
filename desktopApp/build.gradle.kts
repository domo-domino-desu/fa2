import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val appVersionName = providers
    .gradleProperty("APP_VERSION_NAME")
    .get()
val desktopPackageVersion = Regex("""^(\d+\.\d+\.\d+)(?:[-+].*)?$""")
    .matchEntire(appVersionName)
    ?.groupValues
    ?.get(1)
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
                implementation(libs.kermit)
                implementation(libs.room.runtime)
                implementation(libs.sqlite.bundled)
                implementation(libs.slf4j.simple)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "me.domino.fa2.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "fa2"
            packageVersion = desktopPackageVersion

            linux {
                iconFile.set(project.file("src/desktopMain/resources/icon/icon.png"))
            }
            windows {
                iconFile.set(project.file("src/desktopMain/resources/icon/icon.ico"))
            }
            macOS {
                iconFile.set(project.file("src/desktopMain/resources/icon/icon.icns"))
            }
        }
    }
}

val shouldGenerateAppIcons = providers
    .gradleProperty("skipGenerateAppIcons")
    .map { value -> !value.toBoolean() }
    .orElse(true)

tasks.matching { it.name in setOf("desktopProcessResources", "processResources") }.configureEach {
    if (shouldGenerateAppIcons.get()) {
        dependsOn(rootProject.tasks.named("generateAppIcons"))
    }
}
