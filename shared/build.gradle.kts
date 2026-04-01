import io.github.kingsword09.symbolcraft.model.SymbolFill
import io.github.kingsword09.symbolcraft.model.SymbolVariant

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.aboutLibraries)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
  alias(libs.plugins.symbolCraft)
}

val appVersionName = providers.gradleProperty("APP_VERSION_NAME").get()
val generatedAboutMetadataDir =
    layout.buildDirectory.dir("generated/aboutMetadata/commonMain/kotlin")
val generateAboutMetadata =
    tasks.register("generateAboutMetadata") {
      val licenseFile = rootProject.file("LICENSE")
      inputs.property("appVersionName", appVersionName)
      inputs.file(licenseFile)
      outputs.dir(generatedAboutMetadataDir)

      doLast {
        val outputFile =
            generatedAboutMetadataDir.get().file("me/domino/fa2/generated/AboutMetadata.kt").asFile
        val licenseLiteral = buildString {
          append("\"\"\"")
          append(licenseFile.readText().replace("\"\"\"", "\"\"\\\"").replace("$", "\${'$'}"))
          append("\"\"\".trimIndent()")
        }
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            package me.domino.fa2.generated

            internal object AboutMetadata {
              const val versionName: String = "$appVersionName"

              val licenseText: String = $licenseLiteral
            }
            """
                .trimIndent()
        )
      }
    }

kotlin {
  android {
    namespace = "me.domino.fa2.shared"
    compileSdk = 36
    minSdk = 29
    androidResources { enable = true }
  }

  jvm("desktop")

  sourceSets {
    val commonMain by getting {
      kotlin.srcDir(generatedAboutMetadataDir)
      dependencies {
        implementation(libs.aboutlibraries.compose.m3)
        implementation(libs.aboutlibraries.core)
        implementation(libs.coil.compose)
        implementation(libs.coil.network.ktor3)
        implementation(libs.compose.components.resources)
        implementation(libs.compose.foundation)
        implementation(libs.compose.material3)
        implementation(libs.compose.native.webview)
        implementation(libs.compose.runtime)
        implementation(libs.compose.ui)
        implementation(libs.datastore.preferences.core)
        implementation(libs.htmlconverter.compose)
        implementation(libs.kermit)
        implementation(libs.koin.compose)
        implementation(libs.koin.core)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.ksafe)
        implementation(libs.ksoup)
        implementation(libs.ktor.client.core)
        implementation(libs.materialyou)
        implementation(libs.room.runtime)
        implementation(libs.store5)
        implementation(libs.voyager.core)
        implementation(libs.voyager.koin)
        implementation(libs.voyager.navigator)
        implementation(libs.voyager.screenmodel)
        implementation(libs.zoomimage.compose.coil3)
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.koin.core)
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.ktor.client.mock)
        implementation(libs.room.runtime)
        implementation(libs.sqlite.bundled)
      }
    }

    val desktopMain by getting {
      dependencies {
        implementation(libs.ktor.client.okhttp)
        implementation(libs.pdfbox)
        implementation(libs.slf4j.simple)
      }
    }

    val desktopTest by getting {
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation(compose.desktop.uiTestJUnit4)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(libs.androidx.activity.compose)
        implementation(libs.coil.gif)
        implementation(libs.documentfile)
        implementation(libs.ktor.client.okhttp)
        implementation(libs.pdfbox.android)
      }
    }
  }
}

dependencies {
  add("kspCommonMainMetadata", libs.room.compiler)
  add("kspDesktop", libs.room.compiler)
  add("kspAndroid", libs.room.compiler)
}

tasks.register("test") {
  group = "verification"
  dependsOn("desktopTest")
}

aboutLibraries {
  export { outputFile = file("src/commonMain/composeResources/files/aboutlibraries.json") }
}

room { schemaDirectory("$projectDir/schemas") }

symbolCraft {
  packageName.set("me.domino.fa2.generated.symbols")
  outputDirectory.set("src/commonMain/kotlin")
  generatePreview.set(false)
  cacheEnabled.set(true)

  naming { pascalCase() }

  materialSymbols(
      "add",
      "close",
      "done",
      "download",
      "explore",
      "favorite",
      "history",
      "home",
      "info",
      "menu",
      "notifications",
      "restart_alt",
      "save",
      "search",
      "settings",
  ) {
    style(weight = 400, variant = SymbolVariant.OUTLINED, fill = SymbolFill.FILLED)
  }

  materialSymbols(
      "arrow_back",
      "arrow_circle_right",
      "arrow_downward_alt",
      "arrow_upward_alt",
      "category",
      "comment",
      "code",
      "content_copy",
      "date_range",
      "attribution",
      "document_scanner",
      "download",
      "expand_less",
      "expand_more",
      "explore",
      "file_present",
      "filter_alt",
      "home",
      "image",
      "inventory_2",
      "keyboard_arrow_down",
      "keyboard_arrow_right",
      "keyboard_arrow_up",
      "keyboard_double_arrow_down",
      "keyboard_double_arrow_up",
      "language",
      "logout",
      "mail",
      "menu",
      "movie",
      "music_note",
      "notifications",
      "output_circle",
      "refresh",
      "receipt_long",
      "search",
      "share",
      "subject",
      "tag",
      "translate",
      "troubleshoot",
      "vertical_align_bottom",
      "vertical_align_top",
      "visibility",
      "visibility_off",
      "wrap_text",
  ) {
    style(weight = 400, variant = SymbolVariant.OUTLINED, fill = SymbolFill.UNFILLED)
  }

  materialSymbol("favorite") {
    style(weight = 400, variant = SymbolVariant.OUTLINED, fill = SymbolFill.UNFILLED)
  }

  externalIcons(
      *listOf(
              "battle-net",
              "bluesky",
              "deviantart",
              "discord",
              "etsy",
              "facebook",
              "github",
              "instagram",
              "mastodon",
              "patreon",
              "pixiv",
              "playstation",
              "reddit",
              "steam",
              "telegram",
              "tiktok",
              "tumblr",
              "twitch",
              "twitter",
              "xbox",
              "youtube",
          )
          .toTypedArray(),
      libraryName = "fontawesomebrands",
  ) {
    urlTemplate =
        "https://raw.githubusercontent.com/FortAwesome/Font-Awesome/6.x/svgs/brands/{name}.svg"
  }

  externalIcons(
      *listOf(
              "archiveofourown",
              "battledotnet",
              "furrynetwork",
              "kofi",
              "nintendo3ds",
              "nintendoswitch",
              "picartodottv",
              "wattpad",
              "weasyl",
              "wiiu",
          )
          .toTypedArray(),
      libraryName = "simpleicons",
  ) {
    urlTemplate = "https://cdn.jsdelivr.net/npm/simple-icons@latest/icons/{name}.svg"
  }

  localIcons(libraryName = "brand") { directory = "../branding" }
}

val symbolCraftConsumerTasks =
    setOf(
        "compileCommonMainKotlinMetadata",
        "kspCommonMainKotlinMetadata",
        "compileAndroidMain",
        "kspAndroidMain",
        "compileKotlinDesktop",
        "kspKotlinDesktop",
        "compileDevKotlinDesktop",
        "kspDevKotlinDesktop",
    )

tasks.configureEach {
  if (name in symbolCraftConsumerTasks) {
    dependsOn("generateSymbolCraftIcons")
    dependsOn(generateAboutMetadata)
  }
}
